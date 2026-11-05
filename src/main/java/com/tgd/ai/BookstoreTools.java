package com.tgd.ai;

import java.math.BigDecimal;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

import com.tgd.dto.response.CategoryResponse;
import com.tgd.dto.response.ProductResponse;
import com.tgd.dto.response.payment.CartItemResponse;
import com.tgd.dto.response.payment.CartResponse;
import com.tgd.dto.response.payment.OrderResponse;
import com.tgd.service.CategoryService;
import com.tgd.service.ProductService;
import com.tgd.service.payment.CartService;
import com.tgd.service.payment.OrderService;

@Component
public class BookstoreTools {
	private static final Logger log = LoggerFactory.getLogger(BookstoreTools.class);

	private static final int CATALOG_SCAN_LIMIT = 1000;
	private static final int DEFAULT_RESULTS = 5;
	private static final int MAX_RESULTS = 10;
	private static final int DESCRIPTION_PREVIEW_CHARS = 220;

	// Words that carry no search meaning ("recommend me some good fantasy books" ->
	// "fantasy")
	private static final Set<String> STOP_WORDS = new HashSet<>(Arrays.asList("a", "an", "the", "of", "by", "for", "in",
			"on", "with", "and", "or", "to", "me", "my", "i", "some", "any", "book", "books", "novel", "novels", "read",
			"reading", "recommend", "recommendation", "recommendations", "suggest", "like", "similar", "about", "want",
			"need", "good", "best", "great", "please", "find", "show", "have", "do", "you", "there", "is", "are",
			"something", "anything", "another", "other", "more", "store", "bookstore", "buy", "add", "cart", "story",
			"stories", "tale", "tales", "new", "latest", "popular", "famous", "available"));

	// Everyday genre words -> the category they belong to. A hit is a strong signal
	// ("detective" => Mystery & Thriller).
	private static final java.util.Map<String, String> GENRE_ALIASES = java.util.Map.ofEntries(
			java.util.Map.entry("detective", "mystery"), java.util.Map.entry("detectives", "mystery"),
			java.util.Map.entry("crime", "mystery"), java.util.Map.entry("whodunit", "mystery"),
			java.util.Map.entry("murder", "mystery"), java.util.Map.entry("suspense", "thriller"),
			java.util.Map.entry("memoir", "biography"), java.util.Map.entry("memoirs", "biography"),
			java.util.Map.entry("autobiography", "biography"), java.util.Map.entry("programming", "technology"),
			java.util.Map.entry("coding", "technology"), java.util.Map.entry("software", "technology"),
			java.util.Map.entry("habits", "self-help"), java.util.Map.entry("productivity", "self-help"),
			java.util.Map.entry("motivation", "self-help"), java.util.Map.entry("futuristic", "science fiction"));

	// Content words that usually point to a genre. A softer signal, because the
	// word may also be a plain title word.
	private static final java.util.Map<String, String> CONTENT_HINTS = java.util.Map.ofEntries(
			java.util.Map.entry("dragon", "fantasy"), java.util.Map.entry("dragons", "fantasy"),
			java.util.Map.entry("magic", "fantasy"), java.util.Map.entry("wizard", "fantasy"),
			java.util.Map.entry("wizards", "fantasy"), java.util.Map.entry("space", "science fiction"));

	// Results scoring below this share of the best match are dropped (no unrelated
	// filler)
	private static final double RELEVANCE_CUTOFF = 0.35;

	// A message may use at most this many tool calls; a model stuck in a loop gets
	// cut off.
	private static final int MAX_TOOL_CALLS_PER_MESSAGE = 6;
	private final ThreadLocal<Integer> toolCalls = ThreadLocal.withInitial(() -> 0);

	public static class ToolBudgetExceededException extends RuntimeException {
		public ToolBudgetExceededException() {
			super("Tool-call limit reached for this message. Do not call any more tools; answer the customer now with what you already know.");
		}
	}

