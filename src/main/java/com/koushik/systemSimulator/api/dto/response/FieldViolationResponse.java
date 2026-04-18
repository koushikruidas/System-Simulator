package com.koushik.systemSimulator.api.dto.response;

public record FieldViolationResponse(
		String field,
		String message
) {
}
