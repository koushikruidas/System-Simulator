package com.koushik.systemSimulator.simulation.node;

import com.koushik.systemSimulator.simulation.model.SimulationEvent;

@FunctionalInterface
public interface NodeEventHandler {

	NodeResult handle(SimulationEvent event, NodeExecutionContext context);
}
