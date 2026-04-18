package com.koushik.systemSimulator.application.factory;

import com.koushik.systemSimulator.simulation.model.NodeType;
import com.koushik.systemSimulator.simulation.node.ServiceNode;
import com.koushik.systemSimulator.simulation.node.SimNode;
import com.koushik.systemSimulator.simulation.scenario.NodeDefinition;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SimNodeFactoryRegistryTest {

	@Test
	void createsNodeUsingRegisteredFactory() {
		SimNodeFactoryRegistry registry = new SimNodeFactoryRegistry(List.of(new ServiceNodeFactory()));

		SimNode node = registry.create(new NodeDefinition("service", NodeType.SERVICE, 1, 1, 10, "db"));

		assertInstanceOf(ServiceNode.class, node);
	}

	@Test
	void rejectsDuplicateFactoryRegistration() {
		IllegalStateException exception = assertThrows(IllegalStateException.class, () ->
				new SimNodeFactoryRegistry(List.of(new ServiceNodeFactory(), new ServiceNodeFactory())));

		assertEquals("Duplicate SimNodeFactory registration for SERVICE", exception.getMessage());
	}
}
