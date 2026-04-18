package com.koushik.systemSimulator.api.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SimulationRequest {

	@NotEmpty
	private List<@Valid NodeRequest> nodes;

	@NotEmpty
	private List<@Valid ConnectionRequest> connections;

	@Positive
	private int requestCount;
}
