package com.koushik.systemSimulator.simulation.state;

import com.koushik.systemSimulator.simulation.model.Request;
import com.koushik.systemSimulator.simulation.scenario.NodeDefinition;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

public final class RuntimeStateStore {

	private final Map<String, NodeRuntimeState> nodeStates = new LinkedHashMap<>();
	private final Map<String, RequestRuntimeState> requestStates = new LinkedHashMap<>();

	public RuntimeStateStore(Collection<NodeDefinition> nodeDefinitions) {
		for (NodeDefinition nodeDefinition : nodeDefinitions) {
			nodeStates.put(nodeDefinition.nodeId(), new NodeRuntimeState());
		}
	}

	public NodeRuntimeState getNodeState(String nodeId) {
		NodeRuntimeState state = nodeStates.get(nodeId);
		if (state == null) {
			throw new IllegalArgumentException("No runtime state found for nodeId=" + nodeId);
		}
		return state;
	}

	public RequestRuntimeState getOrCreateRequestState(Request request) {
		return requestStates.computeIfAbsent(request.requestId(), ignored -> new RequestRuntimeState(request));
	}

	public RequestRuntimeState getRequestState(String requestId) {
		RequestRuntimeState state = requestStates.get(requestId);
		if (state == null) {
			throw new IllegalArgumentException("No request state found for requestId=" + requestId);
		}
		return state;
	}

	public Collection<RequestRuntimeState> allRequestStates() {
		return requestStates.values();
	}
}
