package com.tgd.exceptions;

/** The payment provider (Stripe) could not be reached or refused a request. */
public class PaymentProviderException extends RuntimeException {
	private static final long serialVersionUID = 1L;

	public PaymentProviderException(String message) {
		super(message);
	}

	public PaymentProviderException(String message, Throwable cause) {
		super(message, cause);
	}
}
