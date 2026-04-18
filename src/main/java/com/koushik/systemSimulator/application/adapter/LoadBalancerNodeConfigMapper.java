package com.koushik.systemSimulator.application.adapter;

import com.koushik.systemSimulator.application.model.NodeConfig;
import com.koushik.systemSimulator.application.model.NodeType;
import com.koushik.systemSimulator.simulation.scenario.NodeDefinition;
import org.springframework.stereotype.Component;

@Component
public class LoadBalancerNodeConfigMapper implements NodeConfigMapper {

	@Override
	public NodeType supportedType() {
		return NodeType.LOAD_BALANCER;
	}

	@Override
	public NodeDefinition toDomain(NodeConfig config, String downstreamNodeId) {
		return new NodeDefinition(
				config.getNodeId(),
				com.koushik.systemSimulator.simulation.model.NodeType.DELAY_LOAD_BALANCER,
				1,
				0,
				valueOrDefault(config.getLatency(), 0L),
				downstreamNodeId
		);
	}

	private long valueOrDefault(Long value, long defaultValue) {
		return value == null ? defaultValue : value;
	}
}
