package com.koushik.systemSimulator.api.mapper;

import com.koushik.systemSimulator.api.dto.request.ConnectionRequest;
import com.koushik.systemSimulator.api.dto.request.NodeRequest;
import com.koushik.systemSimulator.api.dto.request.SimulationRequest;
import com.koushik.systemSimulator.application.model.ConnectionConfig;
import com.koushik.systemSimulator.application.model.NodeConfig;
import com.koushik.systemSimulator.application.model.NodeType;
import com.koushik.systemSimulator.application.model.SimulationCommand;
import org.springframework.stereotype.Component;

@Component
public class SimulationRequestMapper {

	public SimulationCommand toCommand(SimulationRequest request) {
		return SimulationCommand.builder()
				.nodes(request.getNodes().stream().map(this::toNodeConfig).toList())
				.connections(request.getConnections().stream().map(this::toConnectionConfig).toList())
				.requestCount(request.getRequestCount())
				.entryNodeId(request.getEntryNodeId())
				.build();
	}

	private NodeConfig toNodeConfig(NodeRequest request) {
		return NodeConfig.builder()
				.nodeId(request.getId())
				.nodeType(NodeType.valueOf(request.getType().name()))
				.capacity(request.getCapacity())
				.queueLimit(request.getQueueLimit())
				.latency(request.getLatency())
				.build();
	}

	private ConnectionConfig toConnectionConfig(ConnectionRequest request) {
		return ConnectionConfig.builder()
				.sourceNodeId(request.getSourceNodeId())
				.targetNodeId(request.getTargetNodeId())
				.build();
	}
}
