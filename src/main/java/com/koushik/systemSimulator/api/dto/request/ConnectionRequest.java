package com.koushik.systemSimulator.api.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConnectionRequest {

	@NotBlank
	private String sourceNodeId;

	@NotBlank
	private String targetNodeId;
}
