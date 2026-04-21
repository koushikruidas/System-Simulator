package com.koushik.systemSimulator.simulation.batch;

import java.util.Map;

public record TickMetrics(
        long tick,
        int completed,
        int dropped,
        Map<String, Integer> queueDepths,
        double avgLatency
) {}
