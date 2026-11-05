package com.tgd.ai;

import java.math.BigDecimal;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

import com.tgd.document.ChatMessage;
import com.tgd.dto.request.payment.AddToCartRequest;
import com.tgd.dto.response.ProductResponse;
import com.tgd.dto.response.payment.CartItemResponse;
import com.tgd.dto.response.payment.CartResponse;
import com.tgd.enums.ChatbotRole;
import com.tgd.service.ProductService;
import com.tgd.service.payment.CartService;

/**
 * Handles "add this book to my cart" and "remove this book from my cart"
 * requests in plain code instead of asking the language model to do it. A small
 * local model tends to say "I did it" without calling the tool, or to pass the
 * wrong product id, because it cannot see ids from earlier turns. Here the book
 * is resolved from the customer's words and the recent conversation, the real
 * CartService is called, and the reply is built from the real result.
 */
@Component
public class CartIntentHandler {
	private static final Logger log = LoggerFactory.getLogger(CartIntentHandler.class);

	private static final int CATALOG_SCAN_LIMIT = 1000;
	private static final int MAX_QUANTITY = 99; // same limit as AddToCartRequest
	private static final int MAX_BOOKS_PER_REQUEST = 5;

	/**
	 * Every question we ask to find out which book is meant starts with one of
	 * these, so the next reply can be recognised.
	 */
	public static final String CLARIFY_PREFIX = "Which book would you like me to add?";
	public static final String CLARIFY_REMOVE_PREFIX = "Which book would you like me to remove?";
	static final String REMOVE_CONFIRM_TAIL = "Reply yes and I'll remove it from your cart.";
	static final String CLEAR_CONFIRM = "Do you want me to remove all items from your cart? Reply yes to confirm.";
	private static final String CART_ERROR = "I couldn't reach your cart just now. Please use the Cart page instead.";

	private static final Pattern ADD_TO_CART = Pattern.compile("\\b(add|put|them)\\b.*\\b(cart|basket|gio hang)\\b");
	// "add it", "add the second one", "add them all": a reference to books already
	// mentioned, no cart word needed
	private static final Pattern ADD_IT = Pattern.compile(
			"^(please |can you |could you )?(add|put) (the |that |this )?(first|second|third|fourth|fifth|last|1st|2nd|3rd|4th|it|them|that|this|those|these|all)( one| book| ones| all| of them)?( please)?$");
	private static final Pattern BUY = Pattern.compile("\\b(buy|purchase|mua|i ll take|i will take|ill take)\\b");
	// "I want 4 Sapiens in my cart, add them": the cart is mentioned before the
	// verb (a weaker signal, like "buy")
	private static final Pattern CART_THEN_ADD = Pattern.compile("\\b(cart|basket|gio hang)\\b.*\\b(add|put)\\b");
	private static final Pattern REMOVE_VERB = Pattern
			.compile("\\b(remove|delete|take out|take\\b.*\\bout of|get rid of|drop|xoa|bo khoi)\\b");
	private static final Pattern CART_WORD = Pattern.compile("\\b(cart|basket|gio hang)\\b");
	private static final Pattern CLEAR_CART = Pattern.compile("\\b(clear|empty)\\b.*\\b(cart|basket|gio hang)\\b");
	private static final Pattern EVERYTHING = Pattern
			.compile("\\b(everything|all (the |my )?(items|books|products))\\b");
	// "how do I add a book to my cart?" is a question about the store, not a
	// request: the model answers it
	private static final Pattern QUESTION_START = Pattern
			.compile("^(how|what|whats|where|why|when|who|which|is|are|does|do)\\b");
	private static final Pattern CONFIRM = Pattern.compile(
			"^(yes|yeah|yep|yup|sure|ok|okay|please|please do|yes please|yes do it|go ahead|do it|add it|remove it|sounds good|y)$");
	private static final Pattern REFERENCE = Pattern.compile("\\b(that|this|it|them|these|those|the book|the one)\\b");
	private static final Pattern ALL = Pattern.compile("\\b(all|both|them|these|those|every one)\\b");

