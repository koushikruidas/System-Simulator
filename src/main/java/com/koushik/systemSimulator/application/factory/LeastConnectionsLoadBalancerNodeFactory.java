package com.koushik.systemSimulator.application.factory;

import com.koushik.systemSimulator.simulation.model.NodeType;
import com.koushik.systemSimulator.simulation.node.RoutingLoadBalancerNode;
import com.koushik.systemSimulator.simulation.node.SimNode;
import com.koushik.systemSimulator.simulation.routing.LeastConnectionsRoutingStrategy;
import com.koushik.systemSimulator.simulation.scenario.NodeDefinition;
import org.springframework.stereotype.Component;

@Component
public class LeastConnectionsLoadBalancerNodeFactory implements SimNodeFactory {

	@Override
	public NodeType supportedType() {
		return NodeType.LEAST_CONNECTIONS_LOAD_BALANCER;
	}

	@Override
	public SimNode create(NodeDefinition nodeDefinition) {
		return new RoutingLoadBalancerNode(new LeastConnectionsRoutingStrategy());
	}
}
