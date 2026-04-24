package com.koushik.systemSimulator.simulation.node;

import com.koushik.systemSimulator.simulation.metrics.MetricSignal;
import com.koushik.systemSimulator.simulation.metrics.MetricType;
import com.koushik.systemSimulator.simulation.model.EventType;
import com.koushik.systemSimulator.simulation.model.Request;
import com.koushik.systemSimulator.simulation.model.SimulationEvent;
import com.koushik.systemSimulator.simulation.scenario.NodeDefinition;
import com.koushik.systemSimulator.simulation.state.RequestRuntimeState;
import com.koushik.systemSimulator.simulation.state.StateMutations;

import java.util.Map;

abstract class AbstractProcessingNode implements SimNode {

	private final Map<EventType, NodeEventHandler> handlers = Map.of(
			EventType.REQUEST_ARRIVED, this::handleArrival,
			EventType.PROCESSING_COMPLETED, this::handleCompletion
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
		NodeResult.Builder result = NodeResult.builder();

		if (context.currentNodeState().inFlight() < definition.capacityPerTick()) {
			startProcessing(event.request(), definition, context, result);
			return result.build();
		}

		if (context.currentNodeState().queueSize() < definition.queueLimit()) {
			result.mutate(StateMutations.enqueueRequest(definition.nodeId(), event.request()));
			return result.build();
		}

		result.mutate(StateMutations.markRequestDropped(definition.nodeId(), event.request(), context.now()))
				.metric(new MetricSignal(MetricType.REQUEST_DROPPED, definition.nodeId(), event.request().requestId(), 1));
		return result.build();
	}

	private NodeResult handleCompletion(SimulationEvent event, NodeExecutionContext context) {
		NodeDefinition definition = context.currentNodeDefinition();
		NodeResult.Builder result = NodeResult.builder()
				.mutate(StateMutations.decrementInFlight(definition.nodeId()))
				.mutate(StateMutations.incrementProcessedCount(definition.nodeId()))
				.metric(new MetricSignal(MetricType.NODE_PROCESSED, definition.nodeId(), event.request().requestId(), 1));

		onProcessingCompleted(event.request(), definition, context, result);

		if (!context.currentNodeState().queuedRequestIds().isEmpty()) {
			String nextRequestId = context.currentNodeState().queuedRequestIds().get(0);
			Request queuedRequest = context.requestState(nextRequestId).request();
			result.mutate(StateMutations.dequeueRequest(definition.nodeId(), nextRequestId));
			startProcessing(queuedRequest, definition, context, result);
		}

		return result.build();
	}

	private void startProcessing(Request request, NodeDefinition definition, NodeExecutionContext context, NodeResult.Builder result) {
		result.mutate(StateMutations.incrementInFlight(definition.nodeId()))
				.mutate(StateMutations.markRequestInProgress(definition.nodeId(), request))
				.emit(context.createEvent(
						context.now() + definition.processingLatencyTicks(),
						EventType.PROCESSING_COMPLETED,
						request,
						definition.nodeId(),
						definition.nodeId()
				));
	}

	protected abstract void onProcessingCompleted(
			Request request,
			NodeDefinition definition,
			NodeExecutionContext context,
			NodeResult.Builder result
	);
}
