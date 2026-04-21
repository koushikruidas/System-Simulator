package com.koushik.systemSimulator.simulation.engine;

import com.koushik.systemSimulator.simulation.batch.TickMetrics;

import java.util.List;
import java.util.Map;

public record TimeStepReport(
        long totalCompleted,
        long totalDropped,
        long totalLatencySum,
        long totalInjected,
        long unfinishedRequests,
        Map<String, Long> nodeProcessedCounts,
        Map<String, Long> nodeDroppedCounts,
        List<TickMetrics> ticks
) {}
