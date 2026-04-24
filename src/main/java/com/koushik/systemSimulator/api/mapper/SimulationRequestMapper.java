package com.koushik.systemSimulator.api.mapper;

import com.koushik.systemSimulator.api.dto.request.ConnectionRequest;
import com.koushik.systemSimulator.api.dto.request.NodeRequest;
import com.koushik.systemSimulator.api.dto.request.SimulationRequest;
import com.koushik.systemSimulator.application.model.ConnectionConfig;
import com.koushik.systemSimulator.application.model.LbStrategy;
import com.koushik.systemSimulator.application.model.NodeConfig;
import com.koushik.systemSimulator.application.model.NodeType;
import com.koushik.systemSimulator.application.model.SimulationCommand;
import com.koushik.systemSimulator.simulation.config.TimeConverter;
import org.springframework.stereotype.Component;

@Component
public class SimulationRequestMapper {

	public SimulationCommand toCommand(SimulationRequest request) {
		boolean realWorld = "REAL_WORLD".equalsIgnoreCase(request.getTimeUnit());
		TimeConverter conv = TimeConverter.defaultConverter();

		SimulationCommand.SimulationCommandBuilder b = SimulationCommand.builder()
				.nodes(request.getNodes().stream().map(this::toNodeConfig).toList())
				.connections(request.getConnections().stream().map(this::toConnectionConfig).toList())
				.requestCount(request.getRequestCount() != null ? request.getRequestCount() : 0)
				.arrivalRate(request.getArrivalRate())
				.simulationDuration(request.getSimulationDuration())
				.entryNodeId(request.getEntryNodeId())
				.realWorldMode(realWorld);

		if (realWorld) {
			if (request.getArrivalRate() != null)
				b.requestsPerTick(conv.rpsToRequestsPerTick(request.getArrivalRate()));
			if (request.getSimulationDuration() != null)
				b.durationTicks(conv.secondsToTicks(request.getSimulationDuration()));
		}
		return b.build();
	}

	private NodeConfig toNodeConfig(NodeRequest request) {
		LbStrategy strategy = request.getStrategy() != null
				? LbStrategy.valueOf(request.getStrategy().name())
				: null;
		return NodeConfig.builder()
				.nodeId(request.getId())
				.nodeType(NodeType.valueOf(request.getType().name()))
				.capacity(request.getCapacity())
				.queueLimit(request.getQueueLimit())
				.latency(request.getLatency())
				.strategy(strategy)
				.hitRate(request.getHitRate())
				.hitLatency(request.getHitLatency())
				.build();
	}

	private ConnectionConfig toConnectionConfig(ConnectionRequest request) {
		return ConnectionConfig.builder()
				.sourceNodeId(request.getSourceNodeId())
				.targetNodeId(request.getTargetNodeId())
				.build();
	}
}
