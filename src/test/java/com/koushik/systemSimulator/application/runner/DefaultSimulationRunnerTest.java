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
import com.koushik.systemSimulator.application.model.SimulationCommand;
import com.koushik.systemSimulator.application.model.SimulationResult;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

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
}
