package com.koushik.systemSimulator.api.mapper;

import com.koushik.systemSimulator.api.dto.request.ConnectionRequest;
import com.koushik.systemSimulator.api.dto.request.NodeRequest;
import com.koushik.systemSimulator.api.dto.request.SimulationRequest;
import com.koushik.systemSimulator.application.model.ConnectionCommand;
import com.koushik.systemSimulator.application.model.NodeCommand;
import com.koushik.systemSimulator.application.model.SimulationRequestCommand;
import org.springframework.stereotype.Component;

@Component
public class SimulationRequestMapper {

	public SimulationRequestCommand toCommand(SimulationRequest request) {
		return new SimulationRequestCommand(
				request.nodes().stream().map(this::toNodeCommand).toList(),
				request.connections().stream().map(this::toConnectionCommand).toList(),
				request.requestCount()
		);
	}

	private NodeCommand toNodeCommand(NodeRequest request) {
		return new NodeCommand(
				request.id(),
				request.type(),
				request.capacity(),
				request.queueLimit(),
				request.latency()
		);
	}

	private ConnectionCommand toConnectionCommand(ConnectionRequest request) {
		return new ConnectionCommand(request.sourceNodeId(), request.targetNodeId());
	}
}
