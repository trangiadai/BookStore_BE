package com.tgd.service.payment;

import com.tgd.dto.request.payment.AddToCartRequest;
import com.tgd.dto.response.ProductResponse;
import com.tgd.dto.response.payment.CartItemResponse;
import com.tgd.dto.response.payment.CartResponse;
import com.tgd.entity.payment.Cart;
import com.tgd.entity.payment.CartItem;
import com.tgd.repository.CartRepository;
import com.tgd.service.ProductService;

import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Service
public class CartService {
	private final CartRepository cartRepository;
	private final ProductService productService;

	public Cart getCartByAccountId(Long accountId) {
		return cartRepository.getCartByAccountId(accountId);
	}

	public CartResponse getMyCart(Jwt jwt) {
		Long accountId = Long.parseLong(jwt.getSubject());
		Cart cart = cartRepository.getCartByAccountId(accountId);

		if (cart == null) {
			CartResponse emptyResponse = new CartResponse();
			emptyResponse.setItems(new ArrayList<>());
			emptyResponse.setGrandTotal(BigDecimal.ZERO);
			return emptyResponse;
		}

		return buildCartResponse(cart);
	}

	@Transactional
	public CartResponse addToCart(Jwt jwt, AddToCartRequest request) {
		Long accountId = Long.parseLong(jwt.getSubject());

		ProductResponse product = productService.getProductById(request.getProductId());
		if (product == null) {
			throw new IllegalArgumentException("Product not found with ID: " + request.getProductId());
		}

		Cart cart = cartRepository.getCartByAccountId(accountId);

		// Quantity of this product that is already in the cart. The request is ADDED to
		// it (upsertCartItem does quantity = quantity + VALUES(quantity)), so the stock check
		// must compare the resulting total, not just the quantity of this request.
		int existingQuantity = 0;
		if (cart != null && cart.getItems() != null) {
			existingQuantity = cart.getItems().stream()
					.filter(item -> request.getProductId().equals(item.getProductId())).mapToInt(CartItem::getQuantity)
					.sum();
		}

		int stock = product.getQuantity();
		if (existingQuantity + request.getQuantity() > stock) {
			int canStillAdd = Math.max(0, stock - existingQuantity);
			if (canStillAdd == 0) {
				throw new IllegalArgumentException("You already have all " + stock + " available copies of \""
						+ product.getName() + "\" in your cart.");
			}
			throw new IllegalArgumentException("Only " + canStillAdd + " more cop" + (canStillAdd == 1 ? "y" : "ies")
					+ " of \"" + product.getName() + "\" can be added (you already have " + existingQuantity
					+ " in your cart, " + stock + " in stock).");
		}

		if (cart == null) {
			Long cartId = cartRepository.createCart(accountId);
			cart = new Cart();
			cart.setId(cartId);
			cart.setAccountId(accountId);
		}

		cartRepository.upsertCartItem(cart.getId(), request.getProductId(), request.getQuantity());

		return getMyCart(jwt);
	}

	@Transactional
	public CartResponse removeCartItem(Jwt jwt, Long productId) {
		Long accountId = Long.parseLong(jwt.getSubject());
		Cart cart = cartRepository.getCartByAccountId(accountId);

		if (cart != null) {
			cartRepository.removeCartItem(cart.getId(), productId);
		}

		return getMyCart(jwt);
	}

	@Transactional
	public int clearCartItems(Long cartId) {
		return cartRepository.clearCartItems(cartId);
	}

	private CartResponse buildCartResponse(Cart cart) {
		CartResponse response = new CartResponse();
		response.setCartId(cart.getId());

		List<CartItemResponse> itemResponses = new ArrayList<>();
		BigDecimal grandTotal = BigDecimal.ZERO;

		if (cart.getItems() != null) {
			for (CartItem item : cart.getItems()) {
				if (item.getProductId() == null)
					continue;

				ProductResponse product = productService.getProductById(item.getProductId());
				if (product != null) {
					BigDecimal subtotal = product.getSellingPrice().multiply(BigDecimal.valueOf(item.getQuantity()));
					grandTotal = grandTotal.add(subtotal);

					CartItemResponse itemResp = new CartItemResponse();
					itemResp.setProductId(product.getId());
					itemResp.setProductName(product.getName());
					itemResp.setUnitPrice(product.getSellingPrice());
					itemResp.setQuantity(item.getQuantity());
					itemResp.setSubtotal(subtotal);

					itemResponses.add(itemResp);
				}
			}
		}

		response.setItems(itemResponses);
		response.setGrandTotal(grandTotal);

		return response;
	}

	@Transactional
	public CartResponse updateItemQuantity(Jwt jwt, Long productId, Integer newQuantity) {
		Long accountId = Long.parseLong(jwt.getSubject());
		Cart cart = cartRepository.getCartByAccountId(accountId);

		if (cart == null) {
			throw new IllegalArgumentException("Cart not found");
		}

		if (newQuantity <= 0) {
			cartRepository.removeCartItem(cart.getId(), productId);
			return getMyCart(jwt);
		}

		ProductResponse product = productService.getProductById(productId);
		if (product == null) {
			throw new IllegalArgumentException("Product not found with ID: " + productId);
		}
		if (product.getQuantity() < newQuantity) {
			throw new IllegalArgumentException(
					"Requested quantity exceeds available stock (" + product.getQuantity() + ")");
		}

		cartRepository.updateCartItemQuantity(cart.getId(), productId, newQuantity);

		return getMyCart(jwt);
	}

	public CartService(CartRepository cartRepository, ProductService productService) {
		super();
		this.cartRepository = cartRepository;
		this.productService = productService;
	}

}