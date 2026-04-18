package com.koushik.systemSimulator.api.mapper;

import com.koushik.systemSimulator.api.dto.response.NodeMetricsResponse;
import com.koushik.systemSimulator.api.dto.response.SimulationResponse;
import com.koushik.systemSimulator.application.model.SimulationSummaryReport;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

@Component
public class SimulationResponseMapper {

	public SimulationResponse toResponse(SimulationSummaryReport report) {
		Map<String, NodeMetricsResponse> nodeMetrics = new LinkedHashMap<>();
		report.nodeMetrics().forEach((nodeId, metrics) ->
				nodeMetrics.put(nodeId, new NodeMetricsResponse(metrics.processedRequests(), metrics.droppedRequests())));
		return new SimulationResponse(
				report.totalRequests(),
				report.successfulRequests(),
				report.failedRequests(),
				report.averageLatency(),
				Map.copyOf(nodeMetrics)
		);
	}
}
