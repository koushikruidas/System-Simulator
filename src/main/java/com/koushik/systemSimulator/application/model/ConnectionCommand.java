package com.koushik.systemSimulator.application.model;

import java.util.Objects;

public record ConnectionCommand(
		String sourceNodeId,
		String targetNodeId
) {

	public ConnectionCommand {
		Objects.requireNonNull(sourceNodeId, "sourceNodeId must not be null");
		Objects.requireNonNull(targetNodeId, "targetNodeId must not be null");
	}
}
