package com.koushik.systemSimulator.simulation.scheduler;

import com.koushik.systemSimulator.simulation.model.SimulationEvent;

public interface EventScheduler {

	void schedule(SimulationEvent event);

	SimulationEvent pollNext();

	boolean isEmpty();
}
