package com.koushik.systemSimulator.application.adapter;

import com.koushik.systemSimulator.application.model.NodeConfig;
import com.koushik.systemSimulator.application.model.NodeType;
import com.koushik.systemSimulator.simulation.scenario.NodeDefinition;

import java.util.List;

public interface NodeConfigMapper {

	NodeType supportedType();

	NodeDefinition toDomain(NodeConfig config, List<String> downstreamNodeIds, boolean realWorldMode);
}
