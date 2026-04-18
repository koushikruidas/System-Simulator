package com.koushik.systemSimulator.application.factory;

import com.koushik.systemSimulator.simulation.model.NodeType;
import com.koushik.systemSimulator.simulation.node.LoadBalancerNode;
import com.koushik.systemSimulator.simulation.node.SimNode;
import com.koushik.systemSimulator.simulation.scenario.NodeDefinition;
import org.springframework.stereotype.Component;

@Component
public class LoadBalancerNodeFactory implements SimNodeFactory {

	@Override
	public NodeType supportedType() {
		return NodeType.DELAY_LOAD_BALANCER;
	}

	@Override
	public SimNode create(NodeDefinition nodeDefinition) {
		return new LoadBalancerNode();
	}
}
