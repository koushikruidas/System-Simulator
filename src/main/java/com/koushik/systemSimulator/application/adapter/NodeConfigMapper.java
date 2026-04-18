package com.koushik.systemSimulator.application.adapter;

import com.koushik.systemSimulator.application.model.NodeConfig;
import com.koushik.systemSimulator.application.model.NodeType;
import com.koushik.systemSimulator.simulation.scenario.NodeDefinition;

public interface NodeConfigMapper {

	NodeType supportedType();

	NodeDefinition toDomain(NodeConfig config, String downstreamNodeId);
}
