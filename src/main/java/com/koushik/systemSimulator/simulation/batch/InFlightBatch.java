package com.koushik.systemSimulator.simulation.batch;

public record InFlightBatch(int size, long arrivalTick, long completionTick) {}
