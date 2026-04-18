package com.koushik.systemSimulator.simulation.node;

import com.koushik.systemSimulator.simulation.metrics.MetricSignal;
import com.koushik.systemSimulator.simulation.model.SimulationEvent;
import com.koushik.systemSimulator.simulation.state.StateMutation;

import java.util.ArrayList;
import java.util.List;

public final class NodeResult {

	private final List<SimulationEvent> emittedEvents;
	private final List<StateMutation> stateMutations;
	private final List<MetricSignal> metricSignals;

	private NodeResult(List<SimulationEvent> emittedEvents, List<StateMutation> stateMutations, List<MetricSignal> metricSignals) {
		this.emittedEvents = List.copyOf(emittedEvents);
		this.stateMutations = List.copyOf(stateMutations);
		this.metricSignals = List.copyOf(metricSignals);
	}

	public static NodeResult empty() {
		return builder().build();
	}

	public static Builder builder() {
		return new Builder();
	}

	public List<SimulationEvent> emittedEvents() {
		return emittedEvents;
	}

	public List<StateMutation> stateMutations() {
		return stateMutations;
	}

	public List<MetricSignal> metricSignals() {
		return metricSignals;
	}

	public static final class Builder {

		private final List<SimulationEvent> emittedEvents = new ArrayList<>();
		private final List<StateMutation> stateMutations = new ArrayList<>();
		private final List<MetricSignal> metricSignals = new ArrayList<>();

		public Builder emit(SimulationEvent event) {
			emittedEvents.add(event);
			return this;
		}

		public Builder mutate(StateMutation mutation) {
			stateMutations.add(mutation);
			return this;
		}

		public Builder metric(MetricSignal metricSignal) {
			metricSignals.add(metricSignal);
			return this;
		}

		public NodeResult build() {
			return new NodeResult(emittedEvents, stateMutations, metricSignals);
		}
	}
}
