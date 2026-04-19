package com.koushik.systemSimulator.api.dto.response;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class RequestTraceResponse {
    private String requestId;
    private List<String> path;
    private String status;
    private long totalLatency;
    private List<NodeLatencyBreakdownResponse> breakdown;
}