	private static final Map<String, Integer> ORDINALS = Map.of("first", 0, "1st", 0, "second", 1, "2nd", 1, "third", 2,
			"3rd", 2, "fourth", 3, "4th", 3, "fifth", 4);
	private static final Map<String, Integer> NUMBER_WORDS = Map.of("two", 2, "three", 3, "four", 4, "five", 5, "six",
			6, "seven", 7, "eight", 8, "nine", 9, "ten", 10);

	// Words that are part of the request itself, not part of a book title
	private static final Set<String> IGNORED = new HashSet<>(Arrays.asList("add", "put", "them", "buy", "purchase",
			"mua", "remove", "delete", "drop", "out", "get", "rid", "xoa", "khoi", "cart", "basket", "gio", "hang",
			"the", "want", "would", "like", "please", "can", "could", "you", "that", "this", "book", "books", "copy",
			"copies", "one", "two", "three", "four", "five", "six", "seven", "eight", "nine", "ten", "first", "second",
			"third", "fourth", "fifth", "last", "yes", "okay", "also", "more", "another", "take", "will", "just", "now",
			"then", "here", "there", "our", "your", "store", "bookstore", "number", "recommended", "suggested",
			"recommendation", "above", "previous", "same", "mentioned", "for", "with", "from", "and", "but", "not",
			"these", "those", "all", "both", "every", "ill", "everything", "items", "item", "products", "product",
			"clear", "empty", "actually", "really", "maybe", "kindly", "anyway", "instead", "already", "still", "sorry",
			"well", "sure", "thanks"));

	private final ProductService productService;
	private final CartService cartService;

