package com.koushik.systemSimulator.api.dto.response;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class FlowGroupResponse {
    private List<String> path;
    private String outcome;
    private int count;
    private double avgLatency;
    private List<FlowBreakdownResponse> breakdown;
}
