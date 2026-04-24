package com.koushik.systemSimulator.simulation.config;

public final class TimeConverter {

    /** 1 tick = 10 ms. Due to this resolution, capacity < 100 RPS is approximated to 1 req/tick. */
    public static final int DEFAULT_TICK_MS = 10;

    private final int tickMs;

    public TimeConverter(int tickMs) {
        this.tickMs = tickMs;
    }

    public static TimeConverter defaultConverter() {
        return new TimeConverter(DEFAULT_TICK_MS);
    }

    public int ticksPerSecond() {
        return 1000 / tickMs;
    }

    public double rpsToRequestsPerTick(double rps) {
        return rps * tickMs / 1000.0;
    }

    public int secondsToTicks(int seconds) {
        return seconds * ticksPerSecond();
    }

    /**
     * Converts RPS capacity to max req/tick.
     * capacity=0 → 0 (zero processing).
     * capacity < 100 RPS → approximated to 1 req/tick (100 RPS effective) due to tick resolution.
     */
    public int rpsToCapacityTicks(int rps) {
        if (rps <= 0) return 0;
        return Math.max(1, (int) Math.round(rps * (double) tickMs / 1000));
    }

    /**
     * Converts ms latency to ticks. Minimum resolution = 1 tick (10 ms).
     * Any latency < 10 ms is approximated to 10 ms.
     */
    public long msToLatencyTicks(long ms) {
        if (ms <= 0) return 0L;
        return Math.max(1L, (long) Math.ceil((double) ms / tickMs));
    }

    public long toMillis(long ticks) {
        return ticks * tickMs;
    }

    public double avgLatencyToMs(double ticks) {
        return ticks * tickMs;
    }

    public double requestsPerTickToRps(double rpt) {
        return rpt * 1000.0 / tickMs;
    }
}
