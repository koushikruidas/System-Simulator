package com.koushik.systemSimulator.simulation.scheduler;

import com.koushik.systemSimulator.simulation.model.SimulationEvent;

import java.util.Comparator;
import java.util.Objects;
import java.util.PriorityQueue;

public final class PriorityQueueEventScheduler implements EventScheduler {

	private static final Comparator<SimulationEvent> EVENT_ORDER = Comparator
			.comparingLong(SimulationEvent::timestamp)
			.thenComparingLong(SimulationEvent::sequenceNumber);

	private final PriorityQueue<SimulationEvent> queue = new PriorityQueue<>(EVENT_ORDER);

	@Override
	public void schedule(SimulationEvent event) {
		queue.add(Objects.requireNonNull(event, "event must not be null"));
	}

	@Override
	public SimulationEvent pollNext() {
		return queue.poll();
	}

	@Override
	public boolean isEmpty() {
		return queue.isEmpty();
	}
}
