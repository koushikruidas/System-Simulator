package com.koushik.systemSimulator.api.dto.response;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class FlowBreakdownResponse {
    private String nodeId;
    private double avgQueueTime;
    private double avgProcessingTime;
}
