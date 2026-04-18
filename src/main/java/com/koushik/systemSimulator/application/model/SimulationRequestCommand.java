package com.koushik.systemSimulator.application.model;

import java.util.List;
import java.util.Objects;

public record SimulationRequestCommand(
		List<NodeCommand> nodes,
		List<ConnectionCommand> connections,
		int requestCount
) {

	public SimulationRequestCommand {
		nodes = List.copyOf(Objects.requireNonNull(nodes, "nodes must not be null"));
		connections = List.copyOf(Objects.requireNonNull(connections, "connections must not be null"));
		if (requestCount <= 0) {
			throw new IllegalArgumentException("requestCount must be greater than zero");
		}
	}
}
