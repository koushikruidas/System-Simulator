package com.koushik.systemSimulator.simulation.routing;

import com.koushik.systemSimulator.simulation.model.Request;
import com.koushik.systemSimulator.simulation.node.NodeExecutionContext;

import java.util.List;

public interface RoutingStrategy {

	String select(List<String> candidates, Request request, NodeExecutionContext context);
}
