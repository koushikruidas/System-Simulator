package com.koushik.systemSimulator.simulation.engine;

import com.koushik.systemSimulator.simulation.metrics.InMemoryMetricsCollector;
import com.koushik.systemSimulator.simulation.model.EventType;
import com.koushik.systemSimulator.simulation.model.NodeType;
import com.koushik.systemSimulator.simulation.model.Request;
import com.koushik.systemSimulator.simulation.model.SimulationEvent;
import com.koushik.systemSimulator.simulation.node.CacheNode;
import com.koushik.systemSimulator.simulation.node.DatabaseNode;
import com.koushik.systemSimulator.simulation.node.LoadBalancerNode;
import com.koushik.systemSimulator.simulation.node.NodeRegistry;
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
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CacheNodeSimulationEngineTest {

	@Test
	void allRequestsTerminateAtCacheWhenHitRateIsOne() {
		Topology topology = cacheTopology(1.0, 2L, 10L);
		SimulationReport report = newEngine(topology, 1.0).run(List.of(
				seedEvent("request-1", 0, 0),
				seedEvent("request-2", 0, 1),
				seedEvent("request-3", 0, 2)
		));

		assertEquals(3, report.metrics().completedRequests());
		assertEquals(0, report.metrics().droppedRequests());

		for (RequestRuntimeState state : report.requestStates()) {
			assertEquals(RequestStatus.COMPLETED, state.status());
			// Last hop is cache — request never reached db
			assertEquals("cache", state.currentNodeId());
			assertTrue(state.hopHistory().stream().noneMatch(h -> h.equals("db")),
					"Expected no db hop for request " + state.request().requestId());
		}
	}

	@Test
	void allRequestsForwardToDbWhenHitRateIsZero() {
		Topology topology = cacheTopology(0.0, 2L, 10L);
		SimulationReport report = newEngine(topology, 0.0).run(List.of(
				seedEvent("request-1", 0, 0),
				seedEvent("request-2", 0, 1),
				seedEvent("request-3", 0, 2)
		));

		assertEquals(3, report.metrics().completedRequests());
		assertEquals(0, report.metrics().droppedRequests());

		for (RequestRuntimeState state : report.requestStates()) {
			assertEquals(RequestStatus.COMPLETED, state.status());
			assertEquals("db", state.currentNodeId());
			assertTrue(state.hopHistory().contains("cache"), "Expected cache hop");
			assertTrue(state.hopHistory().contains("db"), "Expected db hop");
		}
	}

	@Test
	void mixedHitRateProducesDeterministicResults() {
		// Error-accumulation at hitRate=0.5: request-1 → MISS (owed=0.5), request-2 → HIT (owed=1.0)
		double hitRate = 0.5;
		Topology topology = cacheTopology(hitRate, 2L, 10L);

		List<SimulationEvent> events = List.of(
				seedEvent("request-1", 0, 0),
				seedEvent("request-2", 0, 1)
		);

		SimulationReport first = newEngine(topology, hitRate).run(events);
		SimulationReport second = newEngine(topology, hitRate).run(events);

		// Deterministic: identical runs produce identical outcomes
		List<String> firstNodes = first.requestStates().stream().map(RequestRuntimeState::currentNodeId).toList();
		List<String> secondNodes = second.requestStates().stream().map(RequestRuntimeState::currentNodeId).toList();
		assertEquals(firstNodes, secondNodes);

		// Verify exact counter-based ordering
		Map<String, String> finalNodeByRequestId = first.requestStates().stream()
				.collect(Collectors.toMap(
						s -> s.request().requestId(),
						RequestRuntimeState::currentNodeId
				));
		assertEquals("db", finalNodeByRequestId.get("request-1"), "1st request is a miss (hitsOwed=0.5 < 1.0)");
		assertEquals("cache", finalNodeByRequestId.get("request-2"), "2nd request is a hit (hitsOwed=1.0)");
	}

	@Test
	void hitPathHasLowerLatencyThanMissPath() {
		long hitLatency = 2L;
		long missLatency = 20L;
		long dbLatency = 10L;
		double hitRate = 0.5;

		Topology topology = new Topology(
				List.of(
						new NodeDefinition("lb", NodeType.LOAD_BALANCER, 0, 0, 0L, "cache"),
						new NodeDefinition("cache", NodeType.CACHE, 5, 5, missLatency, "db", hitRate, hitLatency),
						new NodeDefinition("db", NodeType.DATABASE, 5, 5, dbLatency, null)
				),
				List.of(
						new LinkDefinition("lb", "cache"),
						new LinkDefinition("cache", "db")
				)
		);

		SimulationReport report = newEngine(topology, hitRate).run(List.of(
				seedEvent("request-1", 0, 0),
				seedEvent("request-2", 0, 1),
				seedEvent("request-3", 0, 2),
				seedEvent("request-4", 0, 3),
				seedEvent("request-5", 0, 4)
		));

		for (RequestRuntimeState state : report.requestStates()) {
			long latency = state.completedAt() - state.request().createdAt();
			boolean wasHit = "cache".equals(state.currentNodeId());
			if (wasHit) {
				assertEquals(hitLatency, latency, "Hit latency should equal hitLatency");
			} else {
				assertEquals(missLatency + dbLatency, latency, "Miss latency should equal missLatency + dbLatency");
			}
		}
	}

    @Test
    void zeroRequests_noProcessingOccurs() {
        Topology topology = cacheTopology(0.5, 2L, 10L);

        SimulationReport report = newEngine(topology, 0.5).run(List.of());

        assertEquals(0, report.metrics().completedRequests());
        assertEquals(0, report.metrics().droppedRequests());
        assertTrue(report.requestStates().isEmpty());
    }

    @Test
    void highLoad_cacheStillRespectsHitLogic() {
        double hitRate = 0.7;
        Topology topology = cacheTopology(hitRate, 2L, 10L);

        List<SimulationEvent> events = new java.util.ArrayList<>();
        for (int i = 0; i < 100; i++) {
            events.add(seedEvent("r-" + i, 0, i));
        }

        SimulationReport report = newEngine(topology, hitRate).run(events);

        long cacheHits = report.requestStates().stream()
                .filter(s -> s.currentNodeId().equals("cache"))
                .count();

        assertTrue(cacheHits > 0);
        assertTrue(cacheHits < 100);
    }

    @Test
    void latency_neverNegative_and_monotonic() {
        Topology topology = cacheTopology(0.5, 2L, 10L);

        SimulationReport report = newEngine(topology, 0.5).run(List.of(
                seedEvent("r1", 0, 0),
                seedEvent("r2", 0, 1)
        ));

        for (RequestRuntimeState state : report.requestStates()) {
            long latency = state.completedAt() - state.request().createdAt();
            assertTrue(latency >= 0);
        }
    }

	private SimulationEngine newEngine(Topology topology, double hitRate) {
		Map<String, SimNode> nodes = Map.of(
				"lb", new LoadBalancerNode(),
				"cache", new CacheNode(hitRate, topology.nodeDefinitions().stream()
						.filter(d -> d.nodeId().equals("cache")).findFirst().orElseThrow().hitLatency()),
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

	private Topology cacheTopology(double hitRate, long hitLatency, long missLatency) {
		return new Topology(
				List.of(
						new NodeDefinition("lb", NodeType.LOAD_BALANCER, 0, 0, 0L, "cache"),
						new NodeDefinition("cache", NodeType.CACHE, 5, 5, missLatency, "db", hitRate, hitLatency),
						new NodeDefinition("db", NodeType.DATABASE, 5, 5, 5L, null)
				),
				List.of(
						new LinkDefinition("lb", "cache"),
						new LinkDefinition("cache", "db")
				)
		);
	}

	private SimulationEvent seedEvent(String requestId, long timestamp, long sequenceNumber) {
		Request request = new Request(requestId, "READ", timestamp, Map.of());
		return new SimulationEvent(requestId + "-seed", timestamp, sequenceNumber,
				EventType.REQUEST_ARRIVED, request, "client", "lb");
	}
}
