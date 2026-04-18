package com.koushik.systemSimulator.application.factory;

import com.koushik.systemSimulator.simulation.engine.SimulationEngine;
import com.koushik.systemSimulator.simulation.engine.VirtualClock;
import com.koushik.systemSimulator.simulation.metrics.InMemoryMetricsCollector;
import com.koushik.systemSimulator.simulation.node.NodeRegistry;
import com.koushik.systemSimulator.simulation.node.SimNode;
import com.koushik.systemSimulator.simulation.scheduler.PriorityQueueEventScheduler;
import com.koushik.systemSimulator.simulation.scenario.NodeDefinition;
import com.koushik.systemSimulator.simulation.scenario.Topology;
import com.koushik.systemSimulator.simulation.state.RuntimeStateStore;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

@Component
public class SimulationEngineFactory {

	private final SimNodeFactoryRegistry simNodeFactoryRegistry;

	public SimulationEngineFactory(SimNodeFactoryRegistry simNodeFactoryRegistry) {
		this.simNodeFactoryRegistry = simNodeFactoryRegistry;
	}

	public SimulationEngine create(Topology topology) {
		Map<String, SimNode> nodesById = new LinkedHashMap<>();
		for (NodeDefinition nodeDefinition : topology.nodeDefinitions()) {
			nodesById.put(nodeDefinition.nodeId(), simNodeFactoryRegistry.create(nodeDefinition));
		}
		return new SimulationEngine(
				new PriorityQueueEventScheduler(),
				new VirtualClock(),
				new NodeRegistry(topology, nodesById),
				new RuntimeStateStore(topology.nodeDefinitions()),
				new InMemoryMetricsCollector()
		);
	}
}
