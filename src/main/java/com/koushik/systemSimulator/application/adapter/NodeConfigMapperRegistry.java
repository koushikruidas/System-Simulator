package com.koushik.systemSimulator.application.adapter;

import com.koushik.systemSimulator.application.model.NodeConfig;
import com.koushik.systemSimulator.application.model.NodeType;
import com.koushik.systemSimulator.simulation.scenario.NodeDefinition;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

@Component
public class NodeConfigMapperRegistry {

	private final Map<NodeType, NodeConfigMapper> mappersByType;

	public NodeConfigMapperRegistry(List<NodeConfigMapper> mappers) {
		Map<NodeType, NodeConfigMapper> registry = new EnumMap<>(NodeType.class);
		for (NodeConfigMapper mapper : mappers) {
			NodeConfigMapper previous = registry.put(mapper.supportedType(), mapper);
			if (previous != null) {
				throw new IllegalStateException("Duplicate node config mapper for " + mapper.supportedType());
			}
		}
		this.mappersByType = Map.copyOf(registry);
	}

	public NodeDefinition toDomain(NodeConfig config, List<String> downstreamNodeIds) {
		NodeConfigMapper mapper = mappersByType.get(config.getNodeType());
		if (mapper == null) {
			throw new IllegalArgumentException("No node config mapper registered for type " + config.getNodeType());
		}
		return mapper.toDomain(config, downstreamNodeIds);
	}
}
