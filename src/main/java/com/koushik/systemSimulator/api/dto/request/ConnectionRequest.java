package com.koushik.systemSimulator.api.dto.request;

import jakarta.validation.constraints.NotBlank;

public record ConnectionRequest(
		@NotBlank String sourceNodeId,
		@NotBlank String targetNodeId
) {
}
