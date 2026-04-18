package com.koushik.systemSimulator.simulation.metrics;

import java.util.Map;

public record SimulationMetrics(
		long processedEvents,
		long completedRequests,
		long droppedRequests,
		long averageLatency,
		Map<String, Long> nodeProcessedCounts,
		Map<String, Long> nodeDroppedCounts
) {
}
