package com.koushik.systemSimulator.api.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
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

	@Min(1)
	private Integer requestCount;

	@Min(1)
	private Integer arrivalRate;

	@Min(1)
	private Integer simulationDuration;

	@NotBlank
	private String entryNodeId;

	private String timeUnit;
}
