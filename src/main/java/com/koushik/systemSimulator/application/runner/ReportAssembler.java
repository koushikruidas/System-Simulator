package com.koushik.systemSimulator.application.runner;

import com.koushik.systemSimulator.application.model.NodeSummaryMetrics;
import com.koushik.systemSimulator.application.model.SimulationScenario;
import com.koushik.systemSimulator.application.model.SimulationSummaryReport;
import com.koushik.systemSimulator.simulation.metrics.SimulationMetrics;
import com.koushik.systemSimulator.simulation.scenario.NodeDefinition;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

@Component
public class ReportAssembler {

	public SimulationSummaryReport assemble(
			SimulationScenario scenario,
			com.koushik.systemSimulator.simulation.engine.SimulationReport engineReport
	) {
		SimulationMetrics metrics = engineReport.metrics();
		Map<String, NodeSummaryMetrics> nodeMetrics = new LinkedHashMap<>();
		for (NodeDefinition nodeDefinition : scenario.topology().nodeDefinitions()) {
			nodeMetrics.put(
					nodeDefinition.nodeId(),
					new NodeSummaryMetrics(
							metrics.nodeProcessedCounts().getOrDefault(nodeDefinition.nodeId(), 0L),
							metrics.nodeDroppedCounts().getOrDefault(nodeDefinition.nodeId(), 0L)
					)
			);
		}
		return new SimulationSummaryReport(
				scenario.requestCount(),
				(int) metrics.completedRequests(),
				(int) metrics.droppedRequests(),
				metrics.averageLatency(),
				Map.copyOf(nodeMetrics)
		);
	}
}