	/** Called by ChatbotService before every customer message. */
	public void startNewMessage() {
		toolCalls.set(0);
	}

	private void checkBudget(String tool) {
		int n = toolCalls.get() + 1;
		toolCalls.set(n);
		if (n > MAX_TOOL_CALLS_PER_MESSAGE) {
			log.warn("AI tool-call limit reached ({}); refusing {}", MAX_TOOL_CALLS_PER_MESSAGE, tool);
			throw new ToolBudgetExceededException();
		}
	}

	private final CartService cartService;
	private final OrderService orderService;
	private final ProductService productService;
	private final CategoryService categoryService;

	// ---- Result types (serialized to JSON for the model). The import price is
	// deliberately NOT included. ----

	public record BookInfo(Long productId, String title, String category, BigDecimal price, boolean inStock,
			int availableQuantity, Double rating, String description) {
	}

	public record SearchResult(int count, String note, List<BookInfo> books) {
	}

	// Helper method to safely extract the Jwt object from the security context
	private Jwt getCurrentJwt() {
		Authentication auth = SecurityContextHolder.getContext().getAuthentication();
		if (auth != null && auth.getPrincipal() instanceof Jwt jwt) {
			return jwt;
		}
		throw new RuntimeException("Unauthorized: No valid JWT found in security context");
	}

	// 1. Cart Inspection Tool
	@Tool(description = "Get the logged-in customer's shopping cart: items, quantities, prices and total.")
	public CartResponse getCustomerCart() {
		checkBudget("getCustomerCart");
		Jwt jwt = getCurrentJwt();
		return cartService.getMyCart(jwt);
	}

	// 2. Order Tracking & Status Tool
	@Tool(description = "Get the logged-in customer's orders with their delivery and payment status.")
	public List<OrderResponse> getCustomerOrders() {
		checkBudget("getCustomerOrders");
		Jwt jwt = getCurrentJwt();
		return orderService.getUserOrders(jwt);
	}

	// 4. Catalogue: categories
	@Tool(description = "List the genres/categories the store sells.")
	public List<String> listCategories() {
		checkBudget("listCategories");
		return categoryService.getAllCategories().stream().map(CategoryResponse::getName).collect(Collectors.toList());
	}

	// 5. Catalogue: search (ALWAYS used before recommending anything)
	@Tool(description = "Search OUR STORE's catalogue (title, author, genre, description). Call it before recommending, describing or adding any book. "
			+ "Returns only books that exist in the store, with productId, price and stock. An empty query browses the top-rated books.")
	public SearchResult searchBooks(
			@ToolParam(description = "Title, author, genre or topic, e.g. 'harry potter', 'Neil Gaiman', 'detective'. Empty to browse.", required = false) String query,
			@ToolParam(description = "Optional genre/category name", required = false) String category,
			@ToolParam(description = "Optional maximum price", required = false) Double maxPrice,
			@ToolParam(description = "Number of books, 1-10 (default 5)", required = false) Integer limit) {
		checkBudget("searchBooks");

		int max = limit == null ? DEFAULT_RESULTS : Math.max(1, Math.min(limit, MAX_RESULTS));
		String phrase = fold(query).replace("sci-fi", "science fiction").replace("scifi", "science fiction").trim();
		List<String> tokens = tokenize(phrase);
		String categoryFilter = fold(category).trim();
		BigDecimal priceCap = maxPrice == null ? null : BigDecimal.valueOf(maxPrice);
		boolean textSearch = !tokens.isEmpty();

		List<ProductResponse> catalogue = productService.getAllProducts(0, CATALOG_SCAN_LIMIT).getContent();
		List<Scored> ranked = catalogue.stream()
				.filter(p -> categoryFilter.isEmpty() || fold(p.getCategoryName()).contains(categoryFilter))
				.filter(p -> priceCap == null
						|| (p.getSellingPrice() != null && p.getSellingPrice().compareTo(priceCap) <= 0))
				.map(p -> new Scored(p, score(p, phrase, tokens))).filter(s -> !textSearch || s.score > 0)
				.sorted(Comparator.<Scored>comparingInt(s -> -s.score).thenComparing(s -> !isInStock(s.product)) // books
																													// in
																													// stock
																													// first
						.thenComparing(s -> -(s.product.getAverageRating() == null ? 0d : s.product.getAverageRating()))
						.thenComparing(s -> String.valueOf(s.product.getName())))
				.collect(Collectors.toList());
		int best = ranked.isEmpty() ? 0 : ranked.get(0).score;
		double minScore = textSearch ? Math.max(1, Math.ceil(best * RELEVANCE_CUTOFF)) : 0;
		List<BookInfo> books = ranked.stream().filter(s -> s.score >= minScore).limit(max).map(s -> toInfo(s.product))
				.collect(Collectors.toList());

		log.info("AI tool searchBooks(query='{}', category='{}', maxPrice={}) -> {} result(s)", query, category,
				maxPrice, books.size());

		if (books.isEmpty()) {
			return new SearchResult(0,
					"NO book in our store matches this search. Tell the customer the store does not currently have it. "
							+ "You may then suggest well-known books from general knowledge, but clearly label them as NOT currently in our store, "
							+ "and never offer to add them to the cart.",
					new ArrayList<>());
		}
		return new SearchResult(books.size(),
				"These books exist in our store. Recommend only from this list; quote the prices exactly as given.",
				books);
	}

