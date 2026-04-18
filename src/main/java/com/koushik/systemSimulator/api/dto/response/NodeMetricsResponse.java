package com.koushik.systemSimulator.api.dto.response;

public record NodeMetricsResponse(
		long processedRequests,
		long droppedRequests
) {
}