	/**
	 * @param history the saved conversation in chronological order; the newest
	 *                entry is the customer's current message
	 * @return the reply to send, or empty when this is not a cart request (the
	 *         language model answers instead)
	 */
	public Optional<String> handle(Jwt jwt, String prompt, List<ChatMessage> history) {
		String text = fold(prompt);
		if (text.isEmpty()) {
			return Optional.empty();
		}

		List<ChatMessage> prior = history == null ? new ArrayList<>() : new ArrayList<>(history);
		if (!prior.isEmpty() && ChatbotRole.USER.name().equalsIgnoreCase(prior.get(prior.size() - 1).getRole())) {
			prior.remove(prior.size() - 1); // the current message
		}
		String previous = "";
		if (!prior.isEmpty() && ChatbotRole.ASSISTANT.name().equalsIgnoreCase(prior.get(prior.size() - 1).getRole())) {
			previous = prior.get(prior.size() - 1).getContent() == null ? "" : prior.get(prior.size() - 1).getContent();
		}

		boolean awaitingAddChoice = previous.startsWith(CLARIFY_PREFIX);
		boolean awaitingRemoveChoice = previous.startsWith(CLARIFY_REMOVE_PREFIX);
		if (QUESTION_START.matcher(text).find() && !awaitingAddChoice && !awaitingRemoveChoice) {
			return Optional.empty();
		}
		boolean yes = CONFIRM.matcher(text).matches();

		// ---- removing ----
		if (yes && previous.equals(CLEAR_CONFIRM)) {
			return Optional.of(clearCart(jwt));
		}
		boolean removeConfirm = yes && previous.contains(REMOVE_CONFIRM_TAIL);
		if (REMOVE_VERB.matcher(text).find() || removeConfirm || awaitingRemoveChoice) {
			return handleRemove(jwt, prompt, text, prior, previous, removeConfirm, awaitingRemoveChoice);
		}

		// ---- adding ----
		boolean explicitAdd = ADD_TO_CART.matcher(text).find() || ADD_IT.matcher(text).matches();
		boolean buy = !explicitAdd && (BUY.matcher(text).find() || CART_THEN_ADD.matcher(text).find())
				&& !prompt.trim().endsWith("?");
		boolean offerPending = previous.toLowerCase(Locale.ROOT).contains("cart") && previous.contains("?");
		boolean confirm = offerPending && yes;
		if (!(explicitAdd || buy || confirm || awaitingAddChoice)) {
			return Optional.empty(); // an ordinary message: no database work at all
		}

		List<ProductResponse> catalogue = productService.getAllProducts(0, CATALOG_SCAN_LIMIT).getContent();
		List<ProductResponse> named = booksMentioned(text, catalogue);
		boolean choice = awaitingAddChoice && text.split(" ").length <= 8
				&& (!named.isEmpty() || ordinalIndex(text, 99, true) != null);

		if (!(explicitAdd || buy || confirm || choice)) {
			return Optional.empty();
		}

		// ---- which book(s)? ----
		List<ProductResponse> targets = new ArrayList<>();
		if (!named.isEmpty()) {
			targets.addAll(named); // the customer named the book(s)
		} else {
			Partial partial = partialMatch(text, catalogue);
			if (partial.tokenCount > 0 && partial.candidates.isEmpty() && !REFERENCE.matcher(text).find()
					&& !awaitingAddChoice) {
				return explicitAdd ? Optional.of(
						"I couldn't find that book in our store. Please tell me its exact title, for example: add <title> to my cart.")
						: Optional.empty(); // vague talk about buying: the model answers
			}
			if (partial.candidates.size() == 1) {
				ProductResponse only = partial.candidates.get(0);
				if (partial.tokenCount >= 2) {
					targets.add(only); // several words of the title all match one book only
				} else {
					return Optional
							.of("Did you mean \"" + only.getName() + "\"? Reply yes and I'll add it to your cart.");
				}
			} else if (partial.candidates.size() > 1) {
				return Optional.of(clarification(CLARIFY_PREFIX, partial.candidates));
			} else {
				if (buy && !REFERENCE.matcher(text).find()) {
					return Optional.empty(); // general talk about buying, not about a particular book
				}
				List<ProductResponse> options = awaitingAddChoice ? booksMentioned(fold(previous), catalogue)
						: recentBooks(prior, catalogue, true);
				if (options.isEmpty()) {
					return Optional
							.of(CLARIFY_PREFIX + " Please tell me its title, for example: add <title> to my cart.");
				}
				Integer idx = ordinalIndex(text, options.size(), awaitingAddChoice);
				if (idx != null) {
					targets.add(options.get(idx));
				} else if (options.size() == 1) {
					targets.add(options.get(0));
				} else if (ALL.matcher(text).find()) {
					targets.addAll(options.subList(0, Math.min(MAX_BOOKS_PER_REQUEST, options.size())));
				} else {
					return Optional.of(clarification(CLARIFY_PREFIX, options));
				}
			}
		}

		// ---- how many? ----
		Integer asked = awaitingAddChoice && text.matches("\\d{1,2}") ? null
				: explicitQuantity(withoutTitles(text, targets));
		int qty = asked == null ? 1 : asked;
		if (qty < 1) {
			return Optional.of("The quantity must be at least 1.");
		}
		if (qty > MAX_QUANTITY) {
			return Optional.of("You can add at most " + MAX_QUANTITY + " copies of a book at a time.");
		}

		return Optional.of(addToCart(jwt, targets.subList(0, Math.min(MAX_BOOKS_PER_REQUEST, targets.size())), qty));
	}

	// ---------------------------------------------------------------- adding

