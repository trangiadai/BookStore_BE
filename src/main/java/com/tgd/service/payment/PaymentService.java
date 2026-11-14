package com.tgd.service.payment;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.LongSupplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tgd.dao.mappers.PaymentMapper;
import com.tgd.entity.payment.Order;
import com.tgd.entity.payment.OrderItem;
import com.tgd.enums.OrderStatus;
import com.tgd.enums.PaymentMethod;
import com.tgd.enums.PaymentStatus;
import com.tgd.repository.OrderRepository;

/**
 * Online payment of BANK_TRANSFER orders with Stripe Checkout.
 *
 * 1. The customer asks to pay -> createPaymentSession() creates a Stripe Checkout session and returns its URL.
 * 2. Stripe redirects the customer back to /orders/{id} on the website.
 * 3. Stripe calls the webhook -> handleWebhook() verifies the signature and marks the order PAID. The webhook (not
 *    the redirect) is what changes the order: the redirect can be faked or never happen.
 */
@Service
public class PaymentService {
	private static final Logger log = LoggerFactory.getLogger(PaymentService.class);
	private static final ObjectMapper JSON = new ObjectMapper();

	private final OrderRepository orderRepository;
	private final PaymentMapper paymentMapper;
	private final StripeCheckoutClient stripe;
	private final String webhookSecret;
	private final String currency;
	private final String frontendUrl;
	private final String paymentMethodTypes;
	private final int expiryMinutes;
	private LongSupplier epochSeconds = () -> System.currentTimeMillis() / 1000; // replaced in tests

	// ---------------------------------------------------------------- start a payment

	/** @return the Stripe page the customer must be sent to */
	public String createPaymentSession(Jwt jwt, Long orderId) {
		Long accountId = Long.parseLong(jwt.getSubject());
		Order order = orderRepository.getOrderByIdAndAccountId(orderId, accountId);
		if (order == null) {
			throw new IllegalArgumentException("Order not found or access denied for ID: " + orderId);
		}
		if (order.getPaymentMethod() != PaymentMethod.BANK_TRANSFER) {
			throw new IllegalArgumentException("This order is paid on delivery, so there is nothing to pay online.");
		}
		if (order.getPaymentStatus() == PaymentStatus.PAID) {
			throw new IllegalStateException("This order is already paid.");
		}
		if (order.getPaymentStatus() != PaymentStatus.UNPAID) {
			throw new IllegalStateException("This order can no longer be paid (" + order.getPaymentStatus() + ").");
		}
		if (order.getOrderStatus() == OrderStatus.CANCELLED || order.getOrderStatus() == OrderStatus.REFUNDED) {
			throw new IllegalStateException("This order is " + order.getOrderStatus().name().toLowerCase() + " and can no longer be paid.");
		}
		if (!stripe.isConfigured()) {
			throw new IllegalStateException("Online payment is not set up yet. Please contact the store.");
		}
		if (Boolean.TRUE.equals(paymentMapper.isPaymentPending(orderId))) {
			// The customer already completed a delayed payment (bank transfer): paying again could charge twice
			throw new IllegalStateException("We are already waiting for your payment to arrive. This order is marked as paid automatically once it does.");
		}

		long amount = StripeMoney.toMinorUnits(order.getTotalAmount(), currency);
		int copies = order.getItems() == null ? 0 : order.getItems().stream().mapToInt(OrderItem::getQuantity).sum();

		Map<String, String> p = new LinkedHashMap<>();
		p.put("mode", "payment");
		// {CHECKOUT_SESSION_ID} is replaced by Stripe itself. The customer lands back on the order page.
		p.put("success_url", frontendUrl + "/orders/" + orderId + "?payment=success&session_id={CHECKOUT_SESSION_ID}");
		p.put("cancel_url", frontendUrl + "/orders/" + orderId + "?payment=cancelled");
		p.put("client_reference_id", String.valueOf(orderId));
		p.put("metadata[order_id]", String.valueOf(orderId));
		p.put("metadata[order_code]", String.valueOf(order.getOrderCode()));
		p.put("payment_intent_data[metadata][order_id]", String.valueOf(orderId));
		String email = jwt.getClaimAsString("email");
		if (email != null && !email.isBlank()) {
			p.put("customer_email", email);
		}
		p.put("expires_at", String.valueOf(epochSeconds.getAsLong() + expiryMinutes * 60L));
		p.put("line_items[0][quantity]", "1");
		p.put("line_items[0][price_data][currency]", currency);
		p.put("line_items[0][price_data][unit_amount]", String.valueOf(amount));
		p.put("line_items[0][price_data][product_data][name]", "Order " + order.getOrderCode());
		p.put("line_items[0][price_data][product_data][description]", copies + (copies == 1 ? " book" : " books") + ", shipping included");
		String[] types = paymentMethodTypes.isBlank() ? new String[0] : paymentMethodTypes.split("\\s*,\\s*");
		for (int i = 0; i < types.length; i++) {
			p.put("payment_method_types[" + i + "]", types[i]); // empty list = the methods enabled in the Stripe Dashboard
		}

		String previousSession = paymentMapper.getStripeSessionId(orderId);
		StripeCheckoutClient.CheckoutSession session = stripe.createCheckoutSession(p);
		paymentMapper.setStripeSession(orderId, session.id());
		log.info("Stripe checkout session {} created for order {} ({} {})", session.id(), orderId, amount, currency);

		// Only the newest payment page may stay open, so one order cannot be paid twice
		if (previousSession != null && !previousSession.isBlank() && !previousSession.equals(session.id())) {
			try {
				stripe.expireCheckoutSession(previousSession);
			} catch (RuntimeException e) {
				log.debug("Old Stripe session {} could not be expired (probably already finished): {}", previousSession, e.getMessage());
			}
		}
		return session.url();
	}

