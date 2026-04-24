package com.koushik.systemSimulator.application.adapter;

import com.koushik.systemSimulator.application.model.NodeConfig;
import com.koushik.systemSimulator.application.model.NodeType;
import com.koushik.systemSimulator.simulation.config.TimeConverter;
import com.koushik.systemSimulator.simulation.scenario.NodeDefinition;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class DatabaseNodeConfigMapper implements NodeConfigMapper {

	@Override
	public NodeType supportedType() {
		return NodeType.DATABASE;
	}

	@Override
	public NodeDefinition toDomain(NodeConfig config, List<String> downstreamNodeIds, boolean realWorldMode) {
		int capacity = requirePositive(config.getCapacity(), "Database node " + config.getNodeId() + " must define a positive capacity");
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
				com.koushik.systemSimulator.simulation.model.NodeType.DATABASE,
				capacity,
				queueLimit,
				latency,
				null
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
