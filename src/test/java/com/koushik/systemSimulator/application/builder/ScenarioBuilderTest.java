package com.koushik.systemSimulator.application.builder;

import com.koushik.systemSimulator.application.model.SimulationScenario;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ScenarioBuilderTest {

	@Test
	void buildsValidScenario() {
		SimulationScenario scenario = ScenarioBuilder.create()
				.addLoadBalancer("lb", 1)
				.addService("service", 10, 20, 100)
				.addDatabase("db", 5, 10, 200)
				.connect("lb", "service")
				.connect("service", "db")
				.withRequestCount(100)
				.build();

		assertEquals(100, scenario.requestCount());
		assertEquals("service", scenario.topology().getNodeDefinition("lb").downstreamNodeId());
		assertEquals("db", scenario.topology().getNodeDefinition("service").downstreamNodeId());
	}

	@Test
	void rejectsDuplicateNodeIds() {
		ScenarioValidationException exception = assertThrows(ScenarioValidationException.class, () ->
				ScenarioBuilder.create()
						.addLoadBalancer("lb", 0)
						.addLoadBalancer("lb", 1));

		assertEquals("Duplicate node id lb", exception.getMessage());
	}

	@Test
	void rejectsMissingConnectionTarget() {
		ScenarioValidationException exception = assertThrows(ScenarioValidationException.class, () ->
				ScenarioBuilder.create()
						.addLoadBalancer("lb", 0)
						.connect("lb", "service"));

		assertEquals("Node service does not exist", exception.getMessage());
	}

	@Test
	void rejectsIncompleteTopology() {
		ScenarioValidationException exception = assertThrows(ScenarioValidationException.class, () ->
				ScenarioBuilder.create()
						.addLoadBalancer("lb", 0)
						.addService("service", 1, 1, 10)
						.addDatabase("db", 1, 1, 10)
						.connect("lb", "service")
						.build());

		assertEquals("Node service must define a downstream connection", exception.getMessage());
	}
}
