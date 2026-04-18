package com.koushik.systemSimulator.simulation.node;

import com.koushik.systemSimulator.simulation.metrics.MetricSignal;
import com.koushik.systemSimulator.simulation.metrics.MetricType;
import com.koushik.systemSimulator.simulation.model.EventType;
import com.koushik.systemSimulator.simulation.model.Request;
import com.koushik.systemSimulator.simulation.model.SimulationEvent;
import com.koushik.systemSimulator.simulation.scenario.NodeDefinition;
import com.koushik.systemSimulator.simulation.state.StateMutations;

public final class ProcessingLoadBalancerNode extends AbstractProcessingNode {

    @Override
    protected void onProcessingCompleted(
            Request request,
            NodeDefinition definition,
            NodeExecutionContext context,
            NodeResult.Builder result
    ) {
        String downstreamNodeId = definition.downstreamNodeId();

        if (downstreamNodeId == null) {
            throw new IllegalStateException(
                    "Load balancer node " + definition.nodeId() + " must define a downstream node"
            );
        }

        // Forward request AFTER processing is complete
        SimulationEvent nextEvent = context.createEvent(
                context.now(), // important: no extra delay here
                EventType.REQUEST_ARRIVED,
                request,
                definition.nodeId(),
                downstreamNodeId
        );

        result.emit(nextEvent)
              .metric(new MetricSignal(
                      MetricType.NODE_PROCESSED,
                      definition.nodeId(),
                      request.requestId(),
                      1
              ));
    }
}