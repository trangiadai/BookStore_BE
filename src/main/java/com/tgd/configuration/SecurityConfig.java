package com.tgd.configuration;

import java.nio.charset.StandardCharsets;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.SecurityFilterChain;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import com.tgd.service.identity.TokenBlacklistService;

@Configuration
@EnableWebSecurity
public class SecurityConfig {
	@Value("${jwt.secret}")
	private String jwtSecret;

	@Bean
	SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
		http.csrf(csrf -> csrf.disable())
				.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
				.authorizeHttpRequests(
						auth -> auth.requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html")
								.permitAll().requestMatchers("/auth/**").permitAll()
								.requestMatchers("/users/profile").authenticated()
								.requestMatchers(HttpMethod.GET, "/users", "/users/search").hasRole("ADMIN")
								.requestMatchers(HttpMethod.GET, "/categories/**", "/products/**").permitAll()
								.requestMatchers(HttpMethod.GET, "/reviews/products/{productId}").permitAll()
								.requestMatchers("/admin/**").hasRole("ADMIN")
								.requestMatchers(HttpMethod.POST, "/categories/**", "/products/**", "/product-images/**").hasRole("ADMIN")
								.requestMatchers(HttpMethod.PUT, "/categories/**", "/products/**", "/product-images/**").hasRole("ADMIN")
								.requestMatchers(HttpMethod.DELETE, "/categories/**", "/products/**", "/product-images/**").hasRole("ADMIN")
								.requestMatchers(HttpMethod.PATCH, "/products/**", "/product-images/**").hasRole("ADMIN")
								.requestMatchers(HttpMethod.GET, "/product-images/**").hasRole("ADMIN")
								.requestMatchers(HttpMethod.GET, "/products/trash").hasRole("ADMIN")
								.anyRequest().authenticated())
				.oauth2ResourceServer(
						oauth2 -> oauth2.jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter())));

		return http.build();
	}

	// Map custom "role" claim (e.g. "ROLE_ADMIN") directly to GrantedAuthorities
	@Bean
	JwtAuthenticationConverter jwtAuthenticationConverter() {
		JwtGrantedAuthoritiesConverter authoritiesConverter = new JwtGrantedAuthoritiesConverter();
		// 1. Tells Spring to look inside the "role" claim of the JWT instead of "scope"
	    authoritiesConverter.setAuthoritiesClaimName("role");
	    
	    // 2. Prevents Spring from adding the default "SCOPE_" prefix to authorities
	    // Automatically turns "CUSTOMER" into "ROLE_CUSTOMER" for SecurityContext
	    authoritiesConverter.setAuthorityPrefix("ROLE_");

		JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
		converter.setJwtGrantedAuthoritiesConverter(authoritiesConverter);
		return converter;
	}

	@Bean
	JwtDecoder jwtDecoder(TokenBlacklistService blacklistService) {
	    SecretKey key = new SecretKeySpec(jwtSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
	    NimbusJwtDecoder jwtDecoder = NimbusJwtDecoder.withSecretKey(key).build();

	    // Define the custom Redis blacklist validator
	    OAuth2TokenValidator<Jwt> redisValidator = jwt -> {
	        String jti = jwt.getId(); // Retrieves the "jti" claim
	        if (jti != null && blacklistService.isBlacklisted(jti)) {
	            OAuth2Error error = new OAuth2Error("invalid_token", "Token has been revoked/logged out", null);
	            return OAuth2TokenValidatorResult.failure(error);
	        }
	        return OAuth2TokenValidatorResult.success();
	    };

	    // Combine Spring's default validators (exp, nbf, etc.) with your Redis validator
	    OAuth2TokenValidator<Jwt> combinedValidator = new DelegatingOAuth2TokenValidator<>(
	            JwtValidators.createDefault(),
	            redisValidator
	    );

	    // Apply the combined validator to the decoder
	    jwtDecoder.setJwtValidator(combinedValidator);

	    return jwtDecoder;
	}

	@Bean
	JwtEncoder jwtEncoder() {
		SecretKey key = new SecretKeySpec(jwtSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
		return new NimbusJwtEncoder(new ImmutableSecret<>(key));
	}

	@Bean
	PasswordEncoder passwordEncoder() {
		return new BCryptPasswordEncoder(10);
	}

	@Bean
	AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
		return config.getAuthenticationManager();
	}
}