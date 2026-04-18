package com.koushik.systemSimulator.simulation.engine;

import com.koushik.systemSimulator.simulation.metrics.SimulationMetrics;
import com.koushik.systemSimulator.simulation.model.SimulationEvent;
import com.koushik.systemSimulator.simulation.state.RequestRuntimeState;

import java.util.List;

public record SimulationReport(
		long finalTime,
		List<SimulationEvent> processedEvents,
		List<RequestRuntimeState> requestStates,
		SimulationMetrics metrics
) {
}
