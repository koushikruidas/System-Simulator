package com.koushik.systemSimulator.api.exception;

import com.koushik.systemSimulator.api.dto.response.ApiErrorResponse;
import com.koushik.systemSimulator.api.dto.response.FieldViolationResponse;
import com.koushik.systemSimulator.application.builder.ScenarioValidationException;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

import java.time.Instant;
import java.util.List;

@ControllerAdvice
public class ApiExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

	@ExceptionHandler(MethodArgumentNotValidException.class)
	public ResponseEntity<ApiErrorResponse> handleValidation(MethodArgumentNotValidException exception, HttpServletRequest request) {
		List<FieldViolationResponse> fieldErrors = exception.getBindingResult().getFieldErrors().stream()
				.map(this::toFieldViolation)
				.toList();
		return ResponseEntity.badRequest().body(
				new ApiErrorResponse(
						"INVALID_SIMULATION_REQUEST",
						"Request validation failed",
						request.getRequestURI(),
						Instant.now(),
						fieldErrors
				)
		);
	}

	@ExceptionHandler(ScenarioValidationException.class)
	public ResponseEntity<ApiErrorResponse> handleScenarioValidation(ScenarioValidationException exception, HttpServletRequest request) {
		return ResponseEntity.badRequest().body(
				new ApiErrorResponse(
						"INVALID_SIMULATION_REQUEST",
						exception.getMessage(),
						request.getRequestURI(),
						Instant.now(),
						List.of()
				)
		);
	}

	@ExceptionHandler(IllegalArgumentException.class)
	public ResponseEntity<ApiErrorResponse> handleIllegalArgument(IllegalArgumentException exception, HttpServletRequest request) {
		return ResponseEntity.badRequest().body(
				new ApiErrorResponse(
						"INVALID_SIMULATION_REQUEST",
						exception.getMessage(),
						request.getRequestURI(),
						Instant.now(),
						List.of()
				)
		);
	}

	@ExceptionHandler(Exception.class)
	public ResponseEntity<ApiErrorResponse> handleUnexpected(Exception exception, HttpServletRequest request) {
		log.error("Unexpected error", exception);
		return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
				new ApiErrorResponse(
						"INTERNAL_SERVER_ERROR",
						"Unexpected server error",
						request.getRequestURI(),
						Instant.now(),
						List.of()
				)
		);
	}

	private FieldViolationResponse toFieldViolation(FieldError fieldError) {
		return new FieldViolationResponse(fieldError.getField(), fieldError.getDefaultMessage());
	}
}
