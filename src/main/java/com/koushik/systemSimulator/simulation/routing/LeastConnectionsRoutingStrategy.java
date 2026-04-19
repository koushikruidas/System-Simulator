package com.koushik.systemSimulator.simulation.routing;

import com.koushik.systemSimulator.simulation.model.Request;
import com.koushik.systemSimulator.simulation.node.NodeExecutionContext;

import java.util.Comparator;
import java.util.List;

public final class LeastConnectionsRoutingStrategy implements RoutingStrategy {

	@Override
	public String select(List<String> candidates, Request request, NodeExecutionContext context) {
		if (candidates.isEmpty()) {
			throw new IllegalStateException("No downstream candidates for least-connections selection");
		}
		return candidates.stream()
				.min(Comparator.comparingInt(id -> context.nodeState(id).inFlight()))
				.orElseThrow();
	}
}
