package com.tgd.dao.mappers;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface PaymentMapper {
	/** Remembers the newest Stripe Checkout session of an order. */
	int setStripeSession(@Param("orderId") Long orderId, @Param("sessionId") String sessionId);

	String getStripeSessionId(@Param("orderId") Long orderId);

	/** The customer finished the Stripe page but the money (e.g. a bank transfer) has not arrived yet. */
	int markPaymentPending(@Param("orderId") Long orderId, @Param("sessionId") String sessionId);

	/** The delayed payment failed: the customer may try again. */
	int clearPaymentPending(@Param("orderId") Long orderId);

	Boolean isPaymentPending(@Param("orderId") Long orderId);

	/** UNPAID -> PAID, exactly once. Returns 1 when it changed the order, 0 when it was not UNPAID (e.g. a repeated event). */
	int markPaidIfUnpaid(@Param("orderId") Long orderId, @Param("sessionId") String sessionId,
			@Param("paymentIntentId") String paymentIntentId);
}
