package com.koushik.systemSimulator.simulation.state;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

public final class NodeRuntimeState {

	private int inFlight;
	private long processedCount;
	private long droppedCount;
	private final Deque<String> queuedRequestIds = new ArrayDeque<>();

	public int inFlight() {
		return inFlight;
	}

	public long processedCount() {
		return processedCount;
	}

	public long droppedCount() {
		return droppedCount;
	}

	public List<String> queuedRequestIds() {
		return List.copyOf(queuedRequestIds);
	}

	public int queueSize() {
		return queuedRequestIds.size();
	}

	void incrementInFlight() {
		inFlight++;
	}

	void decrementInFlight() {
		if (inFlight == 0) {
			throw new IllegalStateException("Cannot decrement inFlight below zero");
		}
		inFlight--;
	}

	void enqueue(String requestId) {
		queuedRequestIds.addLast(requestId);
	}

	void dequeueExpected(String requestId) {
		String actual = queuedRequestIds.pollFirst();
		if (!requestId.equals(actual)) {
			throw new IllegalStateException("Expected dequeued request " + requestId + " but found " + actual);
		}
	}

	void incrementProcessed() {
		processedCount++;
	}

	void incrementDropped() {
		droppedCount++;
	}
}
