package com.koushik.systemSimulator.application.model;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class FlowGroup {
    private final List<String> path;
    private final String outcome;
    private final int count;
    private final double avgLatency;
    private final List<FlowBreakdown> breakdown;
}
