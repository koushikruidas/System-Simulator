package com.koushik.systemSimulator.application.builder;

import com.koushik.systemSimulator.application.model.SimulationScenario;

public interface ScenarioBuilder {

	static ScenarioBuilder create() {
		return new DefaultScenarioBuilder();
	}

	ScenarioBuilder addLoadBalancer(String nodeId, long latency);

	ScenarioBuilder addService(String nodeId, int capacity, int queueLimit, long latency);

	ScenarioBuilder addDatabase(String nodeId, int capacity, int queueLimit, long latency);

	ScenarioBuilder connect(String sourceNodeId, String targetNodeId);

	ScenarioBuilder withRequestCount(int requestCount);

	SimulationScenario build();
}
