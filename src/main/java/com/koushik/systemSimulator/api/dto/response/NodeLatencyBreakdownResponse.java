package com.koushik.systemSimulator.api.dto.response;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class NodeLatencyBreakdownResponse {
    private String nodeId;
    private long queueTime;
    private long processingTime;
}