	// 6. Recommendations from the cart: the whole job in ONE call, so a small model
	// does not have to chain tools
	@Tool(description = "Recommend store books based on the customer's cart: same genres as the cart items, not already in the cart, in stock. "
			+ "Use it when the customer asks for recommendations based on their cart.")
	public SearchResult recommendBooksFromCart(
			@ToolParam(description = "Number of books, 1-10 (default 5)", required = false) Integer limit) {
		checkBudget("recommendBooksFromCart");
		int max = limit == null ? DEFAULT_RESULTS : Math.max(1, Math.min(limit, MAX_RESULTS));

		CartResponse cart;
		try {
			cart = cartService.getMyCart(getCurrentJwt());
		} catch (RuntimeException e) {
			log.warn("AI tool recommendBooksFromCart could not read the cart: {}", e.getMessage());
			return new SearchResult(0,
					"The cart could not be read right now. Ask the customer which genres they like and use searchBooks.",
					new ArrayList<>());
		}
		List<CartItemResponse> items = cart.getItems() == null ? new ArrayList<>() : cart.getItems();
		if (items.isEmpty()) {
			return new SearchResult(0,
					"The customer's cart is empty, so there is nothing to base recommendations on. Ask which genres or authors they like, then use searchBooks.",
					new ArrayList<>());
		}

		Set<Long> inCart = new HashSet<>();
		java.util.Map<String, Integer> genreWeight = new java.util.LinkedHashMap<>();
		List<String> cartTitles = new ArrayList<>();
		for (CartItemResponse item : items) {
			inCart.add(item.getProductId());
			cartTitles.add(item.getProductName());
			try {
				genreWeight.merge(productService.getProductById(item.getProductId()).getCategoryName(), 1,
						Integer::sum);
			} catch (IllegalArgumentException e) {
				// product no longer sold: ignore it
			}
		}

		List<ProductResponse> catalogue = productService.getAllProducts(0, CATALOG_SCAN_LIMIT).getContent();
		List<BookInfo> books = catalogue.stream()
				.filter(p -> !inCart.contains(p.getId()) && isInStock(p)
						&& genreWeight.containsKey(p.getCategoryName()))
				.sorted(Comparator.<ProductResponse>comparingInt(p -> -genreWeight.get(p.getCategoryName()))
						.thenComparing(p -> -(p.getAverageRating() == null ? 0d : p.getAverageRating()))
						.thenComparing(p -> String.valueOf(p.getName())))
				.limit(max).map(BookstoreTools::toInfo).collect(Collectors.toList());

		log.info("AI tool recommendBooksFromCart(cart={}) -> {} result(s)", cartTitles, books.size());
		String genres = String.join(", ", genreWeight.keySet());
		if (books.isEmpty()) {
			return new SearchResult(0, "The cart holds " + cartTitles + " (genres: " + genres
					+ ") but no other in-stock books in those genres exist. Say so, then use searchBooks for something related.",
					new ArrayList<>());
		}
		return new SearchResult(books.size(), "The cart holds " + cartTitles + " (genres: " + genres
				+ "). These in-stock store books are in the same genres and not in the cart yet. Recommend from this list and mention the shared genre.",
				books);
	}

