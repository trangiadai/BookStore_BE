package com.tgd.exceptions;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.MessageSourceResolvable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.validation.method.ParameterValidationResult;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import com.tgd.dto.response.ErrorDetail;
import com.tgd.dto.response.ValidationErrorDetail;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;

@RestControllerAdvice
public class GlobalExceptionHandler {
	private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

	private ResponseEntity<ErrorDetail> error(HttpStatus status, String message, Exception ex) {
		return new ResponseEntity<>(new ErrorDetail(message, ex.getClass().getSimpleName(), status.value()), status);
	}

	private ResponseEntity<ValidationErrorDetail> validationError(Map<String, String> errors, Exception ex) {
		HttpStatus status = HttpStatus.BAD_REQUEST;
		return new ResponseEntity<>(new ValidationErrorDetail(errors, ex.getClass().getSimpleName(), status.value()),
				status);
	}

	// ---------- 400 ----------
	@ExceptionHandler(IllegalArgumentException.class)
	public ResponseEntity<ErrorDetail> handleIllegalArgument(IllegalArgumentException ex) {
		return error(HttpStatus.BAD_REQUEST, ex.getMessage(), ex);
	}

	// @Valid @RequestBody / @Valid model attribute
	@ExceptionHandler(MethodArgumentNotValidException.class)
	public ResponseEntity<ValidationErrorDetail> handleBodyValidation(MethodArgumentNotValidException ex) {
		Map<String, String> errors = new LinkedHashMap<>();
		for (FieldError fe : ex.getBindingResult().getFieldErrors()) {
			errors.put(fe.getField(), fe.getDefaultMessage());
		}
		return validationError(errors, ex);
	}

	// @Positive / @NotBlank / @Size on @PathVariable and @RequestParam (Spring 6.1+
	// built-in)
	@ExceptionHandler(HandlerMethodValidationException.class)
	public ResponseEntity<ValidationErrorDetail> handleMethodValidation(HandlerMethodValidationException ex) {
		Map<String, String> errors = new LinkedHashMap<>();
		for (ParameterValidationResult result : ex.getParameterValidationResults()) {
			String name = result.getMethodParameter().getParameterName();
			if (name == null) {
				name = "arg" + result.getMethodParameter().getParameterIndex();
			}
			String message = result.getResolvableErrors().stream().map(MessageSourceResolvable::getDefaultMessage)
					.filter(Objects::nonNull).collect(Collectors.joining("; "));
			errors.put(name, message);
		}
		return validationError(errors, ex);
	}

	// Same constraints when a controller IS annotated with @Validated
	@ExceptionHandler(ConstraintViolationException.class)
	public ResponseEntity<ValidationErrorDetail> handleConstraintViolation(ConstraintViolationException ex) {
		Map<String, String> errors = new LinkedHashMap<>();
		for (ConstraintViolation<?> v : ex.getConstraintViolations()) {
			String path = v.getPropertyPath().toString();
			errors.put(path.substring(path.lastIndexOf('.') + 1), v.getMessage());
		}
		return validationError(errors, ex);
	}

	// Malformed JSON, or an invalid enum value in the body (e.g. paymentMethod)
	@ExceptionHandler(HttpMessageNotReadableException.class)
	public ResponseEntity<ErrorDetail> handleUnreadable(HttpMessageNotReadableException ex) {
		return error(HttpStatus.BAD_REQUEST, "Malformed or invalid request body.", ex);
	}

	// Invalid enum / number in a query or path param (e.g. ?orderStatus=FOO)
	@ExceptionHandler(MethodArgumentTypeMismatchException.class)
	public ResponseEntity<ErrorDetail> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
		return error(HttpStatus.BAD_REQUEST, "Invalid value for parameter '" + ex.getName() + "'.", ex);
	}

	@ExceptionHandler(MissingServletRequestParameterException.class)
	public ResponseEntity<ErrorDetail> handleMissingParam(MissingServletRequestParameterException ex) {
		return error(HttpStatus.BAD_REQUEST, ex.getMessage(), ex);
	}

	// ---------- 401 / 403 ----------
	// Wrong password / disabled account thrown by AuthenticationManager in
	// AuthService.login
	@ExceptionHandler(AuthenticationException.class)
	public ResponseEntity<ErrorDetail> handleAuthentication(AuthenticationException ex) {
		return error(HttpStatus.UNAUTHORIZED, "Invalid email or password.", ex);
	}

	// Thrown by @PreAuthorize once @EnableMethodSecurity is on. URL-rule denials
	// never reach here.
	@ExceptionHandler(AccessDeniedException.class)
	public ResponseEntity<ErrorDetail> handleAccessDenied(AccessDeniedException ex) {
		return error(HttpStatus.FORBIDDEN, "You do not have permission to perform this action.", ex);
	}

	// ---------- 409 ----------
	// Business-rule violations (duplicate review, edit limit, edit window...)
	@ExceptionHandler(IllegalStateException.class)
	public ResponseEntity<ErrorDetail> handleIllegalState(IllegalStateException ex) {
		return error(HttpStatus.CONFLICT, ex.getMessage(), ex);
	}

	// ---------- 500 ----------
	@ExceptionHandler(Exception.class)
	public ResponseEntity<ErrorDetail> handleGeneric(Exception ex) {
		log.error("Unhandled exception", ex);
		return error(HttpStatus.INTERNAL_SERVER_ERROR,
				ex.getMessage() != null ? ex.getMessage() : "An unexpected server error occurred.", ex);
	}
}