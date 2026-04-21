package com.koushik.systemSimulator.simulation.batch;

import java.util.List;

public record NodeTickResult(
        String nodeId,
        int completedCount,   // requests that exited the system from this node
        int processedCount,   // requests this node finished processing (includes forwarded)
        int droppedCount,
        long latencySum,
        List<ForwardedBatch> forwardedBatches,
        int queueDepth
) {
    public static NodeTickResult idle(String nodeId) {
        return new NodeTickResult(nodeId, 0, 0, 0, 0L, List.of(), 0);
    }
}
