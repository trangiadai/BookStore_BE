package com.tgd.controller.payment;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.tgd.service.payment.PaymentService;

/**
 * Stripe calls this endpoint (no login: the Stripe-Signature header is the proof). The body must be read as raw bytes,
 * exactly as Stripe sent it, because the signature is computed over those bytes.
 */
@RestController
@RequestMapping("/stripe")
public class StripeWebhookController {
	private final PaymentService paymentService;

	@PostMapping("/webhook")
	public ResponseEntity<Void> receive(@RequestBody byte[] payload,
			@RequestHeader(value = "Stripe-Signature", required = false) String signature) {
		paymentService.handleWebhook(payload, signature);
		return ResponseEntity.ok().build(); // any 2xx tells Stripe the event was received
	}

	public StripeWebhookController(PaymentService paymentService) {
		super();
		this.paymentService = paymentService;
	}
}
