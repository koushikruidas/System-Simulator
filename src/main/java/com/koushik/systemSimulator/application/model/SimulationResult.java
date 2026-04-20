package com.koushik.systemSimulator.application.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.Singular;

import java.util.List;
import java.util.Map;

@Getter
@Builder
@AllArgsConstructor
public class SimulationResult {

    private final int totalRequests;
    private final int successfulRequests;
    private final int failedRequests;
    private final double averageLatency;

    @Singular("nodeMetric")
    private final Map<String, NodeMetrics> nodeMetrics;

    private final List<FlowGroup> flowGroups;
    private final Map<Long, Long> latencyDistribution;
    private final RequestSamples samples;
}
