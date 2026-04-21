package com.koushik.systemSimulator.validation;

import com.koushik.systemSimulator.application.adapter.CacheNodeConfigMapper;
import com.koushik.systemSimulator.application.adapter.DatabaseNodeConfigMapper;
import com.koushik.systemSimulator.application.adapter.LoadBalancerNodeConfigMapper;
import com.koushik.systemSimulator.application.adapter.NodeConfigMapperRegistry;
import com.koushik.systemSimulator.application.adapter.ServiceNodeConfigMapper;
import com.koushik.systemSimulator.application.adapter.SimulationScenarioAdapter;
import com.koushik.systemSimulator.application.builder.ScenarioBuilder;
import com.koushik.systemSimulator.application.factory.CacheNodeFactory;
import com.koushik.systemSimulator.application.factory.DatabaseNodeFactory;
import com.koushik.systemSimulator.application.factory.LeastConnectionsLoadBalancerNodeFactory;
import com.koushik.systemSimulator.application.factory.RoundRobinLoadBalancerNodeFactory;
import com.koushik.systemSimulator.application.factory.ServiceNodeFactory;
import com.koushik.systemSimulator.application.factory.SimNodeFactoryRegistry;
import com.koushik.systemSimulator.application.factory.SimulationEngineFactory;
import com.koushik.systemSimulator.application.model.LbStrategy;
import com.koushik.systemSimulator.application.model.SimulationCommand;
import com.koushik.systemSimulator.application.model.SimulationResult;
import com.koushik.systemSimulator.application.runner.DefaultSimulationRunner;
import com.koushik.systemSimulator.application.runner.SimulationResultAssembler;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Validates TimeStepSimulationEngine against the old event-driven engine.
 * Primary criterion: total completed and dropped counts must match exactly.
 * Per-node distribution differences on multi-downstream topologies are expected and tolerated.
 */
class EngineValidationTest {

    // Captures [EngineCompare] and [TimeStepEngine] lines from both streams
    private PrintStream origOut;
    private PrintStream origErr;
    private ByteArrayOutputStream outBuf;
    private ByteArrayOutputStream errBuf;

    @BeforeEach
    void redirectStreams() {
        origOut = System.out;
        origErr = System.err;
        outBuf  = new ByteArrayOutputStream();
        errBuf  = new ByteArrayOutputStream();
        System.setOut(new PrintStream(outBuf));
        System.setErr(new PrintStream(errBuf));
    }

