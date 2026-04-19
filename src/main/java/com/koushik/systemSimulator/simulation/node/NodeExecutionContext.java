package com.koushik.systemSimulator.simulation.node;

import com.koushik.systemSimulator.simulation.engine.SequenceNumberGenerator;
import com.koushik.systemSimulator.simulation.model.EventType;
import com.koushik.systemSimulator.simulation.model.Request;
import com.koushik.systemSimulator.simulation.model.SimulationEvent;
import com.koushik.systemSimulator.simulation.scenario.NodeDefinition;
import com.koushik.systemSimulator.simulation.scenario.Topology;
import com.koushik.systemSimulator.simulation.state.NodeRuntimeState;
import com.koushik.systemSimulator.simulation.state.RequestRuntimeState;
import com.koushik.systemSimulator.simulation.state.RuntimeStateStore;

import java.util.Objects;

public final class NodeExecutionContext {

	private final long currentTime;
	private final NodeDefinition currentNodeDefinition;
	private final NodeRuntimeState currentNodeState;
	private final Topology topology;
	private final RuntimeStateStore runtimeStateStore;
	private final SequenceNumberGenerator sequenceNumberGenerator;

	public NodeExecutionContext(
			long currentTime,
			NodeDefinition currentNodeDefinition,
			NodeRuntimeState currentNodeState,
			Topology topology,
			RuntimeStateStore runtimeStateStore,
			SequenceNumberGenerator sequenceNumberGenerator
	) {
		this.currentTime = currentTime;
		this.currentNodeDefinition = Objects.requireNonNull(currentNodeDefinition, "currentNodeDefinition must not be null");
		this.currentNodeState = Objects.requireNonNull(currentNodeState, "currentNodeState must not be null");
		this.topology = Objects.requireNonNull(topology, "topology must not be null");
		this.runtimeStateStore = Objects.requireNonNull(runtimeStateStore, "runtimeStateStore must not be null");
		this.sequenceNumberGenerator = Objects.requireNonNull(sequenceNumberGenerator, "sequenceNumberGenerator must not be null");
	}

	public long now() {
		return currentTime;
	}

	public NodeDefinition currentNodeDefinition() {
		return currentNodeDefinition;
	}

	public NodeRuntimeState currentNodeState() {
		return currentNodeState;
	}

	public Topology topology() {
		return topology;
	}

	public RequestRuntimeState requestState(String requestId) {
		return runtimeStateStore.getRequestState(requestId);
	}

	public NodeRuntimeState nodeState(String nodeId) {
		return runtimeStateStore.getNodeState(nodeId);
	}

	public SimulationEvent createEvent(long timestamp, EventType eventType, Request request, String sourceNodeId, String targetNodeId) {
		long sequenceNumber = sequenceNumberGenerator.next();
		return new SimulationEvent(
				request.requestId() + "-" + eventType + "-" + sequenceNumber,
				timestamp,
				sequenceNumber,
				eventType,
				request,
				sourceNodeId,
				targetNodeId
		);
	}
}
