package com.koushik.systemSimulator.application.runner;

import com.koushik.systemSimulator.application.adapter.SimulationScenarioAdapter;
import com.koushik.systemSimulator.application.factory.SimulationEngineFactory;
import com.koushik.systemSimulator.application.model.SimulationCommand;
import com.koushik.systemSimulator.application.model.SimulationResult;
import com.koushik.systemSimulator.simulation.engine.SimulationEngine;
import com.koushik.systemSimulator.simulation.engine.SimulationReport;
import com.koushik.systemSimulator.simulation.engine.TimeStepReport;
import com.koushik.systemSimulator.simulation.engine.TimeStepSimulationEngine;
import com.koushik.systemSimulator.simulation.model.EventType;
import com.koushik.systemSimulator.simulation.model.Request;
import com.koushik.systemSimulator.simulation.model.SimulationEvent;
import com.koushik.systemSimulator.simulation.scenario.Topology;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

@Service
public class DefaultSimulationRunner implements SimulationRunner {

	private static final boolean USE_NEW_ENGINE    = true;
	private static final boolean COMPARE_ENGINES   = false;

	private final SimulationScenarioAdapter scenarioAdapter;
	private final SimulationEngineFactory simulationEngineFactory;
	private final SimulationResultAssembler resultAssembler;

	public DefaultSimulationRunner(
			SimulationScenarioAdapter scenarioAdapter,
			SimulationEngineFactory simulationEngineFactory,
			SimulationResultAssembler resultAssembler
	) {
		this.scenarioAdapter = scenarioAdapter;
		this.simulationEngineFactory = simulationEngineFactory;
		this.resultAssembler = resultAssembler;
	}

	@Override
	public SimulationResult run(SimulationCommand command) {
		Topology topology = scenarioAdapter.toDomainTopology(command);

		if (command.isTimeBased() && COMPARE_ENGINES) {
			compareEngines(command, topology);
		}

		if (command.isTimeBased() && USE_NEW_ENGINE) {
			List<String> entryNodeIds = resolveEntryNodes(topology, command);
			TimeStepSimulationEngine engine = new TimeStepSimulationEngine(
					topology,
					entryNodeIds,
					command.getArrivalRate(),
					command.getSimulationDuration());
			TimeStepReport report = engine.run();
			return resultAssembler.assembleFromTimeStep(command, report);
		}

		SimulationEngine engine = simulationEngineFactory.create(topology);
		SimulationReport engineReport = engine.run(seedEvents(command, topology));
		return resultAssembler.assemble(command, engineReport);
	}

	private List<String> resolveEntryNodes(Topology topology, SimulationCommand command) {
		List<String> graphEntries = topology.getEntryNodes();
		if (!graphEntries.isEmpty()) {
			return graphEntries;
		}
		// Fallback: explicit list from command, then single entryNodeId
		if (command.getEntryNodeIds() != null && !command.getEntryNodeIds().isEmpty()) {
			return command.getEntryNodeIds();
		}
		return List.of(command.getEntryNodeId());
	}

	private List<SimulationEvent> seedEvents(SimulationCommand command, Topology topology) {
		return command.isTimeBased() ? timeBasedSeeds(command, topology) : batchSeeds(command, topology);
	}

	private List<SimulationEvent> batchSeeds(SimulationCommand command, Topology topology) {
		List<String> entryNodes = resolveEntryNodes(topology, command);
		int n = entryNodes.size();
		return IntStream.range(0, command.getRequestCount())
				.mapToObj(index -> {
					String requestId = "request-" + (index + 1);
					Request request = new Request(requestId, "HTTP", 0, Map.of());
					String targetNode = entryNodes.get(index % n);
					return new SimulationEvent(
							requestId + "-seed", 0, index,
							EventType.REQUEST_ARRIVED, request, "client", targetNode);
				})
				.toList();
	}

