package com.koushik.systemSimulator.api.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;

public record NodeRequest(
		@NotBlank String id,
		@NotNull ApiNodeType type,
		@Positive Integer capacity,
		@PositiveOrZero Integer queueLimit,
		@PositiveOrZero Long latency
) {
}
