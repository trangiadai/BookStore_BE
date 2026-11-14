package com.tgd.controller.payment;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.tgd.dto.response.payment.PaymentSessionResponse;
import com.tgd.service.payment.PaymentService;

@RestController
@RequestMapping("/orders")
public class PaymentController {
	private final PaymentService paymentService;

	/** Creates the Stripe payment page for one of the signed-in customer's BANK_TRANSFER orders. */
	@PostMapping("/{id}/payment-session")
	public PaymentSessionResponse createPaymentSession(@AuthenticationPrincipal Jwt jwt, @PathVariable("id") Long orderId) {
		return new PaymentSessionResponse(paymentService.createPaymentSession(jwt, orderId));
	}

	public PaymentController(PaymentService paymentService) {
		super();
		this.paymentService = paymentService;
	}
}
