package com.tgd.service.payment;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Set;

/**
 * Stripe wants amounts as whole numbers in the currency's smallest unit: 1999 = 19.99 USD, but 30000 = 30,000 VND
 * because VND has no cents ("zero-decimal" currencies, see the Stripe currencies page).
 */
public final class StripeMoney {
	private static final Set<String> ZERO_DECIMAL = Set.of("bif", "clp", "djf", "gnf", "jpy", "kmf", "krw", "mga", "pyg",
			"rwf", "vnd", "vuv", "xaf", "xof", "xpf");
	private static final Set<String> THREE_DECIMAL = Set.of("bhd", "jod", "kwd", "omr", "tnd");

	private StripeMoney() {
	}

	/** Rounds half-up to the smallest unit. For VND a price like 30015.5 becomes 30016 (Stripe cannot charge fractions). */
	public static long toMinorUnits(BigDecimal amount, String currency) {
		String cur = currency == null ? "" : currency.trim().toLowerCase();
		if (cur.length() != 3) {
			throw new IllegalArgumentException("Invalid currency code: " + currency);
		}
		if (THREE_DECIMAL.contains(cur)) {
			throw new IllegalArgumentException("Three-decimal currencies are not supported: " + cur);
		}
		if (amount == null || amount.signum() <= 0) {
			throw new IllegalArgumentException("The amount to pay must be greater than zero.");
		}
		BigDecimal minor = ZERO_DECIMAL.contains(cur) ? amount : amount.movePointRight(2);
		return minor.setScale(0, RoundingMode.HALF_UP).longValueExact();
	}
}
