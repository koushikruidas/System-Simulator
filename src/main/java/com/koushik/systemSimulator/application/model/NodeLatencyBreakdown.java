package com.koushik.systemSimulator.application.model;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class NodeLatencyBreakdown {
    private final String nodeId;
    private final long queueTime;
    private final long processingTime;
}
