package com.koushik.systemSimulator.simulation.node;

import com.koushik.systemSimulator.simulation.metrics.MetricSignal;
import com.koushik.systemSimulator.simulation.metrics.MetricType;
import com.koushik.systemSimulator.simulation.model.EventType;
import com.koushik.systemSimulator.simulation.model.SimulationEvent;
import com.koushik.systemSimulator.simulation.scenario.NodeDefinition;
import com.koushik.systemSimulator.simulation.state.StateMutations;

import java.util.Map;

public final class LoadBalancerNode implements SimNode {

	private final Map<EventType, NodeEventHandler> handlers = Map.of(
			EventType.REQUEST_ARRIVED, this::handleArrival
	);

	@Override
	public NodeResult handle(SimulationEvent event, NodeExecutionContext context) {
		NodeEventHandler handler = handlers.get(event.eventType());
		if (handler == null) {
			return NodeResult.empty();
		}
		return handler.handle(event, context);
	}

	private NodeResult handleArrival(SimulationEvent event, NodeExecutionContext context) {
		NodeDefinition definition = context.currentNodeDefinition();
		String downstreamNodeId = definition.downstreamNodeId();
		if (downstreamNodeId == null) {
			throw new IllegalStateException("Load balancer node " + definition.nodeId() + " must define a downstream node");
		}

		SimulationEvent nextEvent = context.createEvent(
				context.now() + definition.processingLatency(),
				EventType.REQUEST_ARRIVED,
				event.request(),
				definition.nodeId(),
				downstreamNodeId
		);

		return NodeResult.builder()
				.mutate(StateMutations.incrementProcessedCount(definition.nodeId()))
				.metric(new MetricSignal(MetricType.NODE_PROCESSED, definition.nodeId(), event.request().requestId(), 1))
				.emit(nextEvent)
				.build();
	}
}
