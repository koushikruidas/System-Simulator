package com.koushik.systemSimulator.api.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TimeSeriesPointResponse {
    private long time;
    private int incoming;
    private int processed;
    private int dropped;
    private Map<String, Integer> queues;
    private double avgLatency;
}
