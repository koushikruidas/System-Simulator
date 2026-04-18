package com.koushik.systemSimulator.api.mapper;

import com.koushik.systemSimulator.api.dto.response.NodeMetricsResponse;
import com.koushik.systemSimulator.api.dto.response.SimulationResponse;
import com.koushik.systemSimulator.application.model.SimulationResult;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

@Component
public class SimulationResponseMapper {

	public SimulationResponse toResponse(SimulationResult report) {
		Map<String, NodeMetricsResponse> nodeMetrics = new LinkedHashMap<>();
		report.getNodeMetrics().forEach((nodeId, metrics) ->
				nodeMetrics.put(nodeId, NodeMetricsResponse.builder()
						.processedRequests(metrics.getProcessedRequests())
						.droppedRequests(metrics.getDroppedRequests())
						.build()));
		return SimulationResponse.builder()
				.totalRequests(report.getTotalRequests())
				.successfulRequests(report.getSuccessfulRequests())
				.failedRequests(report.getFailedRequests())
				.averageLatency(report.getAverageLatency())
				.nodeMetrics(Map.copyOf(nodeMetrics))
				.build();
	}
}