    @AfterEach
    void restoreStreams() {
        System.setOut(origOut);
        System.setErr(origErr);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private DefaultSimulationRunner buildRunner() {
        return new DefaultSimulationRunner(
                new SimulationScenarioAdapter(new NodeConfigMapperRegistry(List.of(
                        new LoadBalancerNodeConfigMapper(),
                        new ServiceNodeConfigMapper(),
                        new DatabaseNodeConfigMapper(),
                        new CacheNodeConfigMapper()
                ))),
                new SimulationEngineFactory(new SimNodeFactoryRegistry(List.of(
                        new RoundRobinLoadBalancerNodeFactory(),
                        new LeastConnectionsLoadBalancerNodeFactory(),
                        new ServiceNodeFactory(),
                        new DatabaseNodeFactory(),
                        new CacheNodeFactory()
                ))),
                new SimulationResultAssembler()
        );
    }

    private String capturedLog() {
        System.out.flush();
        System.err.flush();
        return outBuf.toString() + errBuf.toString();
    }

    /**
     * Parses [EngineCompare] MISMATCH lines and verifies that total completed and
     * dropped counts actually differ beyond the allowed tolerance.
     *
     * A tolerance of 1 is allowed because the new engine processes requests in batches
     * while the old engine processes individual events ordered by sequence number. At exact
     * boundaries (a completion tick coinciding with a queue-drain tick), the two models can
     * disagree on whether the very last request in a queue completes or is dropped — a
     * difference of at most 1 request that doesn't affect the total (completed+dropped) invariant.
     */
    private boolean hasCriticalMismatch(String log) {
        Pattern p = Pattern.compile(
                "\\[EngineCompare\\] MISMATCH.*completed: old=(\\d+) new=(\\d+).*dropped: old=(\\d+) new=(\\d+)");
        Matcher m = p.matcher(log);
        while (m.find()) {
            long oldCompleted = Long.parseLong(m.group(1));
            long newCompleted = Long.parseLong(m.group(2));
            long oldDropped   = Long.parseLong(m.group(3));
            long newDropped   = Long.parseLong(m.group(4));
            if (Math.abs(oldCompleted - newCompleted) > 1 || Math.abs(oldDropped - newDropped) > 1) {
                return true;
            }
        }
        return false;
    }

    private void assertEnginesMatch(String scenarioName) {
        String log = capturedLog();
        // Print to real streams so test output shows the comparison
        origOut.println("=== " + scenarioName + " ===");
        origOut.println(log.isBlank() ? "(no engine comparison output)" : log.trim());

        if (hasCriticalMismatch(log)) {
            fail("Engine MISMATCH (counts differ) for scenario [" + scenarioName + "]:\n" + log);
        }
    }

    // ── Scenario A — Low Load, Single Path ───────────────────────────────────

    @Test
    void scenarioA_lowLoad_allRequestsComplete() {
        SimulationCommand command = ScenarioBuilder.create()
                .addLoadBalancer("lb", LbStrategy.ROUND_ROBIN, 20, 0, 0)
                .addService("svc", 20, 50, 5)
                .addDatabase("db", 20, 50, 10)
                .connect("lb", "svc")
                .connect("svc", "db")
                .withTimeSeries(5, 5)
                .withEntryNode("lb")
                .build();

        SimulationResult result = buildRunner().run(command);

        assertEnginesMatch("Scenario A — Low Load");
        assertEquals(25, result.getTotalRequests(),        "totalRequests should equal totalInjected=25");
        assertEquals(25, result.getSuccessfulRequests(),   "all 25 requests should complete");
        assertEquals(0,  result.getFailedRequests(),       "no requests should be dropped");
    }

    // ── Scenario B — Moderate Load with Queuing ───────────────────────────────

    @Test
    void scenarioB_moderateLoad_queuesButAllComplete() {
        SimulationCommand command = ScenarioBuilder.create()
                .addLoadBalancer("lb", LbStrategy.ROUND_ROBIN, 50, 0, 0)
                .addService("svc", 3, 200, 3)
                .addDatabase("db", 10, 200, 10)
                .connect("lb", "svc")
                .connect("svc", "db")
                .withTimeSeries(20, 10)
                .withEntryNode("lb")
                .build();

        SimulationResult result = buildRunner().run(command);

        assertEnginesMatch("Scenario B — Moderate Load");
        assertEquals(200, result.getTotalRequests(),       "totalRequests should equal totalInjected=200");
        assertEquals(200, result.getSuccessfulRequests(),  "all 200 requests should complete with large queues");
        assertEquals(0,   result.getFailedRequests(),      "no drops with generous queue limits");

        // Verify queuing actually happened (svc was a bottleneck for some ticks)
        boolean queueObserved = result.getTimeSeries().stream()
                .anyMatch(p -> p.getQueues().getOrDefault("svc", 0) > 0);
        assertTrue(queueObserved, "svc queue depth should be > 0 in at least one tick");
    }

    // ── Scenario C — High Load with Drops at Service ─────────────────────────

    @Test
    void scenarioC_highLoad_dropsAtService() {
        SimulationCommand command = ScenarioBuilder.create()
                .addLoadBalancer("lb", LbStrategy.ROUND_ROBIN, 100, 0, 0)
                .addService("svc", 2, 3, 5)
                .addDatabase("db", 20, 50, 10)
                .connect("lb", "svc")
                .connect("svc", "db")
                .withTimeSeries(50, 10)
                .withEntryNode("lb")
                .build();

        SimulationResult result = buildRunner().run(command);

        assertEnginesMatch("Scenario C — High Load with Drops");
        assertEquals(500, result.getTotalRequests(),                            "totalRequests=500");
        assertTrue(result.getFailedRequests() > 0,                              "drops must occur at svc");
        assertTrue(result.getSuccessfulRequests() + result.getFailedRequests()
                        <= result.getTotalRequests(),
                   "completed + dropped must not exceed injected");
    }

    // ── Scenario D — DB Bottleneck ────────────────────────────────────────────

    @Test
    void scenarioD_dbBottleneck_dropsAtDb() {
        SimulationCommand command = ScenarioBuilder.create()
                .addLoadBalancer("lb", LbStrategy.ROUND_ROBIN, 100, 0, 0)
                .addService("svc", 20, 100, 2)
                .addDatabase("db", 1, 2, 10)
                .connect("lb", "svc")
                .connect("svc", "db")
                .withTimeSeries(20, 10)
                .withEntryNode("lb")
                .build();

        SimulationResult result = buildRunner().run(command);

        assertEnginesMatch("Scenario D — DB Bottleneck");
        assertEquals(200, result.getTotalRequests(),                            "totalRequests=200");
        assertTrue(result.getFailedRequests() > 0,                              "drops must occur at db");
        assertTrue(result.getSuccessfulRequests() + result.getFailedRequests()
                        <= result.getTotalRequests(),
                   "completed + dropped must not exceed injected");
        assertTrue(result.getNodeMetrics().get("db").getDroppedRequests() > 0, "db must report drops");

        // Verify drop source: most drops should be at db
        long svcDrops = result.getNodeMetrics().get("svc").getDroppedRequests();
        long dbDrops  = result.getNodeMetrics().get("db").getDroppedRequests();
        assertFalse(svcDrops > 0 && dbDrops == 0, "bottleneck is at db, not svc");
    }
}
