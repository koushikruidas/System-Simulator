package com.koushik.systemSimulator.application.runner;

import com.koushik.systemSimulator.application.adapter.SimulationScenarioAdapter;
import com.koushik.systemSimulator.application.factory.SimulationEngineFactory;
import com.koushik.systemSimulator.application.model.NodeType;
import com.koushik.systemSimulator.application.model.SimulationCommand;
import com.koushik.systemSimulator.application.model.SimulationResult;
import com.koushik.systemSimulator.simulation.engine.SimulationEngine;
import com.koushik.systemSimulator.simulation.model.EventType;
import com.koushik.systemSimulator.simulation.model.Request;
import com.koushik.systemSimulator.simulation.model.SimulationEvent;
import com.koushik.systemSimulator.simulation.scenario.Topology;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

@Service
public class DefaultSimulationRunner implements SimulationRunner {

	private final SimulationScenarioAdapter scenarioAdapter;
	private final SimulationEngineFactory simulationEngineFactory;
	private final SimulationResultAssembler resultAssembler;

	public DefaultSimulationRunner(
			SimulationScenarioAdapter scenarioAdapter,
			SimulationEngineFactory simulationEngineFactory,
			SimulationResultAssembler resultAssembler
	) {
		this.scenarioAdapter = scenarioAdapter;
		this.simulationEngineFactory = simulationEngineFactory;
		this.resultAssembler = resultAssembler;
	}

	@Override
	public SimulationResult run(SimulationCommand command) {
		Topology topology = scenarioAdapter.toDomainTopology(command);
		SimulationEngine engine = simulationEngineFactory.create(topology);
		com.koushik.systemSimulator.simulation.engine.SimulationReport engineReport = engine.run(seedEvents(command));
		return resultAssembler.assemble(command, engineReport);
	}

	private List<SimulationEvent> seedEvents(SimulationCommand command) {
		String entryNodeId = command.getNodes().stream()
				.filter(node -> node.getNodeType() == NodeType.LOAD_BALANCER)
				.findFirst()
				.orElseThrow(() -> new IllegalArgumentException("Scenario must contain a load balancer"))
				.getNodeId();

		return IntStream.range(0, command.getRequestCount())
				.mapToObj(index -> {
					String requestId = "request-" + (index + 1);
					Request request = new Request(requestId, "HTTP", 0, Map.of());
					return new SimulationEvent(
							requestId + "-seed",
							0,
							index,
							EventType.REQUEST_ARRIVED,
							request,
							"client",
							entryNodeId
					);
				})
				.toList();
	}
}
