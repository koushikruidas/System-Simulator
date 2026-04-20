package com.koushik.systemSimulator.application.model;

import lombok.Builder;
import lombok.Getter;

import java.util.Map;

@Getter
@Builder
public class TimeSeriesPoint {
    private final long time;
    private final int incoming;
    private final int processed;
    private final int dropped;
    private final Map<String, Integer> queues;
    private final double avgLatency;
}
