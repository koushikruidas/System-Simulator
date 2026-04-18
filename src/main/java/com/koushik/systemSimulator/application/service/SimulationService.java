package com.koushik.systemSimulator.application.service;

import com.koushik.systemSimulator.application.model.SimulationCommand;
import com.koushik.systemSimulator.application.model.SimulationResult;
import com.koushik.systemSimulator.application.runner.SimulationRunner;
import org.springframework.stereotype.Service;

@Service
public class SimulationService {

	private final SimulationRunner simulationRunner;

	public SimulationService(SimulationRunner simulationRunner) {
		this.simulationRunner = simulationRunner;
	}

	public SimulationResult run(SimulationCommand command) {
		return simulationRunner.run(command);
	}
}
