package com.koushik.systemSimulator.application.runner;

import com.koushik.systemSimulator.application.builder.ScenarioBuilder;
import com.koushik.systemSimulator.application.factory.DatabaseNodeFactory;
import com.koushik.systemSimulator.application.factory.EngineAssemblyFactory;
import com.koushik.systemSimulator.application.factory.LoadBalancerNodeFactory;
import com.koushik.systemSimulator.application.factory.ServiceNodeFactory;
import com.koushik.systemSimulator.application.factory.SimNodeFactoryRegistry;
import com.koushik.systemSimulator.application.model.SimulationScenario;
import com.koushik.systemSimulator.application.model.SimulationSummaryReport;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DefaultSimulationRunnerTest {

	@Test
	void runsScenarioAndBuildsSummaryReport() {
		SimulationScenario scenario = ScenarioBuilder.create()
				.addLoadBalancer("lb", 1)
				.addService("service", 1, 1, 5)
				.addDatabase("db", 1, 1, 10)
				.connect("lb", "service")
				.connect("service", "db")
				.withRequestCount(1)
				.build();

		DefaultSimulationRunner runner = new DefaultSimulationRunner(
				new EngineAssemblyFactory(new SimNodeFactoryRegistry(List.of(
						new LoadBalancerNodeFactory(),
						new ServiceNodeFactory(),
						new DatabaseNodeFactory()
				))),
				new ReportAssembler()
		);

		SimulationSummaryReport report = runner.run(scenario);

		assertEquals(1, report.totalRequests());
		assertEquals(1, report.successfulRequests());
		assertEquals(0, report.failedRequests());
		assertEquals(16.0, report.averageLatency());
		assertEquals(1L, report.nodeMetrics().get("lb").processedRequests());
		assertEquals(1L, report.nodeMetrics().get("service").processedRequests());
		assertEquals(1L, report.nodeMetrics().get("db").processedRequests());
	}
}
