package com.koushik.systemSimulator.application.adapter;

import com.koushik.systemSimulator.application.model.NodeConfig;
import com.koushik.systemSimulator.application.model.NodeType;
import com.koushik.systemSimulator.simulation.scenario.NodeDefinition;
import org.springframework.stereotype.Component;

@Component
public class ServiceNodeConfigMapper implements NodeConfigMapper {

	@Override
	public NodeType supportedType() {
		return NodeType.SERVICE;
	}

	@Override
	public NodeDefinition toDomain(NodeConfig config, String downstreamNodeId) {
		int capacity = requirePositive(config.getCapacity(), "Service node " + config.getNodeId() + " must define a positive capacity");
		int queueLimit = defaultInteger(config.getQueueLimit(), capacity);
		return new NodeDefinition(
				config.getNodeId(),
				com.koushik.systemSimulator.simulation.model.NodeType.SERVICE,
				capacity,
				queueLimit,
				valueOrDefault(config.getLatency(), 0L),
				downstreamNodeId
		);
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
