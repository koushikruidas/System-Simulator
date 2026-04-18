package com.koushik.systemSimulator.simulation.state;

import com.koushik.systemSimulator.simulation.model.Request;

public final class StateMutations {

	private StateMutations() {
	}

	public static StateMutation incrementInFlight(String nodeId) {
		return store -> store.getNodeState(nodeId).incrementInFlight();
	}

	public static StateMutation decrementInFlight(String nodeId) {
		return store -> store.getNodeState(nodeId).decrementInFlight();
	}

	public static StateMutation enqueueRequest(String nodeId, Request request) {
		return store -> {
			store.getOrCreateRequestState(request).moveToNode(nodeId, RequestStatus.QUEUED);
			store.getNodeState(nodeId).enqueue(request.requestId());
		};
	}

	public static StateMutation dequeueRequest(String nodeId, String requestId) {
		return store -> store.getNodeState(nodeId).dequeueExpected(requestId);
	}

	public static StateMutation markRequestInProgress(String nodeId, Request request) {
		return store -> store.getOrCreateRequestState(request).moveToNode(nodeId, RequestStatus.IN_PROGRESS);
	}

	public static StateMutation markRequestCompleted(String nodeId, Request request, long timestamp) {
		return store -> {
			RequestRuntimeState state = store.getOrCreateRequestState(request);
			state.moveToNode(nodeId, RequestStatus.IN_PROGRESS);
			state.markCompleted(timestamp);
		};
	}

	public static StateMutation markRequestDropped(String nodeId, Request request, long timestamp) {
		return store -> {
			RequestRuntimeState state = store.getOrCreateRequestState(request);
			state.moveToNode(nodeId, RequestStatus.DROPPED);
			state.markDropped(timestamp);
			store.getNodeState(nodeId).incrementDropped();
		};
	}

	public static StateMutation incrementProcessedCount(String nodeId) {
		return store -> store.getNodeState(nodeId).incrementProcessed();
	}
}
