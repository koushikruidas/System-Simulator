package com.koushik.systemSimulator.simulation.engine;

import com.koushik.systemSimulator.simulation.batch.TickMetrics;
import com.koushik.systemSimulator.simulation.model.NodeType;
import com.koushik.systemSimulator.simulation.scenario.LinkDefinition;
import com.koushik.systemSimulator.simulation.scenario.NodeDefinition;
import com.koushik.systemSimulator.simulation.scenario.Topology;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Validates cache node behavior in TimeStepSimulationEngine.
 *
 * Covers: hit/miss routing, exact latency values, effectiveness, hit-rate
 * sensitivity, completion guarantees, queue safety, conservation law,
 * determinism, and mixed multi-hop topologies.
 */
class CacheTimeStepEngineTest {

    // ── Test 1 — Cache Hit vs Miss Routing ───────────────────────────────────

    @Test
    void cacheHitRate1_requestsNeverReachDb() {
        Topology topology = lbCacheDbTopology(1.0, 2L, 5L);
        TimeStepReport report = new TimeStepSimulationEngine(topology, "lb", 10, 1).run();

        assertEquals(10, report.totalCompleted());
        assertEquals(0, report.totalDropped());
        assertEquals(0L, report.nodeProcessedCounts().getOrDefault("db", 0L),
                "hitRate=1.0: no requests should reach DB");
    }

    @Test
    void cacheHitRate0_allRequestsForwardedToDb() {
        Topology topology = lbCacheDbTopology(0.0, 2L, 5L);
        TimeStepReport report = new TimeStepSimulationEngine(topology, "lb", 10, 1).run();

        assertEquals(10, report.totalCompleted());
        assertEquals(0, report.totalDropped());
        assertEquals(10L, report.nodeProcessedCounts().getOrDefault("db", 0L),
                "hitRate=0.0: all requests must pass through DB");
    }

    // ── Test 2 — End-to-End Latency Validation ───────────────────────────────

    @Test
    void hitLatency_isExactlyCompletionTickPlusHitLatency() {
        // inject@tick0 → lb@tick1 → cache admitted@tick1 → cache processes@tick2
        // hit latency = completionTick(2) + hitLatency(2) - arrivalTick(0) = 4
        Topology topology = lbCacheDbTopology(1.0, 2L, 5L);
        TimeStepReport report = new TimeStepSimulationEngine(topology, "lb", 10, 1).run();

        assertEquals(4.0, avgLatency(report), 0.001, "Hit avg = completionTick(2) + hitLatency(2) - arrivalTick(0)");
    }

    @Test
    void missLatency_isExactlyCacheCompletionPlusMissLatencyPlusOneDrainTick() {
        // inject@tick0 → lb@tick1 → cache@tick2 (miss, missLatency=5)
        // → deliveryTick=7 → db admits@tick7 → db drains@tick8, latency=8-0=8
        Topology topology = lbCacheDbTopology(0.0, 2L, 5L);
        TimeStepReport report = new TimeStepSimulationEngine(topology, "lb", 10, 1).run();

        assertEquals(8.0, avgLatency(report), 0.001, "Miss avg = cacheCompletionTick(2) + missLatency(5) + dbDrain(1)");
    }

    // ── Test 3 — Cache Effectiveness ─────────────────────────────────────────

    @Test
    void hitPath_hasLowerLatencyThanMissPath() {
        TimeStepReport hitReport  = new TimeStepSimulationEngine(lbCacheDbTopology(1.0, 2L, 5L), "lb", 10, 1).run();
        TimeStepReport missReport = new TimeStepSimulationEngine(lbCacheDbTopology(0.0, 2L, 5L), "lb", 10, 1).run();

        assertTrue(avgLatency(hitReport) < avgLatency(missReport),
                "Hit path must complete faster than miss path through DB");
    }

    // ── Test 4 — Hit Rate Sensitivity (monotonic avg latency decrease) ────────

