package com.koushik.systemSimulator.api.mapper;

import com.koushik.systemSimulator.api.dto.response.NodeLatencyBreakdownResponse;
import com.koushik.systemSimulator.api.dto.response.NodeMetricsResponse;
import com.koushik.systemSimulator.api.dto.response.RequestTraceResponse;
import com.koushik.systemSimulator.api.dto.response.SimulationResponse;
import com.koushik.systemSimulator.application.model.SimulationResult;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
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

		List<RequestTraceResponse> requests = report.getRequestTraces().stream()
				.map(trace -> RequestTraceResponse.builder()
						.requestId(trace.getRequestId())
						.path(trace.getPath())
						.status(trace.getOutcome().name())
						.totalLatency(trace.getTotalLatency())
						.breakdown(trace.getBreakdown().stream()
								.map(b -> NodeLatencyBreakdownResponse.builder()
										.nodeId(b.getNodeId())
										.queueTime(b.getQueueTime())
										.processingTime(b.getProcessingTime())
										.build())
								.toList())
						.build())
				.toList();

		return SimulationResponse.builder()
				.totalRequests(report.getTotalRequests())
				.successfulRequests(report.getSuccessfulRequests())
				.failedRequests(report.getFailedRequests())
				.averageLatency(report.getAverageLatency())
				.nodeMetrics(Map.copyOf(nodeMetrics))
				.requests(requests)
				.build();
	}
}
