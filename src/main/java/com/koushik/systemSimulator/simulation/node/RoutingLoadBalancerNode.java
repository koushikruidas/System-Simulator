package com.koushik.systemSimulator.simulation.node;

import com.koushik.systemSimulator.simulation.model.EventType;
import com.koushik.systemSimulator.simulation.model.Request;
import com.koushik.systemSimulator.simulation.routing.RoutingStrategy;
import com.koushik.systemSimulator.simulation.scenario.NodeDefinition;

import java.util.List;

public final class RoutingLoadBalancerNode extends AbstractProcessingNode {

	private final RoutingStrategy routingStrategy;

	public RoutingLoadBalancerNode(RoutingStrategy routingStrategy) {
		this.routingStrategy = routingStrategy;
	}

	@Override
	protected void onProcessingCompleted(
			Request request,
			NodeDefinition definition,
			NodeExecutionContext context,
			NodeResult.Builder result
	) {
		List<String> candidates = context.topology().getDownstreams(definition.nodeId());
		if (candidates.isEmpty()) {
			throw new IllegalStateException(
					"RoutingLoadBalancerNode " + definition.nodeId() + " has no downstream connections"
			);
		}
		String targetNodeId = routingStrategy.select(candidates, request, context);
		result.emit(context.createEvent(
				context.now(),
				EventType.REQUEST_ARRIVED,
				request,
				definition.nodeId(),
				targetNodeId
		));
	}
}
