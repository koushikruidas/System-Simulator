package com.koushik.systemSimulator.application.runner;

import com.koushik.systemSimulator.application.model.NodeConfig;
import com.koushik.systemSimulator.application.model.NodeMetrics;
import com.koushik.systemSimulator.application.model.SimulationCommand;
import com.koushik.systemSimulator.application.model.SimulationResult;
import com.koushik.systemSimulator.simulation.metrics.SimulationMetrics;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

@Component
public class SimulationResultAssembler {

	public SimulationResult assemble(
			SimulationCommand command,
			com.koushik.systemSimulator.simulation.engine.SimulationReport engineReport
	) {
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
		return SimulationResult.builder()
				.totalRequests(command.getRequestCount())
				.successfulRequests((int) metrics.completedRequests())
				.failedRequests((int) metrics.droppedRequests())
				.averageLatency(metrics.averageLatency())
				.nodeMetrics(nodeMetrics)
				.build();
	}
}
