package com.koushik.systemSimulator.simulation.batch;

public record ForwardedBatch(int size, long arrivalTick, String targetNodeId, long deliveryTick) {}
