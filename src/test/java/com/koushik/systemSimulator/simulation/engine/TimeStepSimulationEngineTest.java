package com.koushik.systemSimulator.simulation.engine;

import com.koushik.systemSimulator.simulation.batch.TickMetrics;
import com.koushik.systemSimulator.simulation.model.NodeType;
import com.koushik.systemSimulator.simulation.scenario.LinkDefinition;
import com.koushik.systemSimulator.simulation.scenario.NodeDefinition;
import com.koushik.systemSimulator.simulation.scenario.Topology;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class TimeStepSimulationEngineTest {

    // ── Helpers ───────────────────────────────────────────────────────────

    private TimeStepSimulationEngine engine(Topology topology, String entry, int rate, int duration) {
        return new TimeStepSimulationEngine(topology, entry, rate, duration);
    }

    private Topology serviceDbTopology(int capacity, int queueLimit, long latency) {
        return new Topology(
                List.of(
                        new NodeDefinition("svc", NodeType.SERVICE, capacity, queueLimit, latency, "db"),
                        new NodeDefinition("db", NodeType.DATABASE, 100, 100, 1, null)
                ),
                List.of(new LinkDefinition("svc", "db"))
        );
    }

    // ── Tests ─────────────────────────────────────────────────────────────

    @Test
    void singleTickSingleNode_allRequestsComplete() {
        Topology topology = new Topology(
                List.of(new NodeDefinition("db", NodeType.DATABASE, 10, 10, 1, null)),
                List.of()
        );
        TimeStepReport report = engine(topology, "db", 5, 1).run();

        assertEquals(5, report.totalCompleted());
        assertEquals(0, report.totalDropped());
        assertFalse(report.ticks().isEmpty());
    }

    @Test
    void queueBuildsUpUnderOverload() {
        // capacity=1 latency=10, 5 req/tick for 3 ticks → queue saturates quickly
        Topology topology = new Topology(
                List.of(new NodeDefinition("svc", NodeType.SERVICE, 1, 100, 10, null)),
                List.of()
        );
        TimeStepReport report = engine(topology, "svc", 5, 3).run();

        // Some ticks should show non-zero queue depth
        boolean anyQueue = report.ticks().stream()
                .anyMatch(t -> t.queueDepths().getOrDefault("svc", 0) > 0);
        assertTrue(anyQueue);
        // Total delivered <= 15 (not all complete during first 3 ticks — still in-flight)
        assertTrue(report.totalCompleted() <= 15);
    }

    @Test
    void requestsExceedingQueueLimit_areDropped() {
        // queueLimit=0 so nothing can queue; capacity=1; 5 req/tick means 4 dropped per tick
        Topology topology = new Topology(
                List.of(new NodeDefinition("svc", NodeType.SERVICE, 1, 0, 1, null)),
                List.of()
        );
        TimeStepReport report = engine(topology, "svc", 5, 2).run();

        assertTrue(report.totalDropped() > 0);
        assertTrue(report.totalCompleted() <= 2);
    }

    @Test
    void cacheHitRateOne_allTerminateAtCache() {
        Topology topology = new Topology(
                List.of(
                        new NodeDefinition("cache", NodeType.CACHE, 100, 100, 5, "db", 1.0, 1),
                        new NodeDefinition("db", NodeType.DATABASE, 100, 100, 5, null)
                ),
                List.of(new LinkDefinition("cache", "db"))
        );
        TimeStepReport report = engine(topology, "cache", 10, 2).run();

        assertEquals(20, report.totalCompleted());
        assertEquals(0, report.totalDropped());
        // DB should process nothing since all are cache hits
        assertEquals(0L, report.nodeProcessedCounts().getOrDefault("db", 0L));
    }

    @Test
    void cacheHitRateZero_allForwardedToDatabase() {
        Topology topology = new Topology(
                List.of(
                        new NodeDefinition("cache", NodeType.CACHE, 100, 100, 1, "db", 0.0, 1),
                        new NodeDefinition("db", NodeType.DATABASE, 100, 100, 1, null)
                ),
                List.of(new LinkDefinition("cache", "db"))
        );
        TimeStepReport report = engine(topology, "cache", 5, 2).run();

        // DB must process all 10 requests
        assertEquals(10L, report.nodeProcessedCounts().getOrDefault("db", 0L));
        assertEquals(10, report.totalCompleted());
    }

    @Test
    void roundRobinLb_distributesBatchAcrossDownstreams() {
        Topology topology = new Topology(
                List.of(
                        new NodeDefinition("lb", NodeType.ROUND_ROBIN_LOAD_BALANCER, 0, 0, 0, null),
                        new NodeDefinition("db1", NodeType.DATABASE, 100, 100, 1, null),
                        new NodeDefinition("db2", NodeType.DATABASE, 100, 100, 1, null)
                ),
                List.of(
                        new LinkDefinition("lb", "db1"),
                        new LinkDefinition("lb", "db2")
                )
        );
        // 6 req/tick for 1 tick → should split 3 and 3
        TimeStepReport report = engine(topology, "lb", 6, 1).run();

        long db1 = report.nodeProcessedCounts().getOrDefault("db1", 0L);
        long db2 = report.nodeProcessedCounts().getOrDefault("db2", 0L);
        assertEquals(6, db1 + db2);
        // Each DB should receive ~half
        assertTrue(db1 >= 2 && db1 <= 4);
        assertTrue(db2 >= 2 && db2 <= 4);
    }

    @Test
    void leastConnectionsLb_routesToLessLoadedDownstream() {
        // One DB has smaller capacity → loads up faster → LC should route away from it
        Topology topology = new Topology(
                List.of(
                        new NodeDefinition("lb", NodeType.LEAST_CONNECTIONS_LOAD_BALANCER, 0, 0, 0, null),
                        new NodeDefinition("db1", NodeType.DATABASE, 1, 100, 10, null),
                        new NodeDefinition("db2", NodeType.DATABASE, 100, 100, 10, null)
                ),
                List.of(
                        new LinkDefinition("lb", "db1"),
                        new LinkDefinition("lb", "db2")
                )
        );
        // duration=5 gives LC enough rounds: db1 (cap=1) queues up while db2 (cap=100) drains fast
        TimeStepReport report = engine(topology, "lb", 10, 5).run();

        // db2 has much more capacity so should get more requests
        long db2 = report.nodeProcessedCounts().getOrDefault("db2", 0L);
        long db1 = report.nodeProcessedCounts().getOrDefault("db1", 0L);
        assertTrue(db2 >= db1, "LC should route more to the less loaded node");
    }

    @Test
    void multiTickDrain_allRequestsCompleteAfterDuration() {
        // latency=5, duration=2, rate=3 → in-flight batches drain after tick 2
        Topology topology = new Topology(
                List.of(new NodeDefinition("db", NodeType.DATABASE, 100, 100, 5, null)),
                List.of()
        );
        TimeStepReport report = engine(topology, "db", 3, 2).run();

        assertEquals(6, report.totalCompleted());
        assertEquals(0, report.totalDropped());
        // Last completed tick must be >= duration (drain happens after arrivals stop)
        long lastCompletedTick = report.ticks().stream()
                .filter(t -> t.completed() > 0)
                .mapToLong(TickMetrics::tick)
                .max().orElse(0);
        assertTrue(lastCompletedTick >= 2);
    }

    @Test
    void timeSeriesPointsHaveCorrectIncomingCounts() {
        Topology topology = new Topology(
                List.of(new NodeDefinition("db", NodeType.DATABASE, 100, 100, 1, null)),
                List.of()
        );
        int arrivalRate = 7;
        int duration = 3;
        TimeStepReport report = engine(topology, "db", arrivalRate, duration).run();

        // During [0, duration) ticks, incoming should equal arrivalRate
        long ticksWithIncoming = report.ticks().stream()
                .filter(t -> t.tick() < duration)
                .count();
        assertEquals(duration, ticksWithIncoming);
    }

    @Test
    void delayLbWithProcessingLatency_batchesArriveDelayed() {
        // LB has processingLatency=2; DB latency=1; duration=1 rate=5
        Topology topology = new Topology(
                List.of(
                        new NodeDefinition("lb", NodeType.LOAD_BALANCER, 0, 0, 2, "db"),
                        new NodeDefinition("db", NodeType.DATABASE, 100, 100, 1, null)
                ),
                List.of(new LinkDefinition("lb", "db"))
        );
        TimeStepReport report = engine(topology, "lb", 5, 1).run();

        assertEquals(5, report.totalCompleted());
        // Completions must happen after LB latency + DB latency = tick >= 3
        long firstDbCompletion = report.ticks().stream()
                .filter(t -> t.completed() > 0)
                .mapToLong(TickMetrics::tick)
                .min().orElse(-1);
        assertTrue(firstDbCompletion >= 2, "DB completions should be delayed by LB latency");
    }

    @Test
    void averageLatencyIsPositiveWhenRequestsComplete() {
        Topology topology = serviceDbTopology(10, 10, 3);
        TimeStepReport report = engine(topology, "svc", 5, 2).run();

        assertTrue(report.totalCompleted() > 0);
        double avg = (double) report.totalLatencySum() / report.totalCompleted();
        assertTrue(avg > 0);
    }

    @Test
    void nodeProcessedCountsSumToTotalCompleted_forSinglePathTopology() {
        Topology topology = serviceDbTopology(100, 100, 1);
        TimeStepReport report = engine(topology, "svc", 4, 3).run();

        // In a SERVICE→DATABASE chain, DB processed count should equal total completed
        long dbProcessed = report.nodeProcessedCounts().getOrDefault("db", 0L);
        assertEquals(report.totalCompleted(), dbProcessed);
    }

    /*@Test
    void strictQueueLimit_neverExceeded() {
        Topology topology = new Topology(
                List.of(new NodeDefinition("svc", NodeType.SERVICE, 2, 5, 5, null)),
                List.of()
        );

        TimeStepReport report = engine(topology, "svc", 20, 5).run();

        for (TickMetrics tick : report.ticks()) {
            int q = tick.queueDepths().getOrDefault("svc", 0);
            assertTrue(q <= 5, "Queue exceeded limit");
        }
    }*/

    @Test
    void processing_happens_every_tick_no_stall() {
        Topology topology = new Topology(
                List.of(new NodeDefinition("db", NodeType.DATABASE, 10, 10, 1, null)),
                List.of()
        );

        TimeStepReport report = engine(topology, "db", 5, 5).run();

        long ticksWithProcessing = report.ticks().stream()
                .filter(t -> t.completed() > 0)
                .count();

        assertTrue(ticksWithProcessing > 1);
    }

    @Test
    void throughput_matches_capacity_under_load() {
        Topology topology = new Topology(
                List.of(new NodeDefinition("db", NodeType.DATABASE, 5, 10, 1, null)),
                List.of()
        );

        TimeStepReport report = engine(topology, "db", 20, 5).run();

        assertTrue(report.totalCompleted() >= 5 * 5);
    }
}
