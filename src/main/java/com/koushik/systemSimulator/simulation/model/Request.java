package com.koushik.systemSimulator.simulation.model;

import java.util.Map;
import java.util.Objects;

public record Request(
		String requestId,
		String requestType,
		long createdAt,
		Map<String, String> attributes
) {

	public Request {
		Objects.requireNonNull(requestId, "requestId must not be null");
		Objects.requireNonNull(requestType, "requestType must not be null");
		attributes = attributes == null ? Map.of() : Map.copyOf(attributes);
	}
}
