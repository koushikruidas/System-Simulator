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
 * Invariant tests for TimeStepSimulationEngine.
 *
 * Covers: queue limit enforcement, early processing, drop timing, multi-entry
 * distribution, conservation law, and determinism.
 */
class EngineInvariantTest {

    // ── Test 1 — Queue Limit Enforcement ─────────────────────────────────────

    /**
     * Verifies that the service's combined queue depth (inputQueue + inFlight batches)
     * never exceeds queueLimit + capacity at any tick, even under heavy overload.
     *
     * Also verifies that drops occur, proving the limit was actually triggered.
     */
    @Test
    void queueLimit_neverExceeded_atAnyTick() {
        int svcCapacity   = 2;
        int svcQueueLimit = 10;

        Topology topology = new Topology(
                List.of(
                        new NodeDefinition("lb",  NodeType.ROUND_ROBIN_LOAD_BALANCER, 0, 0, 0, "svc"),
                        new NodeDefinition("svc", NodeType.SERVICE,  svcCapacity, svcQueueLimit, 5, "db"),
                        new NodeDefinition("db",  NodeType.DATABASE, 100, 100, 1, null)
                ),
                List.of(
                        new LinkDefinition("lb",  "svc"),
                        new LinkDefinition("svc", "db")
                )
        );

        TimeStepReport report = new TimeStepSimulationEngine(topology, "lb", 50, 5).run();

        assertTrue(report.totalDropped() > 0,
                "High arrival rate must trigger drops, proving queue limit enforcement");

        // queueDepths = inputQueue_requests + inFlight_batch_count.
        // inputQueue is bounded by queueLimit; inFlight.size() is bounded by capacity.
        for (TickMetrics tick : report.ticks()) {
            int svcDepth = tick.queueDepths().getOrDefault("svc", 0);
            assertTrue(svcDepth <= svcQueueLimit,
                    String.format("svc depth %d at tick %d exceeds queueLimit+capacity=%d",
                            svcDepth, tick.tick(), svcQueueLimit));
        }
    }

    // ── Test 2 — Early Processing (No Stall) ─────────────────────────────────

    /**
     * Verifies that with latency=1, the first completions appear by tick 2 —
     * the engine starts processing immediately without an artificial stall.
     *
     * Timing: inject at tick 0, LB forwards at tick 1, DB drains at tick 1 (latency=1).
     */
    @Test
    void processingStartsEarly_noStall() {
        Topology topology = new Topology(
                List.of(new NodeDefinition("db", NodeType.DATABASE, 100, 100, 1, null)),
                List.of()
        );

        TimeStepReport report = new TimeStepSimulationEngine(topology, "db", 10, 5).run();

        assertTrue(report.totalCompleted() > 0, "Requests must complete");

        long firstCompletionTick = report.ticks().stream()
                .filter(t -> t.completed() > 0)
                .mapToLong(TickMetrics::tick)
                .min().orElse(Long.MAX_VALUE);

        assertTrue(firstCompletionTick <= 2,
                "With latency=1, completions must start by tick 2, got: " + firstCompletionTick);
    }

    // ── Test 3 — Drop Timing ─────────────────────────────────────────────────

    /**
     * Verifies that drops do NOT start on tick 0.
     *
     * On tick 0: PROCESS runs before INJECT, so the service queue is still empty —
     * no drops can occur. Drops first appear at tick 1 when the LB forwards
     * its queue to the overwhelmed service.
     */
    @Test
    void drops_startAfterQueueFills_notOnTickZero() {
        Topology topology = new Topology(
                List.of(
                        new NodeDefinition("lb",  NodeType.ROUND_ROBIN_LOAD_BALANCER, 0, 0, 0, "svc"),
                        new NodeDefinition("svc", NodeType.SERVICE, 2, 5, 5, null)
                ),
                List.of(new LinkDefinition("lb", "svc"))
        );

        TimeStepReport report = new TimeStepSimulationEngine(topology, "lb", 20, 5).run();

        assertTrue(report.totalDropped() > 0, "High load scenario must produce drops");

        int tick0Dropped = report.ticks().get(0).dropped();
        assertEquals(0, tick0Dropped,
                "No drops on tick 0 — service queue is empty before INJECT");

        long firstDropTick = report.ticks().stream()
                .filter(t -> t.dropped() > 0)
                .mapToLong(TickMetrics::tick)
                .min().orElse(Long.MAX_VALUE);

        assertTrue(firstDropTick >= 1,
                "First drop must occur at tick 1 or later, got: " + firstDropTick);
    }

    // ── Test 4 — Multi-Entry Distribution ────────────────────────────────────

    /**
     * Verifies that when two load balancers are entry nodes, the arrival rate is split
     * evenly between them and neither LB is idle.
     *
     * arrivalRate=10, 2 entry nodes → lb1 gets 5/tick, lb2 gets 5/tick.
     */
    @Test
    void multiEntry_distributesLoadToAllEntryNodes() {
        int arrivalRate = 10;
        int duration    = 3;

        Topology topology = new Topology(
                List.of(
                        new NodeDefinition("lb1", NodeType.ROUND_ROBIN_LOAD_BALANCER, 0, 0, 0, "db"),
                        new NodeDefinition("lb2", NodeType.ROUND_ROBIN_LOAD_BALANCER, 0, 0, 0, "db"),
                        new NodeDefinition("db",  NodeType.DATABASE, 100, 100, 1, null)
                ),
                List.of(
                        new LinkDefinition("lb1", "db"),
                        new LinkDefinition("lb2", "db")
                )
        );

        TimeStepReport report = new TimeStepSimulationEngine(
                topology, List.of("lb1", "lb2"), arrivalRate, duration).run();

        long lb1Processed = report.nodeProcessedCounts().getOrDefault("lb1", 0L);
        long lb2Processed = report.nodeProcessedCounts().getOrDefault("lb2", 0L);

        assertTrue(lb1Processed > 0, "lb1 must receive and process traffic");
        assertTrue(lb2Processed > 0, "lb2 must receive and process traffic");

        assertEquals((long) arrivalRate * duration, lb1Processed + lb2Processed,
                "All injected requests must be processed by the entry LBs");

        assertTrue(Math.abs(lb1Processed - lb2Processed) <= duration,
                "Load should be roughly balanced between entry nodes");
    }

