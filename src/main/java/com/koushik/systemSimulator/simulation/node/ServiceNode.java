package com.koushik.systemSimulator.simulation.node;

import com.koushik.systemSimulator.simulation.model.EventType;
import com.koushik.systemSimulator.simulation.model.Request;
import com.koushik.systemSimulator.simulation.scenario.NodeDefinition;

public final class ServiceNode extends AbstractProcessingNode {

	@Override
	protected void onProcessingCompleted(Request request, NodeDefinition definition, NodeExecutionContext context, NodeResult.Builder result) {
		if (definition.downstreamNodeId() == null) {
			throw new IllegalStateException("Service node " + definition.nodeId() + " must define a downstream node");
		}
		result.emit(context.createEvent(
				context.now(),
				EventType.REQUEST_ARRIVED,
				request,
				definition.nodeId(),
				definition.downstreamNodeId()
		));
	}
}
