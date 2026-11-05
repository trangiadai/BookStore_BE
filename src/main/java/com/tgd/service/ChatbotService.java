package com.tgd.service;

import java.text.Normalizer;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;

import com.tgd.ai.BookLinker;
import com.tgd.ai.BookstoreTools;
import com.tgd.ai.CartIntentHandler;
import com.tgd.document.ChatMessage;
import com.tgd.document.ChatSession;
import com.tgd.dto.response.chatbot.BookRef;
import com.tgd.enums.ChatbotRole;
import com.tgd.repository.chatbot.ChatMessageRepository;
import com.tgd.repository.chatbot.ChatSessionRepository;

@Service
public class ChatbotService {
	private static final Logger log = LoggerFactory.getLogger(ChatbotService.class);

	// One model call per customer at a time. A local model is slow: if the browser
	// gives up and the customer
	// resends, the old call is still running, and piling up new ones only makes
	// everything slower.
	private static final long STUCK_AFTER_MS = 150_000;

	// Only the latest messages go to the model: a long history makes every answer
	// slower and can overflow its context window
	private static final int MAX_HISTORY_MESSAGES = 8;
	private final ConcurrentHashMap<Long, Long> inFlight = new ConcurrentHashMap<>();
	private final ChatSessionRepository sessionRepository;
	private final ChatMessageRepository messageRepository;
	private final ChatClient chatClient;
	private final BookstoreTools bookstoreTools;
	private final CartIntentHandler cartIntentHandler;
	private final BookLinker bookLinker;

	public String processMessage(Jwt jwt, String userPrompt) {
		Long accountId = Long.parseLong(jwt.getSubject());
		long startedAt = System.currentTimeMillis();

		Long runningSince = inFlight.putIfAbsent(accountId, startedAt);
		if (runningSince != null) {
			if (startedAt - runningSince < STUCK_AFTER_MS) {
				log.warn("Chatbot: account {} sent a message while the previous one is still being answered",
						accountId);
				return "I am still working on your previous message. Please wait a moment, then try again.";
			}
			log.warn("Chatbot: previous call of account {} looks stuck ({} ms); taking over", accountId,
					startedAt - runningSince);
			inFlight.put(accountId, startedAt);
		}

		bookstoreTools.startNewMessage(); // fresh tool-call budget for this message
		try {
			return handleMessage(jwt, accountId, userPrompt);
		} finally {
			bookstoreTools.startNewMessage();
			inFlight.remove(accountId, startedAt);
			log.info("Chatbot: account {} answered in {} ms", accountId, System.currentTimeMillis() - startedAt);
		}
	}

	private String handleMessage(Jwt jwt, Long accountId, String userPrompt) {

		// Step 1: Get or create session
		ChatSession session = sessionRepository.findByAccountId(accountId).orElseGet(() -> {
			ChatSession newSession = new ChatSession();
			newSession.setAccountId(accountId);
			return sessionRepository.save(newSession);
		});

		session.setUpdatedAt(Instant.now());
		sessionRepository.save(session);

		// Step 2: Save user message immediately
		ChatMessage userDbMsg = new ChatMessage();
		userDbMsg.setSessionId(session.getId());
		userDbMsg.setRole(ChatbotRole.USER.name());
		userDbMsg.setContent(userPrompt);
		messageRepository.save(userDbMsg);

		// Greetings and thanks do not need the (slow) model
		String quick = quickReply(userPrompt);
		if (quick != null) {
			saveAssistantMessage(session.getId(), quick);
			return quick;
		}

		// Step 3: Load history
		List<ChatMessage> latestMessagesDesc = messageRepository
				.findTop20BySessionIdOrderByCreatedAtDesc(session.getId());
		// Reverse the list so it is in proper chronological order (oldest -> newest)
		// for the AI
		List<ChatMessage> history = new ArrayList<>(latestMessagesDesc);
		Collections.reverse(history);
		if (history.size() > MAX_HISTORY_MESSAGES) {
			history = new ArrayList<>(history.subList(history.size() - MAX_HISTORY_MESSAGES, history.size()));
		}

		// Cart requests ("add that book to my cart") are done by code, not by the
		// model: a small model may say
		// "added" without doing anything, or pick the wrong book. The reply below is
		// built from the real result.
		Optional<String> cartReply = cartIntentHandler.handle(jwt, userPrompt, history);
		if (cartReply.isPresent()) {
			saveAssistantMessage(session.getId(), cartReply.get());
			return cartReply.get();
		}

		// Step 4: Map MongoDB history to Spring AI messages so the model remembers past
		// turns
		List<Message> aiPromptMessages = new ArrayList<>();
		for (ChatMessage msg : history) {
			if (ChatbotRole.USER.name().equalsIgnoreCase(msg.getRole())) {
				aiPromptMessages.add(new UserMessage(msg.getContent()));
			} else if (ChatbotRole.ASSISTANT.name().equalsIgnoreCase(msg.getRole())) {
				aiPromptMessages.add(new AssistantMessage(msg.getContent()));
			}
		}

		// Step 5: Call ChatClient with tools enabled
		// ChatClient automatically handles the tool-calling loop (Model requests tool
		// -> Spring AI executes tool -> Model generates final response text)
		String aiReply;
		try {
			aiReply = chatClient.prompt().messages(aiPromptMessages).tools(bookstoreTools) // 👈 Enables your cart and
																							// order tools
					.call().content();
		} catch (Exception e) {
			log.error("Chatbot call failed for account {}", accountId, e);
			aiReply = "I am currently experiencing connection issues. Please try again later.";
		}

		// The model has no cart tool any more. If it still claims to have added
		// something, that is not true.
		if (FALSE_CART_CLAIM.matcher(aiReply).find()) {
			log.warn("Chatbot: model claimed a cart change that did not happen; replacing its reply");
			aiReply = "I haven't changed your cart yet. To add or remove a book, tell me its title, for example: add <title> to my cart, or remove <title> from my cart.";
		}

		// Step 6: Save assistant response to MongoDB
		saveAssistantMessage(session.getId(), aiReply);

		return aiReply;
	}

