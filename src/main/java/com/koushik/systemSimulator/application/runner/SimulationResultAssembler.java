package com.koushik.systemSimulator.application.runner;

import com.koushik.systemSimulator.application.model.NodeConfig;
import com.koushik.systemSimulator.application.model.NodeLatencyBreakdown;
import com.koushik.systemSimulator.application.model.NodeMetrics;
import com.koushik.systemSimulator.application.model.RequestOutcome;
import com.koushik.systemSimulator.application.model.RequestTrace;
import com.koushik.systemSimulator.application.model.SimulationCommand;
import com.koushik.systemSimulator.application.model.SimulationResult;
import com.koushik.systemSimulator.simulation.engine.SimulationReport;
import com.koushik.systemSimulator.simulation.metrics.SimulationMetrics;
import com.koushik.systemSimulator.simulation.model.EventType;
import com.koushik.systemSimulator.simulation.model.SimulationEvent;
import com.koushik.systemSimulator.simulation.state.RequestRuntimeState;
import com.koushik.systemSimulator.simulation.state.RequestStatus;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
public class SimulationResultAssembler {

	public SimulationResult assemble(SimulationCommand command, SimulationReport engineReport) {
		SimulationMetrics metrics = engineReport.metrics();

		Map<String, NodeMetrics> nodeMetrics = new LinkedHashMap<>();
		for (NodeConfig nodeConfig : command.getNodes()) {
			nodeMetrics.put(
					nodeConfig.getNodeId(),
					NodeMetrics.builder()
							.processedRequests(metrics.nodeProcessedCounts().getOrDefault(nodeConfig.getNodeId(), 0L))
							.droppedRequests(metrics.nodeDroppedCounts().getOrDefault(nodeConfig.getNodeId(), 0L))
							.build()
			);
		}

		Map<String, Long> latencyByNodeId = command.getNodes().stream()
				.collect(Collectors.toMap(NodeConfig::getNodeId, NodeConfig::getLatency));

		Map<String, Map<String, Long>> arrivals = new HashMap<>();
		Map<String, Map<String, Long>> completions = new HashMap<>();
		for (SimulationEvent event : engineReport.processedEvents()) {
			String reqId = event.request().requestId();
			String nodeId = event.targetNodeId();
			if (event.eventType() == EventType.REQUEST_ARRIVED) {
				arrivals.computeIfAbsent(reqId, k -> new HashMap<>()).put(nodeId, event.timestamp());
			} else if (event.eventType() == EventType.PROCESSING_COMPLETED) {
				completions.computeIfAbsent(reqId, k -> new HashMap<>()).put(nodeId, event.timestamp());
			}
		}

		List<RequestTrace> traces = engineReport.requestStates().stream()
				.map(state -> {
					List<String> path = deduplicate(state.hopHistory());
					String reqId = state.request().requestId();
					long totalLatency = state.completedAt() != null
							? state.completedAt() - state.request().createdAt() : 0L;
					List<NodeLatencyBreakdown> breakdown = buildBreakdown(
							path,
							arrivals.getOrDefault(reqId, Map.of()),
							completions.getOrDefault(reqId, Map.of()),
							latencyByNodeId
					);
					return RequestTrace.builder()
							.requestId(reqId)
							.path(path)
							.outcome(state.status() == RequestStatus.COMPLETED
									? RequestOutcome.COMPLETED : RequestOutcome.DROPPED)
							.totalLatency(totalLatency)
							.breakdown(breakdown)
							.build();
				})
				.toList();

		return SimulationResult.builder()
				.totalRequests(command.getRequestCount())
				.successfulRequests((int) metrics.completedRequests())
				.failedRequests((int) metrics.droppedRequests())
				.averageLatency(metrics.averageLatency())
				.nodeMetrics(nodeMetrics)
				.requestTraces(traces)
				.build();
	}

	private List<NodeLatencyBreakdown> buildBreakdown(
			List<String> path,
			Map<String, Long> nodeArrivals,
			Map<String, Long> nodeCompletions,
			Map<String, Long> latencyByNodeId) {

		return path.stream()
				.map(nodeId -> {
					long arrivalTime = nodeArrivals.getOrDefault(nodeId, 0L);
					Long completionTime = nodeCompletions.get(nodeId);
					long nodeLatency = latencyByNodeId.getOrDefault(nodeId, 0L);

					long processingStartTime = completionTime != null
							? completionTime - nodeLatency : arrivalTime;
					long queueTime = processingStartTime - arrivalTime;
					long processingTime = completionTime != null ? nodeLatency : 0L;

					return NodeLatencyBreakdown.builder()
							.nodeId(nodeId)
							.queueTime(queueTime)
							.processingTime(processingTime)
							.build();
				})
				.toList();
	}

	private List<String> deduplicate(List<String> hops) {
		List<String> result = new ArrayList<>();
		for (String hop : hops) {
			if (result.isEmpty() || !result.get(result.size() - 1).equals(hop)) {
				result.add(hop);
			}
		}
		return List.copyOf(result);
	}
}
