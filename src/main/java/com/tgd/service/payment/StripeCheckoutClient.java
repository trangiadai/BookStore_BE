package com.tgd.service.payment;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tgd.exceptions.PaymentProviderException;

/**
 * Talks to the Stripe REST API directly (form-encoded requests, secret key as Bearer token). No Stripe SDK is used:
 * the SDK deserializes responses into objects pinned to one API version, which breaks when the account / webhook
 * version differs. Only the two calls this shop needs are implemented.
 */
@Component
public class StripeCheckoutClient {
	private static final Logger log = LoggerFactory.getLogger(StripeCheckoutClient.class);
	private static final ObjectMapper JSON = new ObjectMapper();

	private final String apiKey;
	private final String apiVersion;
	private final String baseUrl;
	private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

	public record CheckoutSession(String id, String url) {
	}

	public boolean isConfigured() {
		return apiKey != null && !apiKey.isBlank();
	}

	/** POST /v1/checkout/sessions. Keys use Stripe's bracket syntax, e.g. "line_items[0][quantity]". */
	public CheckoutSession createCheckoutSession(Map<String, String> params) {
		JsonNode session = post("/v1/checkout/sessions", params);
		String id = session.path("id").asText("");
		String url = session.path("url").asText("");
		if (id.isEmpty() || url.isEmpty()) {
			throw new PaymentProviderException("Stripe did not return a payment page. Please try again.");
		}
		return new CheckoutSession(id, url);
	}

	/** POST /v1/checkout/sessions/{id}/expire: the customer can no longer pay with the old payment page. */
	public void expireCheckoutSession(String sessionId) {
		post("/v1/checkout/sessions/" + URLEncoder.encode(sessionId, StandardCharsets.UTF_8) + "/expire", Map.of());
	}

	private JsonNode post(String path, Map<String, String> params) {
		HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl + path)).timeout(Duration.ofSeconds(20))
				.header("Authorization", "Bearer " + apiKey).header("Content-Type", "application/x-www-form-urlencoded")
				.header("Stripe-Version", apiVersion).POST(BodyPublishers.ofString(form(params))).build();
		HttpResponse<String> response;
		try {
			response = http.send(request, BodyHandlers.ofString());
		} catch (IOException e) {
			throw new PaymentProviderException("Could not reach Stripe. Please try again in a moment.", e);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new PaymentProviderException("The request to Stripe was interrupted.", e);
		}

		JsonNode json;
		try {
			json = JSON.readTree(response.body());
		} catch (IOException e) {
			throw new PaymentProviderException("Stripe sent an unreadable answer (HTTP " + response.statusCode() + ").", e);
		}
		if (response.statusCode() / 100 != 2) {
			String message = json.path("error").path("message").asText("HTTP " + response.statusCode());
			log.warn("Stripe API call {} failed with HTTP {}: {}", path, response.statusCode(), message);
			throw new PaymentProviderException("Stripe: " + message);
		}
		return json;
	}

	static String form(Map<String, String> params) {
		StringBuilder body = new StringBuilder();
		for (Map.Entry<String, String> e : params.entrySet()) {
			if (body.length() > 0) {
				body.append('&');
			}
			body.append(URLEncoder.encode(e.getKey(), StandardCharsets.UTF_8)).append('=')
					.append(URLEncoder.encode(e.getValue(), StandardCharsets.UTF_8));
		}
		return body.toString();
	}

	public StripeCheckoutClient(@Value("${stripe.api-key:}") String apiKey,
			@Value("${stripe.api-version:2025-09-30.clover}") String apiVersion,
			@Value("${stripe.base-url:https://api.stripe.com}") String baseUrl) {
		super();
		this.apiKey = apiKey == null ? "" : apiKey.trim();
		this.apiVersion = apiVersion;
		this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
	}
}
