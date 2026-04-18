package com.koushik.systemSimulator.application.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@AllArgsConstructor
public class ConnectionConfig {

	private final String sourceNodeId;
	private final String targetNodeId;
}
