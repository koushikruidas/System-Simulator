package com.koushik.systemSimulator.application.factory;

import com.koushik.systemSimulator.simulation.model.NodeType;
import com.koushik.systemSimulator.simulation.node.SimNode;
import com.koushik.systemSimulator.simulation.scenario.NodeDefinition;

public interface SimNodeFactory {

	NodeType supportedType();

	SimNode create(NodeDefinition nodeDefinition);
}