    @Test
    void avgLatency_decreasesMonotonically_asHitRateIncreases() {
        // Bresenham split for 10 requests: hitRate=0.1→1hit/9miss, 0.5→5/5, 0.9→9/1
        // Hit latency=4, miss latency=8 → avg: 7.6, 6.0, 4.4
        TimeStepReport r01 = new TimeStepSimulationEngine(lbCacheDbTopology(0.1, 2L, 5L), "lb", 10, 1).run();
        TimeStepReport r05 = new TimeStepSimulationEngine(lbCacheDbTopology(0.5, 2L, 5L), "lb", 10, 1).run();
        TimeStepReport r09 = new TimeStepSimulationEngine(lbCacheDbTopology(0.9, 2L, 5L), "lb", 10, 1).run();

        assertEquals(7.6, avgLatency(r01), 0.001, "hitRate=0.1 avg");
        assertEquals(6.0, avgLatency(r05), 0.001, "hitRate=0.5 avg");
        assertEquals(4.4, avgLatency(r09), 0.001, "hitRate=0.9 avg");

        assertTrue(avgLatency(r01) > avgLatency(r05), "avg must decrease as hit rate rises");
        assertTrue(avgLatency(r05) > avgLatency(r09), "avg must decrease as hit rate rises");
    }

    // ── Test 5 — Completion Behavior ─────────────────────────────────────────

    @Test
    void allRequestsComplete_whenCapacityIsGenerous() {
        int rate = 10;
        int duration = 3;
        Topology topology = lbCacheDbTopology(0.5, 2L, 5L);
        TimeStepReport report = new TimeStepSimulationEngine(topology, "lb", rate, duration).run();

        long injected = (long) rate * duration;
        assertEquals(injected, report.totalInjected());
        assertEquals(0, report.totalDropped(), "No drops with generous capacity");
        assertEquals(injected, report.totalCompleted(), "All requests must complete");
        assertEquals(0, report.unfinishedRequests());
    }

    // ── Test 6 — High Load with Cache ─────────────────────────────────────────

    @Test
    void highLoad_triggersDrops_conservationStillHolds() {
        Topology topology = new Topology(
                List.of(
                        new NodeDefinition("lb",    NodeType.ROUND_ROBIN_LOAD_BALANCER, 0, 0, 0L, "cache"),
                        new NodeDefinition("cache", NodeType.CACHE, 2, 5, 5L, "db", 0.5, 2L),
                        new NodeDefinition("db",    NodeType.DATABASE, 100, 100, 1L, null)
                ),
                List.of(
                        new LinkDefinition("lb",    "cache"),
                        new LinkDefinition("cache", "db")
                )
        );

        TimeStepReport report = new TimeStepSimulationEngine(topology, "lb", 50, 5).run();

        assertTrue(report.totalDropped() > 0, "Heavy load must trigger cache queue drops");
        assertEquals(
                report.totalInjected(),
                report.totalCompleted() + report.totalDropped() + report.unfinishedRequests(),
                "Conservation must hold under high load"
        );
    }

    // ── Test 7 — Queue + Cache Interaction ────────────────────────────────────

    @Test
    void cacheQueueDepth_neverExceedsQueueLimit() {
        int cacheQueueLimit = 5;
        Topology topology = new Topology(
                List.of(
                        new NodeDefinition("lb",    NodeType.ROUND_ROBIN_LOAD_BALANCER, 0, 0, 0L, "cache"),
                        new NodeDefinition("cache", NodeType.CACHE, 2, cacheQueueLimit, 5L, "db", 0.5, 2L),
                        new NodeDefinition("db",    NodeType.DATABASE, 100, 100, 1L, null)
                ),
                List.of(
                        new LinkDefinition("lb",    "cache"),
                        new LinkDefinition("cache", "db")
                )
        );

        TimeStepReport report = new TimeStepSimulationEngine(topology, "lb", 30, 5).run();

        for (TickMetrics tick : report.ticks()) {
            int depth = tick.queueDepths().getOrDefault("cache", 0);
            assertTrue(depth <= cacheQueueLimit,
                    String.format("cache depth %d at tick %d exceeds queueLimit=%d",
                            depth, tick.tick(), cacheQueueLimit));
        }
    }

    // ── Test 8 — Conservation Law across hit rates ────────────────────────────

    @Test
    void conservationLaw_holdsForAllHitRates() {
        for (double hitRate : new double[]{0.0, 0.25, 0.5, 0.75, 1.0}) {
            Topology topology = lbCacheDbTopology(hitRate, 2L, 5L);
            TimeStepReport report = new TimeStepSimulationEngine(topology, "lb", 20, 3).run();

            assertEquals(
                    report.totalInjected(),
                    report.totalCompleted() + report.totalDropped() + report.unfinishedRequests(),
                    "Conservation must hold for hitRate=" + hitRate
            );
        }
    }

