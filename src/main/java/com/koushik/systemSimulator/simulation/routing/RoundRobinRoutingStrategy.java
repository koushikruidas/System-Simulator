package com.koushik.systemSimulator.simulation.routing;

import com.koushik.systemSimulator.simulation.model.Request;
import com.koushik.systemSimulator.simulation.node.NodeExecutionContext;

import java.util.List;

public final class RoundRobinRoutingStrategy implements RoutingStrategy {

	private int counter = 0;

	@Override
	public String select(List<String> candidates, Request request, NodeExecutionContext context) {
		if (candidates.isEmpty()) {
			throw new IllegalStateException("No downstream candidates for round-robin selection");
		}
		String selected = candidates.get(counter % candidates.size());
		counter++;
		return selected;
	}
}
