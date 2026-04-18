package com.koushik.systemSimulator.simulation.metrics;

import com.koushik.systemSimulator.simulation.model.SimulationEvent;

import java.util.List;

public interface MetricsCollector {

	void recordEventProcessed(SimulationEvent event);

	void recordSignals(List<MetricSignal> metricSignals);

	SimulationMetrics snapshot();
}
