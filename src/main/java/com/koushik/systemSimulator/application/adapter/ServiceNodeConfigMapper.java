package com.koushik.systemSimulator.application.adapter;

import com.koushik.systemSimulator.application.model.NodeConfig;
import com.koushik.systemSimulator.application.model.NodeType;
import com.koushik.systemSimulator.simulation.config.TimeConverter;
import com.koushik.systemSimulator.simulation.scenario.NodeDefinition;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class ServiceNodeConfigMapper implements NodeConfigMapper {

	@Override
	public NodeType supportedType() {
		return NodeType.SERVICE;
	}

	@Override
	public NodeDefinition toDomain(NodeConfig config, List<String> downstreamNodeIds, boolean realWorldMode) {
		String downstreamNodeId = downstreamNodeIds.isEmpty() ? null : downstreamNodeIds.get(0);
		int capacity = requirePositive(config.getCapacity(), "Service node " + config.getNodeId() + " must define a positive capacity");
		int queueLimit = defaultInteger(config.getQueueLimit(), capacity);
		long latency = valueOrDefault(config.getLatency(), 0L);

		if (realWorldMode) {
			TimeConverter conv = TimeConverter.defaultConverter();
			capacity = conv.rpsToCapacityTicks(capacity);
			queueLimit = defaultInteger(config.getQueueLimit(), capacity);
			latency = conv.msToLatencyTicks(latency);
		}

		return new NodeDefinition(
				config.getNodeId(),
				com.koushik.systemSimulator.simulation.model.NodeType.SERVICE,
				capacity,
				queueLimit,
				latency,
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
