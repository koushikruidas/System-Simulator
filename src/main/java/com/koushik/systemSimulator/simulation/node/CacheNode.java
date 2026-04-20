package com.koushik.systemSimulator.simulation.node;

import com.koushik.systemSimulator.simulation.metrics.MetricSignal;
import com.koushik.systemSimulator.simulation.metrics.MetricType;
import com.koushik.systemSimulator.simulation.model.EventType;
import com.koushik.systemSimulator.simulation.model.Request;
import com.koushik.systemSimulator.simulation.model.SimulationEvent;
import com.koushik.systemSimulator.simulation.scenario.NodeDefinition;
import com.koushik.systemSimulator.simulation.state.StateMutations;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class CacheNode implements SimNode {

	private final double hitRate;
	private final long hitLatency;

	// Integer-based error-accumulation avoids floating-point drift.
	// Scaled by 1_000_000 so hitRate=0.7 → hitRateScaled=700_000, threshold=1_000_000.
	private static final long SCALE = 1_000_000L;
	private final long hitRateScaled;
	private long hitsOwedScaled = 0L;
	// Decision is computed once in startProcessing and read in handleCompletion.
	private final Map<String, Boolean> pendingDecisions = new HashMap<>();

	public CacheNode(double hitRate, long hitLatency) {
		this.hitRate = hitRate;
		this.hitLatency = hitLatency;
		this.hitRateScaled = Math.round(hitRate * SCALE);
	}

	@Override
	public NodeResult handle(SimulationEvent event, NodeExecutionContext context) {
		return switch (event.eventType()) {
			case REQUEST_ARRIVED -> handleArrival(event, context);
			case PROCESSING_COMPLETED -> handleCompletion(event, context);
			default -> NodeResult.empty();
		};
	}

	private NodeResult handleArrival(SimulationEvent event, NodeExecutionContext context) {
		NodeDefinition definition = context.currentNodeDefinition();
		NodeResult.Builder result = NodeResult.builder();

		if (context.currentNodeState().inFlight() < definition.capacity()) {
			startProcessing(event.request(), definition, context, result);
		} else if (context.currentNodeState().queueSize() < definition.queueLimit()) {
			result.mutate(StateMutations.enqueueRequest(definition.nodeId(), event.request()));
		} else {
			result.mutate(StateMutations.markRequestDropped(definition.nodeId(), event.request(), context.now()))
					.metric(new MetricSignal(MetricType.REQUEST_DROPPED, definition.nodeId(), event.request().requestId(), 1));
		}

		return result.build();
	}

	private NodeResult handleCompletion(SimulationEvent event, NodeExecutionContext context) {
		NodeDefinition definition = context.currentNodeDefinition();
		NodeResult.Builder result = NodeResult.builder()
				.mutate(StateMutations.decrementInFlight(definition.nodeId()))
				.mutate(StateMutations.incrementProcessedCount(definition.nodeId()))
				.metric(new MetricSignal(MetricType.NODE_PROCESSED, definition.nodeId(), event.request().requestId(), 1));

		boolean hit = pendingDecisions.remove(event.request().requestId());
		if (hit) {
			long latency = context.now() - event.request().createdAt();
			result.mutate(StateMutations.markRequestCompleted(definition.nodeId(), event.request(), context.now()))
					.metric(new MetricSignal(MetricType.REQUEST_COMPLETED, definition.nodeId(), event.request().requestId(), latency));
		} else {
			List<String> downstreams = context.topology().getDownstreams(definition.nodeId());
			if (downstreams.isEmpty()) {
				result.mutate(StateMutations.markRequestDropped(definition.nodeId(), event.request(), context.now()))
						.metric(new MetricSignal(MetricType.REQUEST_DROPPED, definition.nodeId(), event.request().requestId(), 1));
			} else {
				result.emit(context.createEvent(
						context.now(),
						EventType.REQUEST_ARRIVED,
						event.request(),
						definition.nodeId(),
						downstreams.get(0)
				));
			}
		}

		if (!context.currentNodeState().queuedRequestIds().isEmpty()) {
			String nextRequestId = context.currentNodeState().queuedRequestIds().get(0);
			Request queuedRequest = context.requestState(nextRequestId).request();
			result.mutate(StateMutations.dequeueRequest(definition.nodeId(), nextRequestId));
			startProcessing(queuedRequest, definition, context, result);
		}

		return result.build();
	}

	private void startProcessing(Request request, NodeDefinition definition, NodeExecutionContext context, NodeResult.Builder result) {
		hitsOwedScaled += hitRateScaled;
		boolean hit = hitsOwedScaled >= SCALE;
		if (hit) hitsOwedScaled -= SCALE;
		pendingDecisions.put(request.requestId(), hit);

		long latency = hit ? hitLatency : definition.processingLatency();
		result.mutate(StateMutations.incrementInFlight(definition.nodeId()))
				.mutate(StateMutations.markRequestInProgress(definition.nodeId(), request))
				.emit(context.createEvent(
						context.now() + latency,
						EventType.PROCESSING_COMPLETED,
						request,
						definition.nodeId(),
						definition.nodeId()
				));
	}
}
