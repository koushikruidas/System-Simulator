package com.koushik.systemSimulator.application.factory;

import com.koushik.systemSimulator.simulation.model.NodeType;
import com.koushik.systemSimulator.simulation.node.DatabaseNode;
import com.koushik.systemSimulator.simulation.node.SimNode;
import com.koushik.systemSimulator.simulation.scenario.NodeDefinition;
import org.springframework.stereotype.Component;

@Component
public class DatabaseNodeFactory implements SimNodeFactory {

	@Override
	public NodeType supportedType() {
		return NodeType.DATABASE;
	}

	@Override
	public SimNode create(NodeDefinition nodeDefinition) {
		return new DatabaseNode();
	}
}
