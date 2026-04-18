package com.koushik.systemSimulator.simulation.model;

import java.util.Objects;

public record SimulationEvent(
		String eventId,
		long timestamp,
		long sequenceNumber,
		EventType eventType,
		Request request,
		String sourceNodeId,
		String targetNodeId
) {

	public SimulationEvent {
		Objects.requireNonNull(eventId, "eventId must not be null");
		Objects.requireNonNull(eventType, "eventType must not be null");
		Objects.requireNonNull(request, "request must not be null");
		Objects.requireNonNull(targetNodeId, "targetNodeId must not be null");
	}
}
