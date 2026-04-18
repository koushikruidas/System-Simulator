package com.koushik.systemSimulator.application.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@AllArgsConstructor
public class NodeMetrics {

	private final long processedRequests;
	private final long droppedRequests;
}
