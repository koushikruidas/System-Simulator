package com.koushik.systemSimulator.application.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@AllArgsConstructor
public class NodeConfig {

	private final String nodeId;
	private final NodeType nodeType;
	private final Integer capacity;
	private final Integer queueLimit;
	private final Long latency;
	private final LbStrategy strategy;
}
