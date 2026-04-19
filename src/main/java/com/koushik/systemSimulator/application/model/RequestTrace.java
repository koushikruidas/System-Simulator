package com.koushik.systemSimulator.application.model;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class RequestTrace {
    private final String requestId;
    private final List<String> path;
    private final RequestOutcome outcome;
    private final long totalLatency;
    private final List<NodeLatencyBreakdown> breakdown;
}
