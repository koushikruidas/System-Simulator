package com.koushik.systemSimulator.api.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SimulationResponse {

	private int totalRequests;
	private int successfulRequests;
	private int failedRequests;
	private double averageLatency;
	private Map<String, NodeMetricsResponse> nodeMetrics;
}
