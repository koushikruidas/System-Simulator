package com.koushik.systemSimulator.simulation.metrics;

public record MetricSignal(
		MetricType metricType,
		String nodeId,
		String requestId,
		long value
) {
}
