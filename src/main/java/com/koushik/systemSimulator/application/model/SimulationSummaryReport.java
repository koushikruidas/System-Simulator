package com.koushik.systemSimulator.application.model;

import java.util.Map;

public record SimulationSummaryReport(
		int totalRequests,
		int successfulRequests,
		int failedRequests,
		double averageLatency,
		Map<String, NodeSummaryMetrics> nodeMetrics
) {
}
