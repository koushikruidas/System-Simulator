package com.koushik.systemSimulator.simulation.node;

import com.koushik.systemSimulator.simulation.metrics.MetricSignal;
import com.koushik.systemSimulator.simulation.metrics.MetricType;
import com.koushik.systemSimulator.simulation.model.Request;
import com.koushik.systemSimulator.simulation.scenario.NodeDefinition;
import com.koushik.systemSimulator.simulation.state.StateMutations;

public final class DatabaseNode extends AbstractProcessingNode {

	@Override
	protected void onProcessingCompleted(Request request, NodeDefinition definition, NodeExecutionContext context, NodeResult.Builder result) {
		long latency = context.now() - request.createdAt();
		result.mutate(StateMutations.markRequestCompleted(definition.nodeId(), request, context.now()))
				.metric(new MetricSignal(MetricType.REQUEST_COMPLETED, definition.nodeId(), request.requestId(), latency));
	}
}
