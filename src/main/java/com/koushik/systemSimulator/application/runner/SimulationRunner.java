package com.koushik.systemSimulator.application.runner;

import com.koushik.systemSimulator.application.model.SimulationCommand;
import com.koushik.systemSimulator.application.model.SimulationResult;

public interface SimulationRunner {

	SimulationResult run(SimulationCommand command);
}
