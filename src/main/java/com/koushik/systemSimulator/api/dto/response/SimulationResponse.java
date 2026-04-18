package com.koushik.systemSimulator.api.dto.response;

import java.util.Map;

public record SimulationResponse(
		int totalRequests,
		int successfulRequests,
		int failedRequests,
		double averageLatency,
		Map<String, NodeMetricsResponse> nodeMetrics
) {
}
