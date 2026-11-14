package com.tgd.service.payment;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * Verifies the Stripe-Signature header of a webhook request (see "Verify webhook signatures manually" in the Stripe
 * docs): the header looks like "t=1700000000,v1=abc...,v1=def...". Stripe signs "<t>.<raw body>" with HMAC-SHA256 using
 * the endpoint's signing secret (whsec_...) and sends the lowercase hex digest. Requests whose timestamp is more than
 * the tolerance (Stripe's default: 5 minutes) away from now are rejected so a captured request cannot be replayed.
 */
public final class StripeSignature {
	public static final long DEFAULT_TOLERANCE_SECONDS = 300;

	private StripeSignature() {
	}

	public static boolean verify(byte[] payload, String header, String secret, long nowEpochSeconds, long toleranceSeconds) {
		if (payload == null || header == null || header.isBlank() || secret == null || secret.isBlank()) {
			return false;
		}
		Long timestamp = null;
		List<String> signatures = new ArrayList<>();
		for (String part : header.split(",")) {
			int eq = part.indexOf('=');
			if (eq <= 0) {
				continue;
			}
			String key = part.substring(0, eq).trim();
			String value = part.substring(eq + 1).trim();
			if (key.equals("t")) {
				try {
					timestamp = Long.parseLong(value);
				} catch (NumberFormatException e) {
					return false;
				}
			} else if (key.equals("v1")) {
				signatures.add(value); // several v1 values appear while a secret is being rotated; "v0" is ignored
			}
		}
		if (timestamp == null || signatures.isEmpty()) {
			return false;
		}
		if (Math.abs(nowEpochSeconds - timestamp) > toleranceSeconds) {
			return false;
		}
		byte[] expected = sign(secret, timestamp, payload).getBytes(StandardCharsets.UTF_8);
		for (String signature : signatures) {
			if (MessageDigest.isEqual(expected, signature.getBytes(StandardCharsets.UTF_8))) { // constant-time compare
				return true;
			}
		}
		return false;
	}

	/** The lowercase hex HMAC-SHA256 of "<timestamp>.<payload>". */
	public static String sign(String secret, long timestamp, byte[] payload) {
		try {
			Mac mac = Mac.getInstance("HmacSHA256");
			mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
			mac.update((timestamp + ".").getBytes(StandardCharsets.UTF_8));
			mac.update(payload);
			StringBuilder hex = new StringBuilder();
			for (byte b : mac.doFinal()) {
				hex.append(String.format("%02x", b));
			}
			return hex.toString();
		} catch (GeneralSecurityException e) {
			throw new IllegalStateException("HmacSHA256 is not available", e);
		}
	}
}