	// ---------------------------------------------------------------- the webhook

	/**
	 * @throws IllegalArgumentException when the signature is wrong or the body is not a Stripe event (answered with 400)
	 */
	public void handleWebhook(byte[] payload, String signatureHeader) {
		if (!StripeSignature.verify(payload, signatureHeader, webhookSecret, epochSeconds.getAsLong(), StripeSignature.DEFAULT_TOLERANCE_SECONDS)) {
			log.warn("Stripe webhook rejected: invalid or missing signature");
			throw new IllegalArgumentException("Invalid Stripe signature");
		}
		JsonNode event;
		try {
			event = JSON.readTree(payload);
		} catch (IOException e) {
			throw new IllegalArgumentException("The webhook body is not valid JSON");
		}
		String type = event.path("type").asText("");
		JsonNode session = event.path("data").path("object");
		log.info("Stripe event {} ({})", event.path("id").asText("?"), type);

		switch (type) {
		case "checkout.session.completed":
			// "paid" = money received (cards). "unpaid" = the customer finished but the money comes later (bank transfer):
			// then checkout.session.async_payment_succeeded follows.
			if ("paid".equals(session.path("payment_status").asText())) {
				settle(session);
			} else if ("unpaid".equals(session.path("payment_status").asText())) {
				Long orderId = orderId(session);
				if (orderId != null) {
					paymentMapper.markPaymentPending(orderId, session.path("id").asText(null));
					log.info("Order {}: waiting for the delayed payment", orderId);
				}
			}
			break;
		case "checkout.session.async_payment_succeeded":
			settle(session);
			break;
		case "checkout.session.async_payment_failed": {
			Long orderId = orderId(session);
			if (orderId != null) {
				paymentMapper.clearPaymentPending(orderId); // the customer may pay again
				log.warn("Order {}: the delayed payment failed", orderId);
			}
			break;
		}
		default:
			log.debug("Stripe event {} ignored", type);
		}
	}

	/** The money has arrived: UNPAID -> PAID, after checking it is the right amount for the right order. */
	private void settle(JsonNode session) {
		Long orderId = orderId(session);
		if (orderId == null) {
			log.warn("Stripe session {} has no order id; ignored", session.path("id").asText("?"));
			return;
		}
		Order order = orderRepository.getOrderByIdAndAccountId(orderId, null);
		if (order == null) {
			log.warn("Stripe session {} refers to unknown order {}; ignored", session.path("id").asText("?"), orderId);
			return;
		}
		long expected = StripeMoney.toMinorUnits(order.getTotalAmount(), currency);
		long actual = session.path("amount_total").asLong(-1);
		String actualCurrency = session.path("currency").asText("").toLowerCase();
		if (actual != expected || !actualCurrency.equals(currency)) {
			// Never mark an order paid for a different amount: this needs a human to look at it
			log.error("Order {}: Stripe reports {} {} but the order is {} {}. NOT marked as paid, please check it in the Stripe Dashboard.",
					orderId, actual, actualCurrency, expected, currency);
			return;
		}
		int changed = paymentMapper.markPaidIfUnpaid(orderId, session.path("id").asText(null), session.path("payment_intent").asText(null));
		if (changed == 1) {
			log.info("Order {} ({}) is now PAID", orderId, order.getOrderCode());
			if (order.getOrderStatus() == OrderStatus.CANCELLED) {
				log.warn("Order {} was CANCELLED but has just been paid: refund it in the Stripe Dashboard", orderId);
			}
		} else {
			log.info("Order {} was not UNPAID any more (duplicate event?); nothing changed", orderId);
		}
	}

	private static Long orderId(JsonNode session) {
		String ref = session.path("client_reference_id").asText("");
		if (ref.isEmpty()) {
			ref = session.path("metadata").path("order_id").asText("");
		}
		try {
			return Long.parseLong(ref);
		} catch (NumberFormatException e) {
			return null;
		}
	}

	void setClock(LongSupplier epochSeconds) {
		this.epochSeconds = epochSeconds;
	}

	public PaymentService(OrderRepository orderRepository, PaymentMapper paymentMapper, StripeCheckoutClient stripe,
			@Value("${stripe.webhook-secret:}") String webhookSecret, @Value("${stripe.currency:vnd}") String currency,
			@Value("${app.frontend-url:http://localhost:5173}") String frontendUrl,
			@Value("${stripe.payment-method-types:}") String paymentMethodTypes,
			@Value("${stripe.session-expiry-minutes:60}") int expiryMinutes) {
		super();
		this.orderRepository = orderRepository;
		this.paymentMapper = paymentMapper;
		this.stripe = stripe;
		this.webhookSecret = webhookSecret == null ? "" : webhookSecret.trim();
		this.currency = currency.trim().toLowerCase();
		this.frontendUrl = frontendUrl.endsWith("/") ? frontendUrl.substring(0, frontendUrl.length() - 1) : frontendUrl;
		this.paymentMethodTypes = paymentMethodTypes == null ? "" : paymentMethodTypes.trim();
		this.expiryMinutes = Math.max(30, Math.min(expiryMinutes, 1440)); // Stripe: between 30 minutes and 24 hours
	}
}
