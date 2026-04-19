package com.koushik.systemSimulator.application.runner;

import com.koushik.systemSimulator.application.adapter.DatabaseNodeConfigMapper;
import com.koushik.systemSimulator.application.adapter.LoadBalancerNodeConfigMapper;
import com.koushik.systemSimulator.application.adapter.NodeConfigMapperRegistry;
import com.koushik.systemSimulator.application.adapter.ServiceNodeConfigMapper;
import com.koushik.systemSimulator.application.adapter.SimulationScenarioAdapter;
import com.koushik.systemSimulator.application.builder.ScenarioBuilder;
import com.koushik.systemSimulator.application.factory.DatabaseNodeFactory;
import com.koushik.systemSimulator.application.factory.LeastConnectionsLoadBalancerNodeFactory;
import com.koushik.systemSimulator.application.factory.RoundRobinLoadBalancerNodeFactory;
import com.koushik.systemSimulator.application.factory.ServiceNodeFactory;
import com.koushik.systemSimulator.application.factory.SimNodeFactoryRegistry;
import com.koushik.systemSimulator.application.factory.SimulationEngineFactory;
import com.koushik.systemSimulator.application.model.LbStrategy;
import com.koushik.systemSimulator.application.model.NodeLatencyBreakdown;
import com.koushik.systemSimulator.application.model.RequestOutcome;
import com.koushik.systemSimulator.application.model.RequestTrace;
import com.koushik.systemSimulator.application.model.SimulationCommand;
import com.koushik.systemSimulator.application.model.SimulationResult;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DefaultSimulationRunnerTest {

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

	@Test
	void runsScenarioAndBuildsSummaryReport() {
		SimulationCommand command = ScenarioBuilder.create()
				.addLoadBalancer("lb", LbStrategy.ROUND_ROBIN, 1, 0, 1)
				.addService("service", 1, 1, 5)
				.addDatabase("db", 1, 1, 10)
				.connect("lb", "service")
				.connect("service", "db")
				.withRequestCount(1)
				.withEntryNode("lb")
				.build();

		SimulationResult report = buildRunner().run(command);

		assertEquals(1, report.getTotalRequests());
		assertEquals(1, report.getSuccessfulRequests());
		assertEquals(0, report.getFailedRequests());
		assertEquals(16.0, report.getAverageLatency());
		assertEquals(1L, report.getNodeMetrics().get("lb").getProcessedRequests());
		assertEquals(1L, report.getNodeMetrics().get("service").getProcessedRequests());
		assertEquals(1L, report.getNodeMetrics().get("db").getProcessedRequests());
	}

	@Test
	void runsRoundRobinLbWithTwoServicesDistributesEvenly() {
		SimulationCommand command = ScenarioBuilder.create()
				.addLoadBalancer("lb", LbStrategy.ROUND_ROBIN, 4, 0, 0)
				.addService("s1", 2, 5, 10)
				.addService("s2", 2, 5, 10)
				.addDatabase("db", 4, 10, 5)
				.connect("lb", "s1")
				.connect("lb", "s2")
				.connect("s1", "db")
				.connect("s2", "db")
				.withRequestCount(4)
				.withEntryNode("lb")
				.build();

		SimulationResult report = buildRunner().run(command);

		assertEquals(4, report.getTotalRequests());
		assertEquals(4, report.getSuccessfulRequests());
		assertEquals(2L, report.getNodeMetrics().get("s1").getProcessedRequests());
		assertEquals(2L, report.getNodeMetrics().get("s2").getProcessedRequests());
	}

	@Test
	void tracksHopPathForCompletedRequest() {
		SimulationCommand command = ScenarioBuilder.create()
				.addLoadBalancer("lb", LbStrategy.ROUND_ROBIN, 1, 0, 1)
				.addService("service", 1, 1, 5)
				.addDatabase("db", 1, 1, 10)
				.connect("lb", "service")
				.connect("service", "db")
				.withRequestCount(1)
				.withEntryNode("lb")
				.build();

		SimulationResult result = buildRunner().run(command);

		assertEquals(1, result.getRequestTraces().size());
		RequestTrace trace = result.getRequestTraces().get(0);
		assertEquals(List.of("lb", "service", "db"), trace.getPath());
		assertEquals(RequestOutcome.COMPLETED, trace.getOutcome());
	}

	@Test
	void tracksHopPathForAllRequestsWithRoundRobinLb() {
		SimulationCommand command = ScenarioBuilder.create()
				.addLoadBalancer("lb", LbStrategy.ROUND_ROBIN, 4, 0, 0)
				.addService("s1", 2, 5, 10)
				.addService("s2", 2, 5, 10)
				.addDatabase("db", 4, 10, 5)
				.connect("lb", "s1")
				.connect("lb", "s2")
				.connect("s1", "db")
				.connect("s2", "db")
				.withRequestCount(4)
				.withEntryNode("lb")
				.build();

		SimulationResult result = buildRunner().run(command);

		assertEquals(4, result.getRequestTraces().size());
		assertTrue(result.getRequestTraces().stream().allMatch(t -> t.getPath().get(0).equals("lb")));
		assertTrue(result.getRequestTraces().stream().allMatch(t -> t.getPath().get(2).equals("db")));
		long s1Count = result.getRequestTraces().stream().filter(t -> t.getPath().get(1).equals("s1")).count();
		long s2Count = result.getRequestTraces().stream().filter(t -> t.getPath().get(1).equals("s2")).count();
		assertEquals(2, s1Count);
		assertEquals(2, s2Count);
	}

	@Test
	void breakdownShowsZeroQueueTimeWhenNoContention() {
		SimulationCommand command = ScenarioBuilder.create()
				.addLoadBalancer("lb", LbStrategy.ROUND_ROBIN, 1, 0, 1)
				.addService("service", 1, 1, 5)
				.addDatabase("db", 1, 1, 10)
				.connect("lb", "service")
				.connect("service", "db")
				.withRequestCount(1)
				.withEntryNode("lb")
				.build();

		SimulationResult result = buildRunner().run(command);

		RequestTrace trace = result.getRequestTraces().get(0);
		assertEquals(16, trace.getTotalLatency());
		assertEquals(3, trace.getBreakdown().size());

		NodeLatencyBreakdown lbBreakdown = trace.getBreakdown().get(0);
		assertEquals("lb", lbBreakdown.getNodeId());
		assertEquals(0, lbBreakdown.getQueueTime());
		assertEquals(1, lbBreakdown.getProcessingTime());

		NodeLatencyBreakdown svcBreakdown = trace.getBreakdown().get(1);
		assertEquals("service", svcBreakdown.getNodeId());
		assertEquals(0, svcBreakdown.getQueueTime());
		assertEquals(5, svcBreakdown.getProcessingTime());

		NodeLatencyBreakdown dbBreakdown = trace.getBreakdown().get(2);
		assertEquals("db", dbBreakdown.getNodeId());
		assertEquals(0, dbBreakdown.getQueueTime());
		assertEquals(10, dbBreakdown.getProcessingTime());
	}

	@Test
	void breakdownShowsQueueTimeWhenCongested() {
		SimulationCommand command = ScenarioBuilder.create()
				.addLoadBalancer("lb", LbStrategy.ROUND_ROBIN, 2, 0, 0)
				.addService("service", 1, 2, 10)
				.addDatabase("db", 2, 2, 5)
				.connect("lb", "service")
				.connect("service", "db")
				.withRequestCount(2)
				.withEntryNode("lb")
				.build();

		SimulationResult result = buildRunner().run(command);

		RequestTrace r1 = result.getRequestTraces().stream()
				.filter(t -> t.getRequestId().equals("request-1")).findFirst().orElseThrow();
		RequestTrace r2 = result.getRequestTraces().stream()
				.filter(t -> t.getRequestId().equals("request-2")).findFirst().orElseThrow();

		assertEquals(0, r1.getBreakdown().get(1).getQueueTime());
		assertEquals(10, r2.getBreakdown().get(1).getQueueTime());
	}
}
