package com.koushik.systemSimulator.application.factory;

import com.koushik.systemSimulator.simulation.model.NodeType;
import com.koushik.systemSimulator.simulation.node.SimNode;
import com.koushik.systemSimulator.simulation.scenario.NodeDefinition;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

@Component
public class SimNodeFactoryRegistry {

	private final Map<NodeType, SimNodeFactory> factoriesByType;

	public SimNodeFactoryRegistry(List<SimNodeFactory> factories) {
		Map<NodeType, SimNodeFactory> registry = new EnumMap<>(NodeType.class);
		for (SimNodeFactory factory : factories) {
			SimNodeFactory previous = registry.put(factory.supportedType(), factory);
			if (previous != null) {
				throw new IllegalStateException("Duplicate SimNodeFactory registration for " + factory.supportedType());
			}
		}
		this.factoriesByType = Map.copyOf(registry);
	}

	public SimNode create(NodeDefinition nodeDefinition) {
		SimNodeFactory factory = factoriesByType.get(nodeDefinition.nodeType());
		if (factory == null) {
			throw new IllegalArgumentException("No SimNodeFactory registered for node type " + nodeDefinition.nodeType());
		}
		return factory.create(nodeDefinition);
	}
}
