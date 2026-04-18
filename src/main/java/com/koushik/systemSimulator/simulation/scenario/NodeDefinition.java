package com.koushik.systemSimulator.simulation.scenario;

import com.koushik.systemSimulator.simulation.model.NodeType;

import java.util.Objects;

public record NodeDefinition(
		String nodeId,
		NodeType nodeType,
		int capacity,
		int queueLimit,
		long processingLatency,
		String downstreamNodeId
) {

	public NodeDefinition {
		Objects.requireNonNull(nodeId, "nodeId must not be null");
		Objects.requireNonNull(nodeType, "nodeType must not be null");
		if (capacity < 0) {
			throw new IllegalArgumentException("capacity must be >= 0");
		}
		if (queueLimit < 0) {
			throw new IllegalArgumentException("queueLimit must be >= 0");
		}
		if (processingLatency < 0) {
			throw new IllegalArgumentException("processingLatency must be >= 0");
		}
	}
}
