package com.koushik.systemSimulator.simulation.scenario;

import com.koushik.systemSimulator.simulation.model.NodeType;

import java.util.Objects;

public record NodeDefinition(
		String nodeId,
		NodeType nodeType,
		int capacityPerTick,
		int queueLimit,
		long processingLatencyTicks,
		String downstreamNodeId,
		double hitRate,
		long hitLatencyTicks
) {

	public NodeDefinition {
		Objects.requireNonNull(nodeId, "nodeId must not be null");
		Objects.requireNonNull(nodeType, "nodeType must not be null");
		if (capacityPerTick < 0) {
			throw new IllegalArgumentException("capacityPerTick must be >= 0");
		}
		if (queueLimit < 0) {
			throw new IllegalArgumentException("queueLimit must be >= 0");
		}
		if (processingLatencyTicks < 0) {
			throw new IllegalArgumentException("processingLatencyTicks must be >= 0");
		}
	}

	public NodeDefinition(String nodeId, NodeType nodeType, int capacityPerTick, int queueLimit,
						  long processingLatencyTicks, String downstreamNodeId) {
		this(nodeId, nodeType, capacityPerTick, queueLimit, processingLatencyTicks, downstreamNodeId, 0.0, 0L);
	}

	/** @deprecated Use {@link #hitLatencyTicks()} */
	@Deprecated
	public long hitLatency() {
		return hitLatencyTicks;
	}
}
