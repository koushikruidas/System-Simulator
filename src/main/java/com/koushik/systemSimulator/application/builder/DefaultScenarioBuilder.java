package com.koushik.systemSimulator.application.builder;

import com.koushik.systemSimulator.application.model.ConnectionConfig;
import com.koushik.systemSimulator.application.model.LbStrategy;
import com.koushik.systemSimulator.application.model.NodeConfig;
import com.koushik.systemSimulator.application.model.NodeType;
import com.koushik.systemSimulator.application.model.SimulationCommand;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

final class DefaultScenarioBuilder implements ScenarioBuilder {

	private final Map<String, PendingNodeConfig> nodesById = new LinkedHashMap<>();
	private final List<ConnectionConfig> connections = new ArrayList<>();
	private final Map<String, List<String>> downstreamsByNodeId = new LinkedHashMap<>();
	private int requestCount = 1;
	private String entryNodeId;

	@Override
	public ScenarioBuilder addLoadBalancer(String nodeId, LbStrategy strategy, int capacity, int queueLimit, long latency) {
		return addNode(new PendingNodeConfig(nodeId, NodeType.LOAD_BALANCER, capacity, queueLimit, latency, strategy));
	}

	@Override
	@Deprecated
	public ScenarioBuilder addLoadBalancer(String nodeId, long latency) {
		return addLoadBalancer(nodeId, LbStrategy.ROUND_ROBIN, 1, 0, latency);
	}

	@Override
	public ScenarioBuilder addService(String nodeId, int capacity, int queueLimit, long latency) {
		return addNode(new PendingNodeConfig(nodeId, NodeType.SERVICE, capacity, queueLimit, latency, null));
	}

	@Override
	public ScenarioBuilder addDatabase(String nodeId, int capacity, int queueLimit, long latency) {
		return addNode(new PendingNodeConfig(nodeId, NodeType.DATABASE, capacity, queueLimit, latency, null));
	}

	@Override
	public ScenarioBuilder addCache(String nodeId, int capacity, int queueLimit,
									double hitRate, long hitLatency, long missLatency) {
		return addNode(new PendingNodeConfig(nodeId, NodeType.CACHE, capacity, queueLimit, missLatency, null, hitRate, hitLatency));
	}

	@Override
	public ScenarioBuilder connect(String sourceNodeId, String targetNodeId) {
		requireNodeExists(sourceNodeId);
		requireNodeExists(targetNodeId);
		validateConnection(sourceNodeId, targetNodeId);
		boolean isLb = nodesById.get(sourceNodeId).nodeType() == NodeType.LOAD_BALANCER;
		List<String> existing = downstreamsByNodeId.computeIfAbsent(sourceNodeId, k -> new ArrayList<>());
		if (!isLb && !existing.isEmpty()) {
			throw new ScenarioValidationException("Node " + sourceNodeId + " already has a downstream connection");
		}
		existing.add(targetNodeId);
		connections.add(new ConnectionConfig(sourceNodeId, targetNodeId));
		return this;
	}

	@Override
	public ScenarioBuilder withRequestCount(int requestCount) {
		if (requestCount <= 0) {
			throw new ScenarioValidationException("requestCount must be greater than zero");
		}
		this.requestCount = requestCount;
		return this;
	}

	@Override
	public ScenarioBuilder withEntryNode(String nodeId) {
		this.entryNodeId = nodeId;
		return this;
	}

	@Override
	public SimulationCommand build() {
		if (entryNodeId == null || entryNodeId.isBlank()) {
			throw new ScenarioValidationException("entryNodeId must be set before calling build()");
		}
		if (!nodesById.containsKey(entryNodeId)) {
			throw new ScenarioValidationException("Entry node '" + entryNodeId + "' does not exist in the topology");
		}
		validateTopology();
		List<NodeConfig> nodeConfigs = nodesById.values().stream()
				.map(node -> NodeConfig.builder()
						.nodeId(node.nodeId())
						.nodeType(node.nodeType())
						.capacity(node.capacity())
						.queueLimit(node.queueLimit())
						.latency(node.latency())
						.strategy(node.strategy())
						.hitRate(node.hitRate())
						.hitLatency(node.hitLatency())
						.build())
				.toList();
		return SimulationCommand.builder()
				.nodes(nodeConfigs)
				.connections(List.copyOf(connections))
				.requestCount(requestCount)
				.entryNodeId(entryNodeId)
				.build();
	}