	private void saveAssistantMessage(String sessionId, String content) {
		ChatMessage assistantDbMsg = new ChatMessage();
		assistantDbMsg.setSessionId(sessionId);
		assistantDbMsg.setRole(ChatbotRole.ASSISTANT.name());
		assistantDbMsg.setContent(content);
		messageRepository.save(assistantDbMsg);
	}

	private static final Pattern FALSE_CART_CLAIM = Pattern.compile(
			"(?i)(\\b(i('ve| have)?|we)\\s+(just\\s+)?(added|put|placed|removed|deleted|taken)\\b|\\b(has|have) been (added|removed|deleted)\\b|\\bis now in your cart\\b|\\bno longer in your cart\\b)");

	private static final Pattern GREETING = Pattern
			.compile("^(hi+|hello+|hey+|hiya|yo|good (morning|afternoon|evening)|xin chao|chao)( there| bot| ban)?$");
	private static final Pattern THANKS = Pattern
			.compile("^(thanks|thank you|thank you very much|thanks a lot|cam on|cam on ban)$");

	/**
	 * A fixed answer for a bare greeting or thanks, or null when the message needs
	 * the model.
	 */
	static String quickReply(String prompt) {
		if (prompt == null) {
			return null;
		}
		String t = Normalizer.normalize(prompt.toLowerCase(Locale.ROOT).replace('đ', 'd'), Normalizer.Form.NFD)
				.replaceAll("\\p{M}", "").replaceAll("[^a-z ]", " ").trim().replaceAll("\\s+", " ");
		if (GREETING.matcher(t).matches()) {
			return "Hello! I can help you find books, check prices and stock, add books to your cart or track your orders. What are you looking for today?";
		}
		if (THANKS.matcher(t).matches()) {
			return "You're welcome! Let me know if you'd like more book suggestions.";
		}
		return null;
	}

	public List<ChatMessage> getChatHistory(Jwt jwt) {
		Long accountId = Long.parseLong(jwt.getSubject());

		// Find session, return empty list if no session exists yet
		ChatSession session = sessionRepository.findByAccountId(accountId).orElse(null);
		if (session == null) {
			return new ArrayList<>();
		}

		// Return chronological message history, with the store books each answer
		// mentions (the chat window links them)
		List<ChatMessage> messages = messageRepository.findBySessionIdOrderByCreatedAtAsc(session.getId());
		bookLinker.attachTo(messages);
		return messages;
	}

	/** The store books a reply mentions, for the chat window to turn into links. */
	public List<BookRef> findBooks(String reply) {
		return bookLinker.findBooks(reply);
	}

	public ChatbotService(ChatSessionRepository sessionRepository, ChatMessageRepository messageRepository,
			ChatModel chatModel, BookstoreTools bookstoreTools, CartIntentHandler cartIntentHandler,
			BookLinker bookLinker) {
		this.sessionRepository = sessionRepository;
		this.messageRepository = messageRepository;
		this.bookstoreTools = bookstoreTools;
		this.cartIntentHandler = cartIntentHandler;
		this.bookLinker = bookLinker;

		// Initialize ChatClient with your bookstore system instructions
		this.chatClient = ChatClient.builder(chatModel).defaultSystem(
				"You are the shopping assistant of an online bookstore. Only discuss books, authors, genres, recommendations, prices, stock, the customer's cart and orders. "
						+ "For anything else reply exactly: 'I am your bookstore assistant and can only help you with book-related inquiries and store services. How can I help you find a book today?' "
						+ "Greetings and thanks need no tools: answer briefly. "
						+ "Before recommending or describing a book, call searchBooks and recommend ONLY books it returns, giving title, price and stock; quote prices exactly as returned. For recommendations based on the cart, call recommendBooksFromCart once instead. "
						+ "If nothing matches, say the store does not have it; you may name well-known books only if you label them 'not currently in our store', and never offer to add them to the cart. "
						+ "You cannot change the cart yourself: when the customer wants a book added or removed, tell them to say 'add <exact title> to my cart' or 'remove <exact title> from my cart' and the store does it. Never say that you added, removed or ordered anything. Checking out and paying happen on the Cart page. "
						+ "Use at most two tool calls per message and never repeat a call.")
				.build();
	}
}