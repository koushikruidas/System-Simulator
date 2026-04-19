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

	private final String entryNodeId;
}
