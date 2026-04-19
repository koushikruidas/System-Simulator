package com.koushik.systemSimulator.application.adapter;

import com.koushik.systemSimulator.application.model.LbStrategy;
import com.koushik.systemSimulator.application.model.NodeConfig;
import com.koushik.systemSimulator.application.model.NodeType;
import com.koushik.systemSimulator.simulation.scenario.NodeDefinition;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class LoadBalancerNodeConfigMapper implements NodeConfigMapper {

	@Override
	public NodeType supportedType() {
		return NodeType.LOAD_BALANCER;
	}

	@Override
	public NodeDefinition toDomain(NodeConfig config, List<String> downstreamNodeIds) {
		com.koushik.systemSimulator.simulation.model.NodeType domainType = mapToDomainType(config.getStrategy(), config.getNodeId());
		int capacity = requirePositive(config.getCapacity(), "Load balancer " + config.getNodeId() + " must define a positive capacity");
		int queueLimit = defaultInteger(config.getQueueLimit(), capacity);
		return new NodeDefinition(
				config.getNodeId(),
				domainType,
				capacity,
				queueLimit,
				valueOrDefault(config.getLatency(), 0L),
				null
		);
	}

	private com.koushik.systemSimulator.simulation.model.NodeType mapToDomainType(LbStrategy strategy, String nodeId) {
		if (strategy == null) {
			throw new IllegalArgumentException("Load balancer " + nodeId + " must specify a routing strategy");
		}
		return switch (strategy) {
			case ROUND_ROBIN -> com.koushik.systemSimulator.simulation.model.NodeType.ROUND_ROBIN_LOAD_BALANCER;
			case LEAST_CONNECTIONS -> com.koushik.systemSimulator.simulation.model.NodeType.LEAST_CONNECTIONS_LOAD_BALANCER;
		};
	}

	private int requirePositive(Integer value, String message) {
		if (value == null || value <= 0) {
			throw new IllegalArgumentException(message);
		}
		return value;
	}

	private int defaultInteger(Integer value, int defaultValue) {
		return value == null ? defaultValue : value;
	}

	private long valueOrDefault(Long value, long defaultValue) {
		return value == null ? defaultValue : value;
	}
}
