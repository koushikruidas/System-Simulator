package com.koushik.systemSimulator.application.model;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class FlowBreakdown {
    private final String nodeId;
    private final double avgQueueTime;
    private final double avgProcessingTime;
}
