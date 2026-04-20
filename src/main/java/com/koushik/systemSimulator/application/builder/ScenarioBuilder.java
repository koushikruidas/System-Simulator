package com.koushik.systemSimulator.application.builder;

import com.koushik.systemSimulator.application.model.LbStrategy;
import com.koushik.systemSimulator.application.model.SimulationCommand;

public interface ScenarioBuilder {

	static ScenarioBuilder create() {
		return new DefaultScenarioBuilder();
	}

	ScenarioBuilder addLoadBalancer(String nodeId, LbStrategy strategy, int capacity, int queueLimit, long latency);

	@Deprecated
	ScenarioBuilder addLoadBalancer(String nodeId, long latency);

	ScenarioBuilder addService(String nodeId, int capacity, int queueLimit, long latency);

	ScenarioBuilder addDatabase(String nodeId, int capacity, int queueLimit, long latency);

	ScenarioBuilder addCache(String nodeId, int capacity, int queueLimit,
							 double hitRate, long hitLatency, long missLatency);

	ScenarioBuilder connect(String sourceNodeId, String targetNodeId);

	ScenarioBuilder withRequestCount(int requestCount);

	ScenarioBuilder withEntryNode(String nodeId);

	SimulationCommand build();
}
