package com.koushik.systemSimulator.simulation.metrics;

import com.koushik.systemSimulator.simulation.model.SimulationEvent;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class InMemoryMetricsCollector implements MetricsCollector {

	private long processedEvents;
	private long completedRequests;
	private long droppedRequests;
	private long totalLatency;
	private final Map<String, Long> nodeProcessedCounts = new LinkedHashMap<>();
	private final Map<String, Long> nodeDroppedCounts = new LinkedHashMap<>();

	@Override
	public void recordEventProcessed(SimulationEvent event) {
		processedEvents++;
	}

	@Override
	public void recordSignals(List<MetricSignal> metricSignals) {
		for (MetricSignal signal : metricSignals) {
			switch (signal.metricType()) {
				case NODE_PROCESSED -> nodeProcessedCounts.merge(signal.nodeId(), signal.value(), Long::sum);
				case REQUEST_COMPLETED -> {
					completedRequests++;
					totalLatency += signal.value();
				}
				case REQUEST_DROPPED -> {
					droppedRequests++;
					nodeDroppedCounts.merge(signal.nodeId(), signal.value(), Long::sum);
				}
			}
		}
	}

	@Override
	public SimulationMetrics snapshot() {
		long averageLatency = completedRequests == 0 ? 0 : totalLatency / completedRequests;
		return new SimulationMetrics(
				processedEvents,
				completedRequests,
				droppedRequests,
				averageLatency,
				Map.copyOf(nodeProcessedCounts),
				Map.copyOf(nodeDroppedCounts)
		);
	}
}