	// ---------------------------------------------------------------- helpers

	private static boolean isInStock(ProductResponse p) {
		return p.getQuantity() != null && p.getQuantity() > 0;
	}

	private static BookInfo toInfo(ProductResponse p) {
		int stock = p.getQuantity() == null ? 0 : Math.max(0, p.getQuantity());
		Double rating = p.getAverageRating() == null || p.getAverageRating() == 0d ? null : p.getAverageRating();
		return new BookInfo(p.getId(), p.getName(), p.getCategoryName(), p.getSellingPrice(), stock > 0, stock, rating,
				shorten(p.getDescription(), DESCRIPTION_PREVIEW_CHARS));
	}

	private record Scored(ProductResponse product, int score) {
	}

	/**
	 * Title match counts most, then genre, then description (which holds the
	 * author).
	 */
	private static int score(ProductResponse p, String phrase, List<String> tokens) {
		String name = fold(p.getName());
		String category = fold(p.getCategoryName());
		String description = fold(p.getDescription());
		int s = 0;
		if (!phrase.isEmpty()) {
			if (name.equals(phrase)) {
				s += 20;
			} else if (tokens.size() > 1 && name.contains(phrase)) {
				s += 8; // a multi-word phrase inside the title ("harry potter"); single words are
						// scored below
			}
		}
		for (String t : tokens) {
			if (matches(name, t)) {
				s += 3;
			}
			if (matches(category, t)) {
				s += 2;
			}
			String genre = GENRE_ALIASES.get(t);
			if (genre != null && category.contains(genre)) {
				s += 4;
			}
			String hint = CONTENT_HINTS.get(t);
			if (hint != null && category.contains(hint)) {
				s += 2;
			}
			if (matches(description, t)) {
				s += 1;
			}
		}
		return s;
	}

	/**
	 * Substring match that also accepts the singular of a plural ("dragons" finds
	 * "dragon").
	 */
	private static boolean matches(String text, String token) {
		return text.contains(token)
				|| (token.length() > 3 && token.endsWith("s") && text.contains(token.substring(0, token.length() - 1)));
	}

	/** Lower-case and strip accents so 'Nguyen' matches 'Nguyễn'. */
	private static String fold(String s) {
		if (s == null) {
			return "";
		}
		String lower = s.toLowerCase(Locale.ROOT).replace('đ', 'd');
		return Normalizer.normalize(lower, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
	}

	private static List<String> tokenize(String folded) {
		List<String> tokens = new ArrayList<>();
		for (String t : folded.split("[^\\p{L}\\p{N}]+")) {
			if (t.length() > 1 && !STOP_WORDS.contains(t) && !t.chars().allMatch(Character::isDigit)) {
				tokens.add(t);
			}
		}
		return tokens;
	}

	private static String shorten(String text, int max) {
		if (text == null) {
			return "";
		}
		return text.length() <= max ? text : text.substring(0, max - 1).trim() + "…";
	}

	public BookstoreTools(CartService cartService, OrderService orderService, ProductService productService,
			CategoryService categoryService) {
		super();
		this.cartService = cartService;
		this.orderService = orderService;
		this.productService = productService;
		this.categoryService = categoryService;
	}

}