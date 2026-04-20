package com.koushik.systemSimulator.api.mapper;

import com.koushik.systemSimulator.api.dto.response.FlowBreakdownResponse;
import com.koushik.systemSimulator.api.dto.response.FlowGroupResponse;
import com.koushik.systemSimulator.api.dto.response.NodeLatencyBreakdownResponse;
import com.koushik.systemSimulator.api.dto.response.NodeMetricsResponse;
import com.koushik.systemSimulator.api.dto.response.RequestSamplesResponse;
import com.koushik.systemSimulator.api.dto.response.RequestTraceResponse;
import com.koushik.systemSimulator.api.dto.response.SimulationResponse;
import com.koushik.systemSimulator.application.model.RequestTrace;
import com.koushik.systemSimulator.application.model.SimulationResult;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class SimulationResponseMapper {

    public SimulationResponse toResponse(SimulationResult result) {
        Map<String, NodeMetricsResponse> nodeMetrics = new LinkedHashMap<>();
        result.getNodeMetrics().forEach((nodeId, metrics) ->
                nodeMetrics.put(nodeId, NodeMetricsResponse.builder()
                        .processedRequests(metrics.getProcessedRequests())
                        .droppedRequests(metrics.getDroppedRequests())
                        .build()));

        List<FlowGroupResponse> flowSummary = result.getFlowGroups().stream()
                .map(flow -> FlowGroupResponse.builder()
                        .path(flow.getPath())
                        .outcome(flow.getOutcome())
                        .count(flow.getCount())
                        .avgLatency(flow.getAvgLatency())
                        .breakdown(flow.getBreakdown().stream()
                                .map(b -> FlowBreakdownResponse.builder()
                                        .nodeId(b.getNodeId())
                                        .avgQueueTime(b.getAvgQueueTime())
                                        .avgProcessingTime(b.getAvgProcessingTime())
                                        .build())
                                .toList())
                        .build())
                .toList();

        RequestSamplesResponse samples = RequestSamplesResponse.builder()
                .first(toTraceResponses(result.getSamples().getFirst()))
                .slowest(toTraceResponses(result.getSamples().getSlowest()))
                .build();

        return SimulationResponse.builder()
                .totalRequests(result.getTotalRequests())
                .successfulRequests(result.getSuccessfulRequests())
                .failedRequests(result.getFailedRequests())
                .averageLatency(result.getAverageLatency())
                .nodeMetrics(Map.copyOf(nodeMetrics))
                .flowSummary(flowSummary)
                .latencyDistribution(result.getLatencyDistribution())
                .samples(samples)
                .build();
    }

    private List<RequestTraceResponse> toTraceResponses(List<RequestTrace> traces) {
        return traces.stream()
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
    }
}
