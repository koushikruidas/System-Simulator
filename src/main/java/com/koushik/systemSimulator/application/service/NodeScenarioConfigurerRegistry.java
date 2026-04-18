package com.koushik.systemSimulator.application.service;

import com.koushik.systemSimulator.api.dto.request.ApiNodeType;
import com.koushik.systemSimulator.application.builder.ScenarioValidationException;
import com.koushik.systemSimulator.application.builder.ScenarioBuilder;
import com.koushik.systemSimulator.application.model.NodeCommand;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

@Component
public class NodeScenarioConfigurerRegistry {

	private final Map<ApiNodeType, NodeScenarioConfigurer> configurers;

	public NodeScenarioConfigurerRegistry(List<NodeScenarioConfigurer> configurers) {
		Map<ApiNodeType, NodeScenarioConfigurer> registry = new EnumMap<>(ApiNodeType.class);
		for (NodeScenarioConfigurer configurer : configurers) {
			NodeScenarioConfigurer previous = registry.put(configurer.supportedType(), configurer);
			if (previous != null) {
				throw new IllegalStateException("Duplicate node scenario configurer for " + configurer.supportedType());
			}
		}
		this.configurers = Map.copyOf(registry);
	}

	public void configure(ScenarioBuilder builder, NodeCommand nodeCommand) {
		NodeScenarioConfigurer configurer = configurers.get(nodeCommand.nodeType());
		if (configurer == null) {
			throw new ScenarioValidationException("Unsupported node type " + nodeCommand.nodeType());
		}
		configurer.configure(builder, nodeCommand);
	}
}