    // ── Test 9 — Determinism ──────────────────────────────────────────────────

    @Test
    void deterministicExecution_identicalRunsProduceIdenticalResults() {
        Topology topology = lbCacheDbTopology(0.6, 3L, 7L);

        TimeStepReport r1 = new TimeStepSimulationEngine(topology, "lb", 15, 4).run();
        TimeStepReport r2 = new TimeStepSimulationEngine(topology, "lb", 15, 4).run();

        assertEquals(r1.totalCompleted(),      r2.totalCompleted(),      "totalCompleted must be deterministic");
        assertEquals(r1.totalDropped(),        r2.totalDropped(),        "totalDropped must be deterministic");
        assertEquals(r1.totalLatencySum(),     r2.totalLatencySum(),     "totalLatencySum must be deterministic");
        assertEquals(r1.nodeProcessedCounts(), r2.nodeProcessedCounts(), "per-node counts must be deterministic");
    }

    // ── Test 10 — Mixed Topology (LB → Cache → SVC → DB) ─────────────────────

    @Test
    void mixedTopology_hitRateReducesLatency_monotonically() {
        // LB→Cache(missLatency=2)→SVC(latency=2)→DB: hit terminates at cache, miss traverses full chain
        // hitRate=1.0: completionTick(2)+hitLatency(2)-0 = 4
        // hitRate=0.0: cache miss→svc@tick4→db@tick7→drain@tick8, latency=8
        // hitRate=0.5: 5 hits(4) + 5 misses(8) = 6.0
        TimeStepReport r0 = new TimeStepSimulationEngine(mixedTopology(0.0), "lb", 10, 1).run();
        TimeStepReport r5 = new TimeStepSimulationEngine(mixedTopology(0.5), "lb", 10, 1).run();
        TimeStepReport r1 = new TimeStepSimulationEngine(mixedTopology(1.0), "lb", 10, 1).run();

        assertEquals(4.0, avgLatency(r1), 0.001, "hitRate=1.0 avg latency (all cache hits)");
        assertEquals(8.0, avgLatency(r0), 0.001, "hitRate=0.0 avg latency (full chain)");
        assertEquals(6.0, avgLatency(r5), 0.001, "hitRate=0.5 avg latency (mixed)");

        assertTrue(avgLatency(r1) < avgLatency(r5), "Higher hit rate must yield lower avg latency");
        assertTrue(avgLatency(r5) < avgLatency(r0), "Higher hit rate must yield lower avg latency");
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private Topology lbCacheDbTopology(double hitRate, long hitLatency, long missLatency) {
        return new Topology(
                List.of(
                        new NodeDefinition("lb",    NodeType.ROUND_ROBIN_LOAD_BALANCER, 0, 0, 0L, "cache"),
                        new NodeDefinition("cache", NodeType.CACHE, 100, 100, missLatency, "db", hitRate, hitLatency),
                        new NodeDefinition("db",    NodeType.DATABASE, 100, 100, 1L, null)
                ),
                List.of(
                        new LinkDefinition("lb",    "cache"),
                        new LinkDefinition("cache", "db")
                )
        );
    }

    private Topology mixedTopology(double hitRate) {
        // LB → Cache(missLatency=2, hitLatency=2) → SVC(latency=2) → DB
        return new Topology(
                List.of(
                        new NodeDefinition("lb",    NodeType.ROUND_ROBIN_LOAD_BALANCER, 0, 0, 0L, "cache"),
                        new NodeDefinition("cache", NodeType.CACHE, 100, 100, 2L, "svc", hitRate, 2L),
                        new NodeDefinition("svc",   NodeType.SERVICE, 100, 100, 2L, "db"),
                        new NodeDefinition("db",    NodeType.DATABASE, 100, 100, 1L, null)
                ),
                List.of(
                        new LinkDefinition("lb",    "cache"),
                        new LinkDefinition("cache", "svc"),
                        new LinkDefinition("svc",   "db")
                )
        );
    }

    private double avgLatency(TimeStepReport report) {
        if (report.totalCompleted() == 0) return 0.0;
        return (double) report.totalLatencySum() / report.totalCompleted();
    }
}
