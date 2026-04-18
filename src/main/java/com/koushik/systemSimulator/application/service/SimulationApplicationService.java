package com.koushik.systemSimulator.application.service;

import com.koushik.systemSimulator.application.builder.ScenarioBuilder;
import com.koushik.systemSimulator.application.model.ConnectionCommand;
import com.koushik.systemSimulator.application.model.SimulationRequestCommand;
import com.koushik.systemSimulator.application.model.SimulationScenario;
import com.koushik.systemSimulator.application.model.SimulationSummaryReport;
import com.koushik.systemSimulator.application.runner.SimulationRunner;
import org.springframework.stereotype.Service;

@Service
public class SimulationApplicationService {

	private final NodeScenarioConfigurerRegistry nodeScenarioConfigurerRegistry;
	private final SimulationRunner simulationRunner;

	public SimulationApplicationService(NodeScenarioConfigurerRegistry nodeScenarioConfigurerRegistry, SimulationRunner simulationRunner) {
		this.nodeScenarioConfigurerRegistry = nodeScenarioConfigurerRegistry;
		this.simulationRunner = simulationRunner;
	}

	public SimulationSummaryReport simulate(SimulationRequestCommand command) {
		ScenarioBuilder builder = ScenarioBuilder.create();
		command.nodes().forEach(nodeCommand -> nodeScenarioConfigurerRegistry.configure(builder, nodeCommand));
		for (ConnectionCommand connectionCommand : command.connections()) {
			builder.connect(connectionCommand.sourceNodeId(), connectionCommand.targetNodeId());
		}
		SimulationScenario scenario = builder.withRequestCount(command.requestCount()).build();
		return simulationRunner.run(scenario);
	}
}
