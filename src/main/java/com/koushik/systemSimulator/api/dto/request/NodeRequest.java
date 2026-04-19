package com.koushik.systemSimulator.api.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NodeRequest {

	@NotBlank
	private String id;

	@NotNull
	private ApiNodeType type;

	@Positive
	private Integer capacity;

	@PositiveOrZero
	private Integer queueLimit;

	@PositiveOrZero
	private Long latency;

	private ApiLbStrategy strategy;
}
