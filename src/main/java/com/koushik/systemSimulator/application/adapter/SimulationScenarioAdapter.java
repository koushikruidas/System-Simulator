package com.koushik.systemSimulator.application.adapter;

import com.koushik.systemSimulator.application.model.ConnectionConfig;
import com.koushik.systemSimulator.application.model.NodeConfig;
import com.koushik.systemSimulator.application.model.NodeType;
import com.koushik.systemSimulator.application.model.SimulationCommand;
import com.koushik.systemSimulator.simulation.scenario.LinkDefinition;
import com.koushik.systemSimulator.simulation.scenario.NodeDefinition;
import com.koushik.systemSimulator.simulation.scenario.Topology;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class SimulationScenarioAdapter {

	private final NodeConfigMapperRegistry nodeConfigMapperRegistry;

	public SimulationScenarioAdapter(NodeConfigMapperRegistry nodeConfigMapperRegistry) {
		this.nodeConfigMapperRegistry = nodeConfigMapperRegistry;
	}

	public Topology toDomainTopology(SimulationCommand command) {
		Map<String, NodeConfig> nodeConfigsById = new LinkedHashMap<>();
		for (NodeConfig nodeConfig : command.getNodes()) {
			if (nodeConfigsById.put(nodeConfig.getNodeId(), nodeConfig) != null) {
				throw new IllegalArgumentException("Duplicate node id " + nodeConfig.getNodeId());
			}
		}

		Map<String, List<String>> downstreamsBySource = new LinkedHashMap<>();
		List<LinkDefinition> links = new ArrayList<>();
		for (ConnectionConfig connection : command.getConnections()) {
			if (!nodeConfigsById.containsKey(connection.getSourceNodeId())) {
				throw new IllegalArgumentException("Node " + connection.getSourceNodeId() + " does not exist");
			}
			if (!nodeConfigsById.containsKey(connection.getTargetNodeId())) {
				throw new IllegalArgumentException("Node " + connection.getTargetNodeId() + " does not exist");
			}
			NodeConfig sourceNode = nodeConfigsById.get(connection.getSourceNodeId());
			boolean isLb = sourceNode.getNodeType() == NodeType.LOAD_BALANCER;
			List<String> existing = downstreamsBySource.computeIfAbsent(
					connection.getSourceNodeId(), k -> new ArrayList<>());
			if (!isLb && !existing.isEmpty()) {
				throw new IllegalArgumentException("Node " + connection.getSourceNodeId() + " already has a downstream connection");
			}
			existing.add(connection.getTargetNodeId());
			links.add(new LinkDefinition(connection.getSourceNodeId(), connection.getTargetNodeId()));
		}

		if (!nodeConfigsById.containsKey(command.getEntryNodeId())) {
			throw new IllegalArgumentException(
					"Entry node '" + command.getEntryNodeId() + "' does not exist in the topology"
			);
		}

		validateTopology(nodeConfigsById, downstreamsBySource);

		List<NodeDefinition> nodeDefinitions = nodeConfigsById.values().stream()
				.map(nodeConfig -> nodeConfigMapperRegistry.toDomain(
						nodeConfig,
						downstreamsBySource.getOrDefault(nodeConfig.getNodeId(), List.of())))
				.toList();
		return new Topology(nodeDefinitions, links);
	}

	private void validateTopology(Map<String, NodeConfig> nodeConfigsById, Map<String, List<String>> downstreamsBySource) {
		if (nodeConfigsById.isEmpty()) {
			throw new IllegalArgumentException("At least one node is required");
		}
		long loadBalancers = nodeConfigsById.values().stream()
				.filter(node -> node.getNodeType() == NodeType.LOAD_BALANCER)
				.count();
		if (loadBalancers < 1) {
			throw new IllegalArgumentException("At least one load balancer is required");
		}
		if (nodeConfigsById.values().stream().noneMatch(node -> node.getNodeType() == NodeType.SERVICE)) {
			throw new IllegalArgumentException("At least one service node is required");
		}
		if (nodeConfigsById.values().stream().noneMatch(node -> node.getNodeType() == NodeType.DATABASE)) {
			throw new IllegalArgumentException("At least one database node is required");
		}
		for (NodeConfig nodeConfig : nodeConfigsById.values()) {
			if (nodeConfig.getNodeType() == NodeType.DATABASE) {
				continue;
			}
			List<String> downstreams = downstreamsBySource.getOrDefault(nodeConfig.getNodeId(), List.of());
			if (downstreams.isEmpty()) {
				throw new IllegalArgumentException("Node " + nodeConfig.getNodeId() + " must define a downstream connection");
			}
		}
	}
}
