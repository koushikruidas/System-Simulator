package com.koushik.systemSimulator.application.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.Singular;

import java.util.List;

@Getter
@Builder
@AllArgsConstructor
public class SimulationCommand {

	@Singular("node")
	private final List<NodeConfig> nodes;

	@Singular("connection")
	private final List<ConnectionConfig> connections;

	private final int requestCount;

	private final Integer arrivalRate;

	private final Integer simulationDuration;

	private final String entryNodeId;

	public boolean isTimeBased() {
		return arrivalRate != null && simulationDuration != null;
	}

	public int getTotalRequests() {
		return isTimeBased() ? arrivalRate * simulationDuration : requestCount;
	}
}
