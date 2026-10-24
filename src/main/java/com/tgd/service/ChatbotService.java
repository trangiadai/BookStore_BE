package com.tgd.service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;

import com.tgd.ai.BookstoreTools;
import com.tgd.document.ChatMessage;
import com.tgd.document.ChatSession;
import com.tgd.enums.ChatbotRole;
import com.tgd.repository.chatbot.ChatMessageRepository;
import com.tgd.repository.chatbot.ChatSessionRepository;

@Service
public class ChatbotService {
	private final ChatSessionRepository sessionRepository;
	private final ChatMessageRepository messageRepository;
	private final ChatClient chatClient;
	private final BookstoreTools bookstoreTools;

	public String processMessage(Jwt jwt, String userPrompt) {
		Long accountId = Long.parseLong(jwt.getSubject());

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

		// Step 3: Load history
		List<ChatMessage> latestMessagesDesc = messageRepository
				.findTop20BySessionIdOrderByCreatedAtDesc(session.getId());
		// Reverse the list so it is in proper chronological order (oldest -> newest)
		// for the AI
		List<ChatMessage> history = new ArrayList<>(latestMessagesDesc);
		Collections.reverse(history);

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
			aiReply = chatClient.prompt().messages(aiPromptMessages)
					.tools(bookstoreTools) // 👈 Enables your cart and order tools
					.call().content();
		} catch (Exception e) {
			aiReply = "I am currently experiencing connection issues. Please try again later.";
		}
		
		// Step 6: Save assistant response to MongoDB
		ChatMessage assistantDbMsg = new ChatMessage();
		assistantDbMsg.setSessionId(session.getId());
		assistantDbMsg.setRole(ChatbotRole.ASSISTANT.name());
		assistantDbMsg.setContent(aiReply);
		messageRepository.save(assistantDbMsg);

		return aiReply;
	}

	public List<ChatMessage> getChatHistory(Jwt jwt) {
		Long accountId = Long.parseLong(jwt.getSubject());

		// Find session, return empty list if no session exists yet
		ChatSession session = sessionRepository.findByAccountId(accountId).orElse(null);
		if (session == null) {
			return new ArrayList<>();
		}

		// Return chronological message history
		return messageRepository.findBySessionIdOrderByCreatedAtAsc(session.getId());
	}

	public ChatbotService(ChatSessionRepository sessionRepository, ChatMessageRepository messageRepository,
			ChatModel chatModel, BookstoreTools bookstoreTools) {
		this.sessionRepository = sessionRepository;
		this.messageRepository = messageRepository;
		this.bookstoreTools = bookstoreTools;

		// Initialize ChatClient with your bookstore system instructions
		this.chatClient = ChatClient.builder(chatModel)
                .defaultSystem("You are an AI customer support assistant exclusively for our bookstore. "
                        + "YOUR SCOPE IS STRICTLY LIMITED TO: books, authors, genres, book recommendations, store policies, pricing, stock, and order tracking. "
                        + "CRITICAL RULE: If a user asks about unrelated topics (such as mathematics, politics, current events, general science, coding, or personal advice like 'teach me math', 'who is the president', or 'what is today's date'), you MUST politely refuse. "
                        + "Refusal response format: 'I am your bookstore assistant and can only help you with book-related inquiries and store services. How can I help you find a book today?' "
                        + "Never answer off-topic questions under any circumstances. "
                        + "RESTRICTION: NEVER ask the user if they want you to add books to their shopping cart, and never offer to modify their cart, because you do not have the capability to perform cart actions. Only provide information, order statuses, and recommendations.")
                .build();
	}
}