package com.koushik.systemSimulator.application.runner;

import com.koushik.systemSimulator.application.factory.EngineAssemblyFactory;
import com.koushik.systemSimulator.application.model.SimulationScenario;
import com.koushik.systemSimulator.application.model.SimulationSummaryReport;
import com.koushik.systemSimulator.simulation.engine.SimulationEngine;
import com.koushik.systemSimulator.simulation.model.EventType;
import com.koushik.systemSimulator.simulation.model.Request;
import com.koushik.systemSimulator.simulation.model.SimulationEvent;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

@Service
public class DefaultSimulationRunner implements SimulationRunner {

	private final EngineAssemblyFactory engineAssemblyFactory;
	private final ReportAssembler reportAssembler;

	public DefaultSimulationRunner(EngineAssemblyFactory engineAssemblyFactory, ReportAssembler reportAssembler) {
		this.engineAssemblyFactory = engineAssemblyFactory;
		this.reportAssembler = reportAssembler;
	}

	@Override
	public SimulationSummaryReport run(SimulationScenario scenario) {
		SimulationEngine engine = engineAssemblyFactory.create(scenario);
		com.koushik.systemSimulator.simulation.engine.SimulationReport engineReport = engine.run(seedEvents(scenario));
		return reportAssembler.assemble(scenario, engineReport);
	}

	private List<SimulationEvent> seedEvents(SimulationScenario scenario) {
		String entryNodeId = scenario.topology().nodeDefinitions().stream()
				.filter(nodeDefinition -> nodeDefinition.nodeType() == com.koushik.systemSimulator.simulation.model.NodeType.LOAD_BALANCER)
				.findFirst()
				.orElseThrow(() -> new IllegalArgumentException("Scenario must contain a load balancer"))
				.nodeId();

		return IntStream.range(0, scenario.requestCount())
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
