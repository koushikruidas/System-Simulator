package com.koushik.systemSimulator.simulation.scenario;

import java.util.Objects;

public record LinkDefinition(
		String sourceNodeId,
		String targetNodeId
) {

	public LinkDefinition {
		Objects.requireNonNull(sourceNodeId, "sourceNodeId must not be null");
		Objects.requireNonNull(targetNodeId, "targetNodeId must not be null");
	}
}
