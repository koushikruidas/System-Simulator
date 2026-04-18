package com.koushik.systemSimulator.api.dto.response;

import java.time.Instant;
import java.util.List;

public record ApiErrorResponse(
		String code,
		String message,
		String path,
		Instant timestamp,
		List<FieldViolationResponse> fieldErrors
) {
}
