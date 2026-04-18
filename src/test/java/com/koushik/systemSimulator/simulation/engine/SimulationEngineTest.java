package com.koushik.systemSimulator.simulation.engine;

import com.koushik.systemSimulator.simulation.metrics.InMemoryMetricsCollector;
import com.koushik.systemSimulator.simulation.metrics.SimulationMetrics;
import com.koushik.systemSimulator.simulation.model.EventType;
import com.koushik.systemSimulator.simulation.model.NodeType;
import com.koushik.systemSimulator.simulation.model.Request;
import com.koushik.systemSimulator.simulation.model.SimulationEvent;
import com.koushik.systemSimulator.simulation.node.DatabaseNode;
import com.koushik.systemSimulator.simulation.node.LoadBalancerNode;
import com.koushik.systemSimulator.simulation.node.NodeRegistry;
import com.koushik.systemSimulator.simulation.node.ServiceNode;
import com.koushik.systemSimulator.simulation.node.SimNode;
import com.koushik.systemSimulator.simulation.scenario.LinkDefinition;
import com.koushik.systemSimulator.simulation.scenario.NodeDefinition;
import com.koushik.systemSimulator.simulation.scenario.Topology;
import com.koushik.systemSimulator.simulation.scheduler.PriorityQueueEventScheduler;
import com.koushik.systemSimulator.simulation.state.RequestRuntimeState;
import com.koushik.systemSimulator.simulation.state.RequestStatus;
import com.koushik.systemSimulator.simulation.state.RuntimeStateStore;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertIterableEquals;

class SimulationEngineTest {

	@Test
	void runsSingleRequestEndToEndDeterministically() {
		SimulationReport firstRun = newEngine(singlePathTopology()).run(List.of(seedEvent("request-1", 0, 0)));
		SimulationReport secondRun = newEngine(singlePathTopology()).run(List.of(seedEvent("request-1", 0, 0)));

		assertEquals(16, firstRun.finalTime());
		assertEquals(5, firstRun.processedEvents().size());
		assertIterableEquals(
				firstRun.processedEvents().stream().map(SimulationEvent::eventId).toList(),
				secondRun.processedEvents().stream().map(SimulationEvent::eventId).toList()
		);

		SimulationMetrics metrics = firstRun.metrics();
		assertEquals(5, metrics.processedEvents());
		assertEquals(1, metrics.completedRequests());
		assertEquals(0, metrics.droppedRequests());
		assertEquals(16, metrics.averageLatency());
		assertEquals(Map.of("lb", 1L, "service", 1L, "db", 1L), metrics.nodeProcessedCounts());

		RequestRuntimeState requestState = firstRun.requestStates().get(0);
		assertEquals(RequestStatus.COMPLETED, requestState.status());
		assertEquals("db", requestState.currentNodeId());
	}

	@Test
	void queuesAndDropsRequestsDeterministicallyWhenCapacityIsExceeded() {
		Topology topology = queuePressureTopology();
		SimulationReport report = newEngine(topology).run(List.of(
				seedEvent("request-1", 0, 0),
				seedEvent("request-2", 0, 1),
				seedEvent("request-3", 0, 2)
		));

		assertEquals(12, report.finalTime());
		assertEquals(2, report.metrics().completedRequests());
		assertEquals(1, report.metrics().droppedRequests());
		assertEquals(1L, report.metrics().nodeDroppedCounts().get("service"));
		assertEquals(List.of(RequestStatus.COMPLETED, RequestStatus.COMPLETED, RequestStatus.DROPPED),
				report.requestStates().stream().map(RequestRuntimeState::status).toList());
	}

	private SimulationEngine newEngine(Topology topology) {
		Map<String, SimNode> nodes = Map.of(
				"lb", new LoadBalancerNode(),
				"service", new ServiceNode(),
				"db", new DatabaseNode()
		);
		return new SimulationEngine(
				new PriorityQueueEventScheduler(),
				new VirtualClock(),
				new NodeRegistry(topology, nodes),
				new RuntimeStateStore(topology.nodeDefinitions()),
				new InMemoryMetricsCollector()
		);
	}

	private Topology singlePathTopology() {
		return new Topology(
				List.of(
						new NodeDefinition("lb", NodeType.DELAY_LOAD_BALANCER, 0, 0, 1, "service"),
						new NodeDefinition("service", NodeType.SERVICE, 1, 1, 5, "db"),
						new NodeDefinition("db", NodeType.DATABASE, 1, 1, 10, null)
				),
				List.of(
						new LinkDefinition("lb", "service"),
						new LinkDefinition("service", "db")
				)
		);
	}

	private Topology queuePressureTopology() {
		return new Topology(
				List.of(
						new NodeDefinition("lb", NodeType.DELAY_LOAD_BALANCER, 0, 0, 0, "service"),
						new NodeDefinition("service", NodeType.SERVICE, 1, 1, 5, "db"),
						new NodeDefinition("db", NodeType.DATABASE, 1, 1, 2, null)
				),
				List.of(
						new LinkDefinition("lb", "service"),
						new LinkDefinition("service", "db")
				)
		);
	}

	private SimulationEvent seedEvent(String requestId, long timestamp, long sequenceNumber) {
		Request request = new Request(requestId, "READ", timestamp, Map.of());
		return new SimulationEvent(requestId + "-seed", timestamp, sequenceNumber, EventType.REQUEST_ARRIVED, request, "client", "lb");
	}
}
