package com.koushik.systemSimulator.simulation.node;

import com.koushik.systemSimulator.simulation.scenario.NodeDefinition;
import com.koushik.systemSimulator.simulation.scenario.Topology;

import java.util.LinkedHashMap;
import java.util.Map;

public final class NodeRegistry {

	private final Topology topology;
	private final Map<String, SimNode> nodesById = new LinkedHashMap<>();

	public NodeRegistry(Topology topology, Map<String, SimNode> nodesById) {
		this.topology = topology;
		this.nodesById.putAll(nodesById);
	}

	public SimNode getNode(String nodeId) {
		SimNode node = nodesById.get(nodeId);
		if (node == null) {
			throw new IllegalArgumentException("No node registered for nodeId=" + nodeId);
		}
		return node;
	}

	public NodeDefinition getDefinition(String nodeId) {
		return topology.getNodeDefinition(nodeId);
	}

	public Topology topology() {
		return topology;
	}
}