	private List<SimulationEvent> timeBasedSeeds(SimulationCommand command, Topology topology) {
		int rate = command.getArrivalRate();
		int duration = command.getSimulationDuration();
		List<String> entryNodes = resolveEntryNodes(topology, command);
		int n = entryNodes.size();
		List<SimulationEvent> seeds = new ArrayList<>();
		int idx = 0;
		for (int t = 0; t < duration; t++) {
			for (int r = 0; r < rate; r++) {
				String requestId = "request-" + (++idx);
				Request request = new Request(requestId, "HTTP", t, Map.of());
				String targetNode = entryNodes.get((idx - 1) % n);
				seeds.add(new SimulationEvent(
						requestId + "-seed", t, idx - 1,
						EventType.REQUEST_ARRIVED, request, "client", targetNode));
			}
		}
		return seeds;
	}

	private void compareEngines(SimulationCommand command, Topology topology) {
		// Run old engine
		SimulationEngine oldEngine = simulationEngineFactory.create(topology);
		SimulationReport oldReport = oldEngine.run(timeBasedSeeds(command, topology));
		long oldCompleted   = oldReport.metrics().completedRequests();
		long oldDropped     = oldReport.metrics().droppedRequests();
		double oldAvgLatency = oldReport.metrics().averageLatency();
		Map<String, Long> oldNodeProcessed = oldReport.metrics().nodeProcessedCounts();

		// Run new engine (topology is immutable — safe to reuse)
		TimeStepSimulationEngine newEngine = new TimeStepSimulationEngine(
				topology,
				resolveEntryNodes(topology, command),
				command.getArrivalRate(),
				command.getSimulationDuration());
		TimeStepReport newReport = newEngine.run();
		long newCompleted   = newReport.totalCompleted();
		long newDropped     = newReport.totalDropped();
		double newAvgLatency = newReport.totalCompleted() > 0
				? (double) newReport.totalLatencySum() / newReport.totalCompleted() : 0.0;
		Map<String, Long> newNodeProcessed = newReport.nodeProcessedCounts();

		boolean countsMatch    = oldCompleted == newCompleted && oldDropped == newDropped;
		boolean nodeCountsMatch = oldNodeProcessed.equals(newNodeProcessed);

		if (!countsMatch || !nodeCountsMatch) {
			System.err.printf(
					"[EngineCompare] MISMATCH — completed: old=%d new=%d | dropped: old=%d new=%d" +
					" | avgLatency: old=%.2f new=%.2f | perNodeMatch=%b%n",
					oldCompleted, newCompleted, oldDropped, newDropped,
					oldAvgLatency, newAvgLatency, nodeCountsMatch);
			if (!nodeCountsMatch) {
				oldNodeProcessed.forEach((nodeId, count) ->
						System.err.printf("  node=%s old=%d new=%d%n",
								nodeId, count, newNodeProcessed.getOrDefault(nodeId, 0L)));
			}
		} else {
			System.out.printf(
					"[EngineCompare] MATCH — completed=%d dropped=%d" +
					" | avgLatency: old=%.2f new=%.2f | perNodeCounts=OK%n",
					oldCompleted, newCompleted, oldAvgLatency, newAvgLatency);
		}

		// Queue depth: old engine always drains to 0; check new engine did the same
		if (!newReport.ticks().isEmpty()) {
			Map<String, Integer> finalQueues =
					newReport.ticks().get(newReport.ticks().size() - 1).queueDepths();
			long totalQueued = finalQueues.values().stream().mapToLong(Integer::longValue).sum();
			if (totalQueued > 0) {
				System.err.printf(
						"[EngineCompare] WARNING: new engine final queues non-zero (old engine drains to 0) — total=%d%n",
						totalQueued);
				finalQueues.forEach((nodeId, depth) -> {
					if (depth > 0) System.err.printf("  node=%s queued=%d%n", nodeId, depth);
				});
			} else {
				System.out.printf("[EngineCompare] queue depths: both engines drained to 0 OK%n");
			}
		}
	}
}
