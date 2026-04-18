package com.koushik.systemSimulator.simulation.state;

import com.koushik.systemSimulator.simulation.model.Request;

import java.util.ArrayList;
import java.util.List;

public final class RequestRuntimeState {

	private final Request request;
	private RequestStatus status;
	private String currentNodeId;
	private Long completedAt;
	private final List<String> hopHistory = new ArrayList<>();

	public RequestRuntimeState(Request request) {
		this.request = request;
		this.status = RequestStatus.CREATED;
	}

	public Request request() {
		return request;
	}

	public RequestStatus status() {
		return status;
	}

	public String currentNodeId() {
		return currentNodeId;
	}

	public Long completedAt() {
		return completedAt;
	}

	public List<String> hopHistory() {
		return List.copyOf(hopHistory);
	}

	void moveToNode(String nodeId, RequestStatus nextStatus) {
		this.currentNodeId = nodeId;
		this.status = nextStatus;
		this.hopHistory.add(nodeId);
	}

	void markCompleted(long timestamp) {
		this.status = RequestStatus.COMPLETED;
		this.completedAt = timestamp;
	}

	void markDropped(long timestamp) {
		this.status = RequestStatus.DROPPED;
		this.completedAt = timestamp;
	}
}