	private ScenarioBuilder addNode(PendingNodeConfig nodeConfig) {
		Objects.requireNonNull(nodeConfig, "nodeConfig must not be null");
		if (nodesById.containsKey(nodeConfig.nodeId())) {
			throw new ScenarioValidationException("Duplicate node id " + nodeConfig.nodeId());
		}
		nodesById.put(nodeConfig.nodeId(), nodeConfig);
		return this;
	}

	private void requireNodeExists(String nodeId) {
		if (!nodesById.containsKey(nodeId)) {
			throw new ScenarioValidationException("Node " + nodeId + " does not exist");
		}
	}

	private void validateConnection(String sourceNodeId, String targetNodeId) {
		NodeType sourceType = nodesById.get(sourceNodeId).nodeType();
		NodeType targetType = nodesById.get(targetNodeId).nodeType();
		if (sourceType == NodeType.DATABASE) {
			throw new ScenarioValidationException("Database node " + sourceNodeId + " cannot have outbound connections");
		}
		if (sourceType == NodeType.SERVICE && targetType != NodeType.DATABASE) {
			throw new ScenarioValidationException("Service node " + sourceNodeId + " must connect to a database node");
		}
		if (sourceType == NodeType.CACHE && targetType != NodeType.DATABASE) {
			throw new ScenarioValidationException("Cache node " + sourceNodeId + " must connect to a database node");
		}
	}

	private void validateTopology() {
		if (nodesById.isEmpty()) {
			throw new ScenarioValidationException("At least one node is required");
		}
		long loadBalancers = nodesById.values().stream().filter(node -> node.nodeType() == NodeType.LOAD_BALANCER).count();
		if (loadBalancers < 1) {
			throw new ScenarioValidationException("At least one load balancer is required");
		}
		boolean hasServiceOrCache = nodesById.values().stream()
				.anyMatch(node -> node.nodeType() == NodeType.SERVICE || node.nodeType() == NodeType.CACHE);
		if (!hasServiceOrCache) {
			throw new ScenarioValidationException("At least one service or cache node is required");
		}
		if (nodesById.values().stream().noneMatch(node -> node.nodeType() == NodeType.DATABASE)) {
			throw new ScenarioValidationException("At least one database node is required");
		}
		if (connections.isEmpty()) {
			throw new ScenarioValidationException("At least one connection is required");
		}
		for (PendingNodeConfig node : nodesById.values()) {
			if (node.nodeType() == NodeType.DATABASE || node.nodeType() == NodeType.CACHE) {
				continue;
			}
			if (downstreamsByNodeId.getOrDefault(node.nodeId(), List.of()).isEmpty()) {
				throw new ScenarioValidationException("Node " + node.nodeId() + " must define a downstream connection");
			}
		}
	}

	private record PendingNodeConfig(
			String nodeId,
			NodeType nodeType,
			Integer capacity,
			Integer queueLimit,
			Long latency,
			LbStrategy strategy,
			Double hitRate,
			Long hitLatency
	) {

		private PendingNodeConfig(String nodeId, NodeType nodeType, Integer capacity,
								  Integer queueLimit, Long latency, LbStrategy strategy) {
			this(nodeId, nodeType, capacity, queueLimit, latency, strategy, null, null);
		}

		private PendingNodeConfig {
			Objects.requireNonNull(nodeId, "nodeId must not be null");
			Objects.requireNonNull(nodeType, "nodeType must not be null");
			if (latency == null || latency < 0) {
				throw new ScenarioValidationException("latency must be >= 0 for node " + nodeId);
			}
			if (nodeType != NodeType.LOAD_BALANCER) {
				if (capacity == null || capacity <= 0) {
					throw new ScenarioValidationException("capacity must be > 0 for node " + nodeId);
				}
				if (queueLimit == null || queueLimit < 0) {
					throw new ScenarioValidationException("queueLimit must be >= 0 for node " + nodeId);
				}
			}
		}
	}
}
