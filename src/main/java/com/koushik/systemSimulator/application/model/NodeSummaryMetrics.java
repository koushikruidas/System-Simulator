package com.koushik.systemSimulator.application.model;

public record NodeSummaryMetrics(
		long processedRequests,
		long droppedRequests
) {
}
