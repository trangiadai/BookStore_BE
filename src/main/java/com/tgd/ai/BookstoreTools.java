package com.tgd.ai;

import java.util.List;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

import com.tgd.dto.response.payment.CartResponse;
import com.tgd.dto.response.payment.OrderResponse;
import com.tgd.service.payment.CartService;
import com.tgd.service.payment.OrderService;

@Component
public class BookstoreTools {
	private final CartService cartService;
	private final OrderService orderService;

	// Helper method to safely extract the Jwt object from the security context
	private Jwt getCurrentJwt() {
		Authentication auth = SecurityContextHolder.getContext().getAuthentication();
		if (auth != null && auth.getPrincipal() instanceof Jwt jwt) {
			return jwt;
		}
		throw new RuntimeException("Unauthorized: No valid JWT found in security context");
	}

	// 1. Cart Inspection Tool
	@Tool(description = "Get the shopping cart items, product names, quantities, unit prices, and grand total for the currently logged-in customer.")
	public CartResponse getCustomerCart() {
		Jwt jwt = getCurrentJwt();
		return cartService.getMyCart(jwt);
	}

	// 2. Order Tracking & Status Tool
	@Tool(description = "Get the list of all orders, order codes, delivery tracking statuses, payment statuses, and ordered items for the currently logged-in customer.")
	public List<OrderResponse> getCustomerOrders() {
		Jwt jwt = getCurrentJwt();
		return orderService.getUserOrders(jwt);
	}

	// 3. Cart-Based Recommendations Tool
	@Tool(description = "Inspect the books currently in the customer's shopping cart so the AI can provide personalized book recommendations based on those selections.")
	public CartResponse getCartForRecommendations() {
		Jwt jwt = getCurrentJwt();
		return cartService.getMyCart(jwt);
	}

	public BookstoreTools(CartService cartService, OrderService orderService) {
		super();
		this.cartService = cartService;
		this.orderService = orderService;
	}

}