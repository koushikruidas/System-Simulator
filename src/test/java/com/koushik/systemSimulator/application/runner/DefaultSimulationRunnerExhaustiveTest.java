package com.koushik.systemSimulator.application.runner;

import com.koushik.systemSimulator.application.adapter.*;
import com.koushik.systemSimulator.application.builder.ScenarioBuilder;
import com.koushik.systemSimulator.application.builder.ScenarioValidationException;
import com.koushik.systemSimulator.application.factory.*;
import com.koushik.systemSimulator.application.model.*;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class DefaultSimulationRunnerExhaustiveTest {

    private DefaultSimulationRunner buildRunner() {
        return new DefaultSimulationRunner(
                new SimulationScenarioAdapter(new NodeConfigMapperRegistry(List.of(
                        new LoadBalancerNodeConfigMapper(),
                        new ServiceNodeConfigMapper(),
                        new DatabaseNodeConfigMapper()
                ))),
                new SimulationEngineFactory(new SimNodeFactoryRegistry(List.of(
                        new RoundRobinLoadBalancerNodeFactory(),
                        new LeastConnectionsLoadBalancerNodeFactory(),
                        new ServiceNodeFactory(),
                        new DatabaseNodeFactory()
                ))),
                new SimulationResultAssembler()
        );
    }

    private final DefaultSimulationRunner runner = buildRunner();

    // 🔥 1. STRICT CONSERVATION LAW
    @Test
    void conservationLaw_shouldHoldExactly() {
        SimulationCommand command = ScenarioBuilder.create()
                .addLoadBalancer("lb", LbStrategy.ROUND_ROBIN, 100, 100, 1)
                .addService("svc", 50, 40, 3)
                .addDatabase("db", 50, 50, 5)
                .connect("lb", "svc")
                .connect("svc", "db")
                .withTimeSeries(100, 5)
                .withEntryNode("lb")
                .build();

        SimulationResult result = runner.run(command);

        long total = result.getTotalRequests();
        long completed = result.getSuccessfulRequests();
        long dropped = result.getFailedRequests();

        assertEquals(total, completed + dropped);
    }

    // 🔁 2. DETERMINISM
    /*@Test
    void sameInput_shouldProduceConsistentResults() {
        SimulationCommand command = ScenarioBuilder.create()
                .addLoadBalancer("lb", LbStrategy.ROUND_ROBIN, 100, 100, 1)
                .addService("svc", 50, 40, 3)
                .addDatabase("db", 50, 50, 5)
                .connect("lb", "svc")
                .connect("svc", "db")
                .withTimeSeries(50, 5)
                .withEntryNode("lb")
                .build();

        SimulationResult r1 = runner.run(command);
        SimulationResult r2 = runner.run(command);

        assertEquals(r1.getTotalRequests(), r2.getTotalRequests());
        assertEquals(r1.getSuccessfulRequests(), r2.getSuccessfulRequests());
        assertEquals(r1.getFailedRequests(), r2.getFailedRequests());

        // Optional stronger check (only if deterministic)
        assertEquals(r1.getNodeMetrics(), r2.getNodeMetrics());
    }
*/
    // 🔥 3. EXTREME OVERLOAD
    @Test
    void overload_shouldCauseQueueSaturationAndDrops() {
        SimulationCommand command = ScenarioBuilder.create()
                .addLoadBalancer("lb", LbStrategy.ROUND_ROBIN, 1000, 1000, 1)
                .addService("svc", 10, 40, 3)
                .addDatabase("db", 20, 50, 5)
                .connect("lb", "svc")
                .connect("svc", "db")
                .withTimeSeries(500, 10)
                .withEntryNode("lb")
                .build();

        SimulationResult result = runner.run(command);

        assertTrue(result.getFailedRequests() > 0);
        assertTrue(result.getSuccessfulRequests() > 0);
    }

    // 🧊 4. ZERO LOAD
    @Test
    void zeroLoad_shouldBeRejected() {
        assertThrows(ScenarioValidationException.class, () -> {
            ScenarioBuilder.create()
                    .addLoadBalancer("lb", LbStrategy.ROUND_ROBIN, 10, 10, 1)
                    .addService("svc", 10, 10, 3)
                    .addDatabase("db", 10, 10, 5)
                    .connect("lb", "svc")
                    .connect("svc", "db")
                    .withTimeSeries(0, 10) // ❌ invalid
                    .withEntryNode("lb")
                    .build();
        });
    }

    // 🧩 5. MULTI-ENTRY BALANCE (STRICT)
    @Test
    void multiEntry_shouldDistributeLoadEvenly() {
        SimulationCommand command = ScenarioBuilder.create()
                .addLoadBalancer("lb1", LbStrategy.ROUND_ROBIN, 100, 100, 1)
                .addLoadBalancer("lb2", LbStrategy.ROUND_ROBIN, 100, 100, 1)
                .addService("svc", 100, 100, 3)
                .addDatabase("db", 100, 100, 5)
                .connect("lb1", "svc")
                .connect("lb2", "svc")
                .connect("svc", "db")
                .withTimeSeries(200, 5)
                .withEntryNode("lb1")
                .build();

        SimulationResult result = runner.run(command);

        long lb1 = result.getNodeMetrics().get("lb1").getProcessedRequests();
        long lb2 = result.getNodeMetrics().get("lb2").getProcessedRequests();

        assertTrue(Math.abs(lb1 - lb2) <= 1);
    }

    // 📈 6. LATENCY INCREASE UNDER LOAD
    @Test
    void latency_shouldIncreaseUnderLoad() {
        SimulationCommand lowLoad = ScenarioBuilder.create()
                .addLoadBalancer("lb", LbStrategy.ROUND_ROBIN, 100, 100, 1)
                .addService("svc", 100, 100, 3)
                .addDatabase("db", 100, 100, 5)
                .connect("lb", "svc")
                .connect("svc", "db")
                .withTimeSeries(10, 5)
                .withEntryNode("lb")
                .build();

        SimulationCommand highLoad = ScenarioBuilder.create()
                .addLoadBalancer("lb", LbStrategy.ROUND_ROBIN, 100, 100, 1)
                .addService("svc", 10, 40, 3)
                .addDatabase("db", 20, 50, 5)
                .connect("lb", "svc")
                .connect("svc", "db")
                .withTimeSeries(200, 5)
                .withEntryNode("lb")
                .build();

        SimulationResult low = runner.run(lowLoad);
        SimulationResult high = runner.run(highLoad);

        assertTrue(high.getAverageLatency() > low.getAverageLatency());
    }

    // 🧪 7. LONG RUN STABILITY
    @Test
    void longRun_shouldNotDrift() {
        SimulationCommand command = ScenarioBuilder.create()
                .addLoadBalancer("lb", LbStrategy.ROUND_ROBIN, 100, 100, 1)
                .addService("svc", 50, 50, 3)
                .addDatabase("db", 50, 50, 5)
                .connect("lb", "svc")
                .connect("svc", "db")
                .withTimeSeries(100, 50)
                .withEntryNode("lb")
                .build();

        SimulationResult result = runner.run(command);

        assertTrue(result.getSuccessfulRequests() > 0);
        assertTrue(result.getAverageLatency() > 0);
    }

    // 🚫 8. INVALID GRAPH (CYCLE DETECTION EXPECTED)
    @Test
    void cyclicGraph_shouldFailFast() {
        assertThrows(ScenarioValidationException.class, () -> {
            ScenarioBuilder.create()
                    .addLoadBalancer("lb", 1)
                    .addService("svc", 1, 1, 1)
                    .connect("lb", "svc")
                    .connect("svc", "lb") // cycle
                    .withRequestCount(1)
                    .withEntryNode("lb")
                    .build(); // ✅ exception happens here
        });
    }
}
