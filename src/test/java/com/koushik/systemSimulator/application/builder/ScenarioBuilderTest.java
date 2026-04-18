package com.koushik.systemSimulator.application.builder;

import com.koushik.systemSimulator.application.model.NodeConfig;
import com.koushik.systemSimulator.application.model.NodeType;
import com.koushik.systemSimulator.application.model.SimulationCommand;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ScenarioBuilderTest {

	@Test
	void buildsValidScenario() {
		SimulationCommand command = ScenarioBuilder.create()
				.addLoadBalancer("lb", 1)
				.addService("service", 10, 20, 100)
				.addDatabase("db", 5, 10, 200)
				.connect("lb", "service")
				.connect("service", "db")
				.withRequestCount(100)
				.build();

		assertEquals(100, command.getRequestCount());
		assertEquals(3, command.getNodes().size());
		NodeConfig loadBalancer = command.getNodes().stream()
				.filter(node -> node.getNodeId().equals("lb"))
				.findFirst()
				.orElseThrow();
		NodeConfig service = command.getNodes().stream()
				.filter(node -> node.getNodeId().equals("service"))
				.findFirst()
				.orElseThrow();
		assertEquals(NodeType.LOAD_BALANCER, loadBalancer.getNodeType());
		assertEquals(NodeType.SERVICE, service.getNodeType());
		assertEquals("service", command.getConnections().get(0).getTargetNodeId());
		assertEquals("db", command.getConnections().get(1).getTargetNodeId());
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
