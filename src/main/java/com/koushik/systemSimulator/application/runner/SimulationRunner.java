package com.koushik.systemSimulator.application.runner;

import com.koushik.systemSimulator.application.model.SimulationScenario;
import com.koushik.systemSimulator.application.model.SimulationSummaryReport;

public interface SimulationRunner {

	SimulationSummaryReport run(SimulationScenario scenario);
}
