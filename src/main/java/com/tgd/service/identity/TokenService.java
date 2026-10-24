package com.tgd.service.identity;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.stereotype.Service;

import com.tgd.dto.request.identity.IntrospectRequest;
import com.tgd.dto.response.identity.IntrospectResponse;

import java.time.Instant;
import java.util.UUID;

@Service
public class TokenService {
	private final JwtEncoder jwtEncoder;
	private final JwtDecoder jwtDecoder;
	private final long expirationInMs;
	private final TokenBlacklistService blacklistService;

	public String generateToken(Long accountId, String email, String role) {
		Instant now = Instant.now();
		Instant expiresAt = now.plusMillis(expirationInMs);

		JwtClaimsSet claims = JwtClaimsSet.builder()
				.issuer("self")
				.issuedAt(now)
				.expiresAt(expiresAt)
				.subject(String.valueOf(accountId))
				.claim("email", email)
				.claim("role", role)
				.claim("jti", UUID.randomUUID().toString())
				.build();

		JwsHeader jwsHeader = JwsHeader.with(MacAlgorithm.HS256).build();

		return jwtEncoder.encode(JwtEncoderParameters.from(jwsHeader, claims)).getTokenValue();
	}
	
	public IntrospectResponse introspect(IntrospectRequest request) {
		try {

			// Decodes, validates HMAC signature, and checks expiration automatically
			Jwt jwt = jwtDecoder.decode(request.getToken());
			String jti = jwt.getId();

			// Reject if blacklisted in Redis
			if (jti != null && blacklistService.isBlacklisted(jti)) {
				return new IntrospectResponse(false);
			}

			return new IntrospectResponse(true);
		} catch (JwtException e) {
			// Triggered if signature is invalid, token is expired, or malformed
			return new IntrospectResponse(false);
		}
	}

	public void logout(String token) {
		try {
			Jwt jwt = jwtDecoder.decode(token);
			String jti = jwt.getId();
			Instant expiresAt = jwt.getExpiresAt();

			if (jti != null && expiresAt != null) {
				long remainingTimeMs = expiresAt.toEpochMilli() - System.currentTimeMillis();
				blacklistService.blacklistToken(jti, remainingTimeMs);
			}
		} catch (JwtException e) {
			// Token is already invalid or expired; no action needed
		}
	}


	public TokenService(JwtEncoder jwtEncoder, JwtDecoder jwtDecoder,
			@Value("${jwt.expiration:86400000}") long expirationInMs, TokenBlacklistService blacklistService) {
		super();
		this.jwtEncoder = jwtEncoder;
		this.jwtDecoder = jwtDecoder;
		this.expirationInMs = expirationInMs;
		this.blacklistService = blacklistService;
	}
}