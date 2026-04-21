package com.koushik.systemSimulator.simulation.batch;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;

public final class BatchNodeState {

    public final ArrayDeque<RequestBatch> inputQueue = new ArrayDeque<>();

    // Used by SERVICE, DATABASE, PROCESSING_LOAD_BALANCER.
    public final List<InFlightBatch> inFlight = new ArrayList<>();

    // CACHE-specific: separate lists for hit and miss paths.
    public final List<InFlightBatch> hitInFlight  = new ArrayList<>();
    public final List<InFlightBatch> missInFlight = new ArrayList<>();

    public long processedCount = 0L;
    public long droppedCount   = 0L;

    // Round-robin index for ROUND_ROBIN_LOAD_BALANCER nodes.
    public int rrIndex = 0;

    // Cache hit-rate error accumulation (Bresenham integer method, SCALE = 1_000_000).
    public long hitsOwedScaled = 0L;

    // Pre-tick load snapshot written by orchestrator before the parallel phase;
    // read-only by LEAST_CONNECTIONS LB workers during the parallel phase.
    // volatile ensures visibility across threads without explicit locking.
    public volatile int loadSnapshot = 0;
}