	private String addToCart(Jwt jwt, List<ProductResponse> books, int qty) {
		List<String> added = new ArrayList<>();
		List<String> problems = new ArrayList<>();
		CartResponse lastCart = null;
		for (ProductResponse p : books) {
			if (p.getQuantity() == null || p.getQuantity() <= 0) {
				problems.add("\"" + p.getName() + "\" is out of stock.");
				continue;
			}
			try {
				lastCart = cartService.addToCart(jwt, new AddToCartRequest(p.getId(), qty)); // checks stock incl. what
																								// is already in the
																								// cart
				added.add(copies(qty) + " of \"" + p.getName() + "\"");
				log.info("Chatbot cart: added productId={} quantity={}", p.getId(), qty);
			} catch (IllegalArgumentException e) {
				log.info("Chatbot cart: productId={} quantity={} refused: {}", p.getId(), qty, e.getMessage());
				problems.add(e.getMessage());
			}
		}

		StringBuilder reply = new StringBuilder();
		if (!added.isEmpty()) {
			reply.append("Added ").append(String.join(" and ", added)).append(" to your cart.")
					.append(cartSummary(lastCart)).append(" You can review it on the Cart page.");
		}
		if (!problems.isEmpty()) {
			reply.append(added.isEmpty() ? "" : " However: ").append(String.join(" ", problems));
		}
		return reply.toString().trim();
	}

	// ---------------------------------------------------------------- removing

	private Optional<String> handleRemove(Jwt jwt, String prompt, String text, List<ChatMessage> prior, String previous,
			boolean removeConfirm, boolean awaitingRemoveChoice) {
		CartResponse cart;
		try {
			cart = cartService.getMyCart(jwt);
		} catch (RuntimeException e) {
			log.warn("Chatbot cart: cannot read the cart: {}", e.getMessage());
			return Optional.of(CART_ERROR);
		}
		List<CartItemResponse> items = cart.getItems() == null ? new ArrayList<>() : cart.getItems();
		boolean cartWord = CART_WORD.matcher(text).find();
		boolean followUp = removeConfirm || awaitingRemoveChoice;

		// "clear my cart", "remove everything from my cart": ask first, this one cannot
		// be undone
		if (!followUp && cartWord && (CLEAR_CART.matcher(text).find() || EVERYTHING.matcher(text).find())) {
			return Optional.of(items.isEmpty() ? "Your cart is already empty." : CLEAR_CONFIRM);
		}
		if (items.isEmpty()) {
			return cartWord || followUp ? Optional.of("Your cart is empty, so there is nothing to remove.")
					: Optional.empty();
		}

		// the books in the cart play the role of "the catalogue" here
		List<ProductResponse> cartBooks = new ArrayList<>();
		Map<Long, Integer> inCart = new LinkedHashMap<>();
		for (CartItemResponse i : items) {
			ProductResponse p = new ProductResponse();
			p.setId(i.getProductId());
			p.setName(i.getProductName());
			cartBooks.add(p);
			inCart.put(i.getProductId(), i.getQuantity());
		}

		List<ProductResponse> targets = new ArrayList<>();
		if (removeConfirm) {
			List<ProductResponse> asked = booksMentioned(fold(previous), cartBooks);
			if (asked.isEmpty()) {
				return Optional.empty();
			}
			targets.add(asked.get(0));
		} else {
			List<ProductResponse> named = booksMentioned(text, cartBooks);
			if (awaitingRemoveChoice) {
				List<ProductResponse> options = booksMentioned(fold(previous), cartBooks);
				if (!named.isEmpty()) {
					targets.add(named.get(0));
				} else {
					Integer idx = ordinalIndex(text, options.size(), true);
					if (idx == null || text.split(" ").length > 8) {
						return Optional.empty(); // not an answer to our question: the model handles the message
					}
					targets.add(options.get(idx));
				}
			} else if (!named.isEmpty()) {
				targets.addAll(named);
			} else {
				Partial partial = partialMatch(text, cartBooks);
				boolean byReference = partial.tokenCount == 0
						|| (partial.candidates.isEmpty() && REFERENCE.matcher(text).find());
				if (!byReference) {
					if (partial.candidates.isEmpty()) {
						return cartWord ? Optional.of("I couldn't find that book in your cart.") : Optional.empty();
					}
					if (partial.candidates.size() > 1) {
						return Optional.of(clarification(CLARIFY_REMOVE_PREFIX, partial.candidates));
					}
					ProductResponse only = partial.candidates.get(0);
					if (partial.tokenCount < 2) {
						return Optional.of("Did you mean \"" + only.getName() + "\"? " + REMOVE_CONFIRM_TAIL);
					}
					targets.add(only);
				} else {
					// "remove that book", "remove it", "remove the second one", "remove them all"
					if (!cartWord && !REFERENCE.matcher(text).find() && ordinalIndex(text, 99, false) == null) {
						return Optional.empty(); // e.g. "delete my account": not about the cart
					}
					List<ProductResponse> options = recentBooks(prior, cartBooks, false);
					if (options.isEmpty()) {
						options = cartBooks;
					}
					Integer idx = ordinalIndex(text, options.size(), false);
					if (idx != null) {
						targets.add(options.get(idx));
					} else if (options.size() == 1) {
						targets.add(options.get(0));
					} else if (ALL.matcher(text).find()) {
						targets.addAll(options.subList(0, Math.min(MAX_BOOKS_PER_REQUEST, options.size())));
					} else {
						return Optional.of(clarification(CLARIFY_REMOVE_PREFIX, options));
					}
				}
			}
		}

		// "remove 2 Dune" takes two copies out; "remove Dune" takes the whole line out
		Integer asked = awaitingRemoveChoice && text.matches("\\d{1,2}") ? null
				: explicitQuantity(withoutTitles(text, targets));
		if (asked != null && asked < 1) {
			return Optional.of("The quantity must be at least 1.");
		}

		List<String> removed = new ArrayList<>();
		CartResponse lastCart = null;
		for (ProductResponse p : targets.subList(0, Math.min(MAX_BOOKS_PER_REQUEST, targets.size()))) {
			int current = inCart.getOrDefault(p.getId(), 0);
			try {
				if (asked != null && asked < current) {
					lastCart = cartService.updateItemQuantity(jwt, p.getId(), current - asked);
					removed.add(copies(asked) + " of \"" + p.getName() + "\" (" + (current - asked) + " left)");
				} else {
					lastCart = cartService.removeCartItem(jwt, p.getId());
					removed.add("\"" + p.getName() + "\"");
				}
				log.info("Chatbot cart: removed productId={} (had {})", p.getId(), current);
			} catch (RuntimeException e) {
				log.warn("Chatbot cart: could not remove productId={}: {}", p.getId(), e.getMessage());
				return Optional.of(removed.isEmpty() ? CART_ERROR
						: "Removed " + String.join(" and ", removed)
								+ " from your cart, but I couldn't change the rest. Please use the Cart page.");
			}
		}
		return Optional.of("Removed " + String.join(" and ", removed) + " from your cart." + cartSummary(lastCart));
	}

