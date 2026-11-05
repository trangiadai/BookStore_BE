package com.tgd.ai;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.tgd.document.ChatMessage;
import com.tgd.dto.response.ProductResponse;
import com.tgd.dto.response.chatbot.BookRef;
import com.tgd.enums.ChatbotRole;
import com.tgd.service.ProductService;

/**
 * Finds which store books a chat message talks about, so the chat window can
 * turn their titles into links to the book's detail page. It matches against
 * the products that are on sale right now, so a deleted book is never linked.
 */
@Component
public class BookLinker {
	private static final Logger log = LoggerFactory.getLogger(BookLinker.class);
	private static final int CATALOG_SCAN_LIMIT = 1000;
	private static final int MAX_LINKS_PER_MESSAGE = 10;

	private final ProductService productService;

	/**
	 * Books named in the text, in order of appearance, each only once. Never
	 * throws: links are a nicety.
	 */
	public List<BookRef> findBooks(String text) {
		if (text == null || text.isBlank()) {
			return new ArrayList<>();
		}
		List<ProductResponse> catalogue = catalogue();
		return catalogue == null ? new ArrayList<>() : findBooks(text, catalogue);
	}

	public List<BookRef> findBooks(String text, List<ProductResponse> catalogue) {
		List<BookRef> refs = new ArrayList<>();
		if (text == null || text.isBlank() || catalogue == null) {
			return refs;
		}
		Set<Long> seen = new HashSet<>();
		for (CartIntentHandler.BookMatch match : CartIntentHandler.mentions(CartIntentHandler.fold(text), catalogue)) {
			if (refs.size() >= MAX_LINKS_PER_MESSAGE) {
				break;
			}
			if (seen.add(match.product().getId())) {
				refs.add(new BookRef(match.product().getId(), match.label()));
			}
		}
		return refs;
	}

	/**
	 * Adds the book links to every assistant message of a saved conversation (one
	 * catalogue query for all of them).
	 */
	public void attachTo(List<ChatMessage> messages) {
		if (messages == null
				|| messages.stream().noneMatch(m -> ChatbotRole.ASSISTANT.name().equalsIgnoreCase(m.getRole()))) {
			return;
		}
		List<ProductResponse> catalogue = catalogue();
		if (catalogue == null) {
			return;
		}
		for (ChatMessage m : messages) {
			if (ChatbotRole.ASSISTANT.name().equalsIgnoreCase(m.getRole())) {
				m.setBooks(findBooks(m.getContent(), catalogue));
			}
		}
	}

	private List<ProductResponse> catalogue() {
		try {
			return productService.getAllProducts(0, CATALOG_SCAN_LIMIT).getContent();
		} catch (RuntimeException e) {
			log.warn("Book links skipped: cannot read the catalogue: {}", e.getMessage());
			return null;
		}
	}

	public BookLinker(ProductService productService) {
		super();
		this.productService = productService;
	}
}