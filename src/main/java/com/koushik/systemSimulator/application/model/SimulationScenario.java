package com.koushik.systemSimulator.application.model;

import com.koushik.systemSimulator.simulation.scenario.Topology;

import java.util.Objects;

public record SimulationScenario(
		Topology topology,
		int requestCount
) {

	public SimulationScenario {
		Objects.requireNonNull(topology, "topology must not be null");
		if (requestCount <= 0) {
			throw new IllegalArgumentException("requestCount must be greater than zero");
		}
	}
}
