package com.koushik.systemSimulator.simulation.engine;

import com.koushik.systemSimulator.simulation.metrics.MetricsCollector;
import com.koushik.systemSimulator.simulation.model.SimulationEvent;
import com.koushik.systemSimulator.simulation.node.NodeExecutionContext;
import com.koushik.systemSimulator.simulation.node.NodeRegistry;
import com.koushik.systemSimulator.simulation.node.NodeResult;
import com.koushik.systemSimulator.simulation.node.SimNode;
import com.koushik.systemSimulator.simulation.scheduler.EventScheduler;
import com.koushik.systemSimulator.simulation.state.NodeRuntimeState;
import com.koushik.systemSimulator.simulation.state.RuntimeStateStore;
import com.koushik.systemSimulator.simulation.state.StateMutation;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class SimulationEngine {

	private final EventScheduler eventScheduler;
	private final VirtualClock virtualClock;
	private final NodeRegistry nodeRegistry;
	private final RuntimeStateStore runtimeStateStore;
	private final MetricsCollector metricsCollector;

	public SimulationEngine(
			EventScheduler eventScheduler,
			VirtualClock virtualClock,
			NodeRegistry nodeRegistry,
			RuntimeStateStore runtimeStateStore,
			MetricsCollector metricsCollector
	) {
		this.eventScheduler = eventScheduler;
		this.virtualClock = virtualClock;
		this.nodeRegistry = nodeRegistry;
		this.runtimeStateStore = runtimeStateStore;
		this.metricsCollector = metricsCollector;
	}

	public SimulationReport run(List<SimulationEvent> seedEvents) {
		List<SimulationEvent> sortedSeedEvents = seedEvents.stream()
				.sorted(Comparator.comparingLong(SimulationEvent::timestamp)
						.thenComparingLong(SimulationEvent::sequenceNumber))
				.toList();
		for (SimulationEvent seedEvent : sortedSeedEvents) {
			eventScheduler.schedule(seedEvent);
			runtimeStateStore.getOrCreateRequestState(seedEvent.request());
		}

		long initialSequence = sortedSeedEvents.stream()
				.mapToLong(SimulationEvent::sequenceNumber)
				.max()
				.orElse(-1L) + 1;
		SequenceNumberGenerator sequenceNumberGenerator = new SequenceNumberGenerator(initialSequence);
		List<SimulationEvent> processedEvents = new ArrayList<>();

		while (!eventScheduler.isEmpty()) {
			SimulationEvent event = eventScheduler.pollNext();
			virtualClock.advanceTo(event.timestamp());
			metricsCollector.recordEventProcessed(event);
			processedEvents.add(event);

			SimNode node = nodeRegistry.getNode(event.targetNodeId());
			NodeRuntimeState nodeRuntimeState = runtimeStateStore.getNodeState(event.targetNodeId());
			NodeExecutionContext context = new NodeExecutionContext(
					virtualClock.now(),
					nodeRegistry.getDefinition(event.targetNodeId()),
					nodeRuntimeState,
					nodeRegistry.topology(),
					runtimeStateStore,
					sequenceNumberGenerator
			);

			NodeResult result = node.handle(event, context);
			for (StateMutation mutation : result.stateMutations()) {
				mutation.apply(runtimeStateStore);
			}
			metricsCollector.recordSignals(result.metricSignals());
			for (SimulationEvent emittedEvent : result.emittedEvents()) {
				eventScheduler.schedule(emittedEvent);
			}
		}

		return new SimulationReport(
				virtualClock.now(),
				List.copyOf(processedEvents),
				List.copyOf(runtimeStateStore.allRequestStates()),
				metricsCollector.snapshot()
		);
	}
}
