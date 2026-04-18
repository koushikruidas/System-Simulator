package com.koushik.systemSimulator.simulation.node;

import com.koushik.systemSimulator.simulation.model.SimulationEvent;

public interface SimNode {

	NodeResult handle(SimulationEvent event, NodeExecutionContext context);
}
