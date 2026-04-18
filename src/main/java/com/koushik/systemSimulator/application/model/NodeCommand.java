package com.koushik.systemSimulator.application.model;

import com.koushik.systemSimulator.api.dto.request.ApiNodeType;

import java.util.Objects;

public record NodeCommand(
		String nodeId,
		ApiNodeType nodeType,
		Integer capacity,
		Integer queueLimit,
		Long latency
) {

	public NodeCommand {
		Objects.requireNonNull(nodeId, "nodeId must not be null");
		Objects.requireNonNull(nodeType, "nodeType must not be null");
	}
}
