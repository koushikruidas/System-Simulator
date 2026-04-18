package com.koushik.systemSimulator.api.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Positive;

import java.util.List;

public record SimulationRequest(
		@NotEmpty List<@Valid NodeRequest> nodes,
		@NotEmpty List<@Valid ConnectionRequest> connections,
		@Positive int requestCount
) {
}