	private String clearCart(Jwt jwt) {
		try {
			CartResponse cart = cartService.getMyCart(jwt);
			if (cart.getItems() == null || cart.getItems().isEmpty()) {
				return "Your cart is already empty.";
			}
			cartService.clearCartItems(cart.getCartId());
			log.info("Chatbot cart: cleared the cart");
			return "Removed everything from your cart. Your cart is now empty.";
		} catch (RuntimeException e) {
			log.warn("Chatbot cart: could not clear the cart: {}", e.getMessage());
			return CART_ERROR;
		}
	}

	// ---------------------------------------------------------------- shared
	// helpers

	private static String cartSummary(CartResponse cart) {
		if (cart == null) {
			return "";
		}
		int items = cart.getItems() == null ? 0
				: cart.getItems().stream().mapToInt(CartItemResponse::getQuantity).sum();
		if (items == 0) {
			return " Your cart is now empty.";
		}
		BigDecimal total = cart.getGrandTotal() == null ? BigDecimal.ZERO : cart.getGrandTotal();
		return " Your cart now has " + items + (items == 1 ? " item" : " items") + ", total "
				+ total.stripTrailingZeros().toPlainString() + ".";
	}

	private static String copies(int qty) {
		return qty + (qty == 1 ? " copy" : " copies");
	}

