package com.monarret.backend.controller;

import org.springframework.beans.TypeMismatchException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import com.monarret.backend.dto.ApiResponse;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;

/**
 * Every error leaves the API as an {@link ApiResponse} with success=false. The standard MVC exceptions keep the status
 * Spring gives them; anything else is a 500 that is logged here and never described to the client.
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

	@Override
	protected ResponseEntity<Object> handleMissingServletRequestParameter(MissingServletRequestParameterException ex,
			HttpHeaders headers, HttpStatusCode status, WebRequest request) {
		return error(status, headers, "Missing parameter: " + ex.getParameterName());
	}

	@Override
	protected ResponseEntity<Object> handleTypeMismatch(TypeMismatchException ex, HttpHeaders headers,
			HttpStatusCode status, WebRequest request) {
		return error(status, headers, "Invalid value for: " + ex.getPropertyName());
	}

	@Override
	protected ResponseEntity<Object> handleExceptionInternal(Exception ex, Object body, HttpHeaders headers,
			HttpStatusCode statusCode, WebRequest request) {
		return error(statusCode, headers, messageFor(statusCode));
	}

	@ExceptionHandler(Exception.class)
	ResponseEntity<Object> handleUnexpected(Exception ex, HttpServletRequest request) {
		log.error("Unhandled exception on {} {}", request.getMethod(), request.getRequestURI(), ex);
		return error(HttpStatus.INTERNAL_SERVER_ERROR, HttpHeaders.EMPTY, messageFor(HttpStatus.INTERNAL_SERVER_ERROR));
	}

	private static ResponseEntity<Object> error(HttpStatusCode status, HttpHeaders headers, String message) {
		return ResponseEntity.status(status).headers(headers).body(new ApiResponse<>(false, message, null));
	}

	private static String messageFor(HttpStatusCode status) {
		if (status.value() == 404) return "Not found";
		if (status.value() == 405) return "Method not allowed";
		if (status.is4xxClientError()) return "Invalid request";
		return "Something went wrong";
	}
}
