package com.koushik.systemSimulator.application.adapter;

import com.koushik.systemSimulator.application.model.NodeConfig;
import com.koushik.systemSimulator.application.model.NodeType;
import com.koushik.systemSimulator.simulation.config.TimeConverter;
import com.koushik.systemSimulator.simulation.scenario.NodeDefinition;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class CacheNodeConfigMapper implements NodeConfigMapper {

	@Override
	public NodeType supportedType() {
		return NodeType.CACHE;
	}

	@Override
	public NodeDefinition toDomain(NodeConfig config, List<String> downstreamNodeIds, boolean realWorldMode) {
		int capacity = requirePositive(config.getCapacity(),
				"Cache node " + config.getNodeId() + " must define a positive capacity");
		int queueLimit = config.getQueueLimit() != null ? config.getQueueLimit() : capacity;
		long missLatency = config.getLatency() != null ? config.getLatency() : 0L;
		double hitRate = config.getHitRate() != null ? config.getHitRate() : 0.0;
		long hitLatency = config.getHitLatency() != null ? config.getHitLatency() : 0L;
		String downstream = downstreamNodeIds.isEmpty() ? null : downstreamNodeIds.get(0);

		if (realWorldMode) {
			TimeConverter conv = TimeConverter.defaultConverter();
			capacity = conv.rpsToCapacityTicks(capacity);
			queueLimit = config.getQueueLimit() != null ? config.getQueueLimit() : capacity;
			missLatency = conv.msToLatencyTicks(missLatency);
			hitLatency = conv.msToLatencyTicks(hitLatency);
		}

		return new NodeDefinition(
				config.getNodeId(),
				com.koushik.systemSimulator.simulation.model.NodeType.CACHE,
				capacity,
				queueLimit,
				missLatency,
				downstream,
				hitRate,
				hitLatency
		);
	}

	private int requirePositive(Integer value, String message) {
		if (value == null || value <= 0) {
			throw new IllegalArgumentException(message);
		}
		return value;
	}
}