    // ── Test 5 — Conservation Law ────────────────────────────────────────────

    /**
     * Verifies the conservation invariant held in the final report:
     *   totalInjected == totalCompleted + totalDropped + unfinishedRequests
     *
     * Also verifies that no counter is negative.
     */
    @Test
    void conservationLaw_holdsInReport() {
        Topology topology = new Topology(
                List.of(
                        new NodeDefinition("lb",  NodeType.ROUND_ROBIN_LOAD_BALANCER, 0, 0, 0, "svc"),
                        new NodeDefinition("svc", NodeType.SERVICE,  2, 3, 5, "db"),
                        new NodeDefinition("db",  NodeType.DATABASE, 10, 20, 2, null)
                ),
                List.of(
                        new LinkDefinition("lb",  "svc"),
                        new LinkDefinition("svc", "db")
                )
        );

        TimeStepReport report = new TimeStepSimulationEngine(topology, "lb", 30, 5).run();

        assertEquals(
                report.totalInjected(),
                report.totalCompleted() + report.totalDropped() + report.unfinishedRequests(),
                "totalInjected must equal completed + dropped + unfinished");

        assertTrue(report.totalInjected()       >= 0, "totalInjected must not be negative");
        assertTrue(report.totalCompleted()      >= 0, "totalCompleted must not be negative");
        assertTrue(report.totalDropped()        >= 0, "totalDropped must not be negative");
        assertTrue(report.unfinishedRequests()  >= 0, "unfinishedRequests must not be negative");
    }

    // ── Test 6 — Determinism ─────────────────────────────────────────────────

    /**
     * Verifies that running the same simulation twice produces identical results.
     *
     * The engine must be deterministic: no random state, no shared mutable fields,
     * no time-based branching.
     */
    @Test
    void deterministicExecution_sameInputsSameOutputs() {
        Topology topology = new Topology(
                List.of(
                        new NodeDefinition("lb",  NodeType.ROUND_ROBIN_LOAD_BALANCER, 0, 0, 0, "svc"),
                        new NodeDefinition("svc", NodeType.SERVICE,  3, 10, 5, "db"),
                        new NodeDefinition("db",  NodeType.DATABASE, 10, 30, 3, null)
                ),
                List.of(
                        new LinkDefinition("lb",  "svc"),
                        new LinkDefinition("svc", "db")
                )
        );

        TimeStepReport r1 = new TimeStepSimulationEngine(topology, "lb", 20, 5).run();
        TimeStepReport r2 = new TimeStepSimulationEngine(topology, "lb", 20, 5).run();

        assertEquals(r1.totalCompleted(),       r2.totalCompleted(),       "totalCompleted must be deterministic");
        assertEquals(r1.totalDropped(),         r2.totalDropped(),         "totalDropped must be deterministic");
        assertEquals(r1.totalInjected(),        r2.totalInjected(),        "totalInjected must be deterministic");
        assertEquals(r1.unfinishedRequests(),   r2.unfinishedRequests(),   "unfinishedRequests must be deterministic");
        assertEquals(r1.nodeProcessedCounts(),  r2.nodeProcessedCounts(),  "per-node processed counts must be deterministic");
        assertEquals(r1.nodeDroppedCounts(),    r2.nodeDroppedCounts(),    "per-node dropped counts must be deterministic");
        assertEquals(r1.ticks().size(),         r2.ticks().size(),         "tick count must be deterministic");
    }

    @Test
    void conservationLaw_holds_globally() {
        Topology topology = new Topology(
                List.of(
                        new NodeDefinition("db", NodeType.DATABASE, 10, 10, 1, null)
                ),
                List.of()
        );

        int arrivalRate = 5;
        int duration = 5;

        TimeStepReport report = new TimeStepSimulationEngine(topology, "db", arrivalRate, duration).run();

        long totalInjected = (long) arrivalRate * duration;

        long completed = report.totalCompleted();
        long dropped = report.totalDropped();
        long unfinished = report.unfinishedRequests();

        assertEquals(
                totalInjected,
                completed + dropped + unfinished,
                "Global conservation must hold"
        );
    }

    @Test
    void zeroLoad_producesNoActivity() {
        Topology topology = new Topology(
                List.of(new NodeDefinition("db", NodeType.DATABASE, 10, 10, 1, null)),
                List.of()
        );

        TimeStepReport report = new TimeStepSimulationEngine(topology, "db", 0, 5).run();

        assertEquals(0, report.totalCompleted());
        assertEquals(0, report.totalDropped());
    }

    @Test
    void longRun_noDrift() {
        Topology topology = new Topology(
                List.of(new NodeDefinition("db", NodeType.DATABASE, 50, 50, 1, null)),
                List.of()
        );

        TimeStepReport report = new TimeStepSimulationEngine(topology, "db", 20, 50).run();

        assertTrue(report.totalCompleted() > 0);
        assertTrue(report.totalLatencySum() > 0);
    }
}