	// ---------------------------------------------------------------- resolving
	// books

	private record Mention(int index, int length, ProductResponse product, String label) {
	}

	/**
	 * A store book found in a text, with the title text that matched (the full
	 * title, or the short one before ':' or '(').
	 */
	record BookMatch(ProductResponse product, String label) {
	}

	record NameKey(String folded, String label) {
	}

	private record Partial(int tokenCount, List<ProductResponse> candidates) {
	}

	/**
	 * Store books whose full title (or short title) appears in the text, in order
	 * of appearance.
	 */
	static List<ProductResponse> booksMentioned(String foldedText, List<ProductResponse> catalogue) {
		return mentions(foldedText, catalogue).stream().map(BookMatch::product).collect(Collectors.toList());
	}

	static List<BookMatch> mentions(String foldedText, List<ProductResponse> catalogue) {
		String padded = " " + foldedText + " ";
		List<Mention> found = new ArrayList<>();
		for (ProductResponse p : catalogue) {
			for (NameKey key : nameKeys(p)) {
				int idx = padded.indexOf(" " + key.folded() + " ");
				if (idx >= 0) {
					found.add(new Mention(idx, key.folded().length(), p, key.label()));
					break;
				}
			}
		}
		// when one title lies inside another match, keep only the longer one
		List<Mention> accepted = new ArrayList<>();
		found.sort(Comparator.comparingInt((Mention m) -> -m.length));
		for (Mention m : found) {
			boolean overlaps = accepted.stream()
					.anyMatch(a -> m.index < a.index + a.length && a.index < m.index + m.length);
			if (!overlaps) {
				accepted.add(m);
			}
		}
		accepted.sort(Comparator.comparingInt(m -> m.index));
		return accepted.stream().map(m -> new BookMatch(m.product, m.label)).collect(Collectors.toList());
	}

	static List<NameKey> nameKeys(ProductResponse p) {
		String name = p.getName() == null ? "" : p.getName();
		List<NameKey> keys = new ArrayList<>();
		String full = fold(name);
		if (!full.isEmpty()) {
			keys.add(new NameKey(full, name));
		}
		int cut = -1;
		for (char c : new char[] { ':', '(' }) {
			int i = name.indexOf(c);
			if (i > 0 && (cut < 0 || i < cut)) {
				cut = i;
			}
		}
		if (cut > 0) {
			String shortLabel = name.substring(0, cut).trim(); // "Dune (Dune Chronicles, Book 1)" -> "Dune"
			String shortName = fold(shortLabel);
			if (shortName.length() >= 4 && !shortName.equals(full)) {
				keys.add(new NameKey(shortName, shortLabel));
			}
		}
		return keys;
	}

	/**
	 * Books whose title contains ALL the meaningful words of the request ("harry
	 * potter" -> the three Harry Potter books).
	 */
	private static Partial partialMatch(String text, List<ProductResponse> catalogue) {
		List<String> tokens = Arrays.stream(text.split(" "))
				.filter(t -> t.length() >= 3 && !IGNORED.contains(t) && !t.chars().allMatch(Character::isDigit))
				.distinct().collect(Collectors.toList());
		if (tokens.isEmpty()) {
			return new Partial(0, new ArrayList<>());
		}
		List<ProductResponse> candidates = catalogue.stream().filter(p -> {
			Set<String> words = new HashSet<>(Arrays.asList(fold(p.getName()).split(" ")));
			return words.containsAll(tokens);
		}).sorted(Comparator.comparing(p -> String.valueOf(p.getName()))).collect(Collectors.toList());
		return new Partial(tokens.size(), candidates);
	}

	/**
	 * The books named in the most recent assistant message(s) that mention any.
	 * When adding, results and questions of this handler are skipped (they are not
	 * recommendations); when removing they are exactly what "that book" means.
	 */
	private static List<ProductResponse> recentBooks(List<ChatMessage> prior, List<ProductResponse> catalogue,
			boolean skipOwnMessages) {
		int seen = 0;
		for (int i = prior.size() - 1; i >= 0 && seen < 3; i--) {
			ChatMessage m = prior.get(i);
			if (!ChatbotRole.ASSISTANT.name().equalsIgnoreCase(m.getRole()) || m.getContent() == null) {
				continue;
			}
			if (m.getContent().startsWith(CLARIFY_PREFIX) || m.getContent().startsWith(CLARIFY_REMOVE_PREFIX)
					|| (skipOwnMessages
							&& (m.getContent().startsWith("Added ") || m.getContent().startsWith("Removed ")))) {
				continue;
			}
			seen++;
			List<ProductResponse> books = booksMentioned(fold(m.getContent()), catalogue);
			if (!books.isEmpty()) {
				return books;
			}
		}
		return new ArrayList<>();
	}

	private static String clarification(String prefix, List<ProductResponse> options) {
		StringBuilder sb = new StringBuilder(prefix);
		int n = Math.min(MAX_BOOKS_PER_REQUEST, options.size());
		for (int i = 0; i < n; i++) {
			sb.append("\n").append(i + 1).append(". \"").append(options.get(i).getName()).append("\"");
		}
		return sb.append("\nReply with the number or the title.").toString();
	}

	/**
	 * "the second one" -> 1, "last" -> size-1, a bare "2" -> 1 (only right after we
	 * asked which book).
	 */
	static Integer ordinalIndex(String text, int size, boolean allowBareNumber) {
		for (String t : text.split(" ")) {
			Integer i = ORDINALS.get(t);
			if (i != null) {
				return i < size ? i : null;
			}
		}
		if (Arrays.asList(text.split(" ")).contains("last")) {
			return size - 1;
		}
		if (allowBareNumber && text.matches("\\d")) {
			int n = Integer.parseInt(text);
			return n >= 1 && n <= size ? n - 1 : null;
		}
		return null;
	}

	// ---------------------------------------------------------------- quantity

	private static String withoutTitles(String text, List<ProductResponse> books) {
		String t = " " + text + " ";
		for (ProductResponse p : books) {
			for (NameKey key : nameKeys(p)) {
				t = t.replace(" " + key.folded() + " ", " ");
			}
		}
		return t;
	}

	/**
	 * "2", "x2", "two copies" -> that number; nothing said -> null. Ordinals like
	 * "2nd" are not quantities.
	 */
	static Integer explicitQuantity(String text) {
		String t = " " + text.replaceAll("\\b\\d+(st|nd|rd|th)\\b", " ").replaceAll("\\bx(?=\\d)", " ")
				.replaceAll("(?<=\\d)x\\b", " ") + " ";
		Matcher m = Pattern.compile("\\b(\\d{1,4})\\b").matcher(t);
		if (m.find()) {
			return Integer.parseInt(m.group(1));
		}
		for (Map.Entry<String, Integer> e : NUMBER_WORDS.entrySet()) {
			if (t.contains(" " + e.getKey() + " ")) {
				return e.getValue();
			}
		}
		return null;
	}

	// ---------------------------------------------------------------- text helpers

	/**
	 * Lower-case, no accents, letters and digits separated by single spaces ("Giỏ
	 * hàng!" -> "gio hang").
	 */
	static String fold(String s) {
		if (s == null) {
			return "";
		}
		String lower = s.toLowerCase(Locale.ROOT).replace('đ', 'd');
		String plain = Normalizer.normalize(lower, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
		return plain.replaceAll("[^a-z0-9]+", " ").trim();
	}

	public CartIntentHandler(ProductService productService, CartService cartService) {
		super();
		this.productService = productService;
		this.cartService = cartService;
	}
}