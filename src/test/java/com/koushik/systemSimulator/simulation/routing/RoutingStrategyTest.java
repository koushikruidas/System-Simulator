package com.koushik.systemSimulator.simulation.routing;

import com.koushik.systemSimulator.simulation.engine.SequenceNumberGenerator;
import com.koushik.systemSimulator.simulation.model.NodeType;
import com.koushik.systemSimulator.simulation.model.Request;
import com.koushik.systemSimulator.simulation.node.NodeExecutionContext;
import com.koushik.systemSimulator.simulation.scenario.NodeDefinition;
import com.koushik.systemSimulator.simulation.scenario.Topology;
import com.koushik.systemSimulator.simulation.state.RuntimeStateStore;
import com.koushik.systemSimulator.simulation.state.StateMutations;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RoutingStrategyTest {

	private static final Request DUMMY_REQUEST = new Request("r1", "HTTP", 0, Map.of());

	@Test
	void roundRobinSelectsInOrder() {
		RoundRobinRoutingStrategy strategy = new RoundRobinRoutingStrategy();
		List<String> candidates = List.of("s1", "s2", "s3");

		assertEquals("s1", strategy.select(candidates, DUMMY_REQUEST, null));
		assertEquals("s2", strategy.select(candidates, DUMMY_REQUEST, null));
		assertEquals("s3", strategy.select(candidates, DUMMY_REQUEST, null));
		assertEquals("s1", strategy.select(candidates, DUMMY_REQUEST, null));
		assertEquals("s2", strategy.select(candidates, DUMMY_REQUEST, null));
		assertEquals("s3", strategy.select(candidates, DUMMY_REQUEST, null));
	}

	@Test
	void roundRobinIsDeterministicWithFreshInstance() {
		List<String> candidates = List.of("s1", "s2");

		RoundRobinRoutingStrategy first = new RoundRobinRoutingStrategy();
		RoundRobinRoutingStrategy second = new RoundRobinRoutingStrategy();

		for (int i = 0; i < 4; i++) {
			assertEquals(
					first.select(candidates, DUMMY_REQUEST, null),
					second.select(candidates, DUMMY_REQUEST, null)
			);
		}
	}

	@Test
	void roundRobinThrowsOnEmptyCandidates() {
		RoundRobinRoutingStrategy strategy = new RoundRobinRoutingStrategy();
		assertThrows(IllegalStateException.class,
				() -> strategy.select(List.of(), DUMMY_REQUEST, null));
	}

	@Test
	void leastConnectionsSelectsIdleNode() {
		List<String> candidates = List.of("s1", "s2");
		NodeExecutionContext context = buildContext(candidates, Map.of("s1", 2, "s2", 0));
		LeastConnectionsRoutingStrategy strategy = new LeastConnectionsRoutingStrategy();

		assertEquals("s2", strategy.select(candidates, DUMMY_REQUEST, context));
	}

	@Test
	void leastConnectionsBreaksTiesOnInsertionOrder() {
		List<String> candidates = List.of("s1", "s2");
		NodeExecutionContext context = buildContext(candidates, Map.of("s1", 1, "s2", 1));
		LeastConnectionsRoutingStrategy strategy = new LeastConnectionsRoutingStrategy();

		assertEquals("s1", strategy.select(candidates, DUMMY_REQUEST, context));
	}

	@Test
	void leastConnectionsThrowsOnEmptyCandidates() {
		LeastConnectionsRoutingStrategy strategy = new LeastConnectionsRoutingStrategy();
		NodeDefinition anchor = new NodeDefinition("anchor", NodeType.SERVICE, 1, 1, 0, null);
		RuntimeStateStore store = new RuntimeStateStore(List.of(anchor));
		Topology topology = new Topology(List.of(anchor), List.of());
		NodeExecutionContext context = new NodeExecutionContext(0L, anchor,
				store.getNodeState("anchor"), topology, store, new SequenceNumberGenerator(0));

		assertThrows(IllegalStateException.class,
				() -> strategy.select(List.of(), DUMMY_REQUEST, context));
	}

	private NodeExecutionContext buildContext(List<String> nodeIds, Map<String, Integer> inFlightByNodeId) {
		List<NodeDefinition> defs = new ArrayList<>();
		for (String id : nodeIds) {
			defs.add(new NodeDefinition(id, NodeType.SERVICE, 10, 10, 0, null));
		}
		NodeDefinition anchor = defs.get(0);
		Topology topology = new Topology(defs, List.of());
		RuntimeStateStore store = new RuntimeStateStore(defs);

		for (Map.Entry<String, Integer> entry : inFlightByNodeId.entrySet()) {
			for (int i = 0; i < entry.getValue(); i++) {
				StateMutations.incrementInFlight(entry.getKey()).apply(store);
			}
		}

		return new NodeExecutionContext(0L, anchor,
				store.getNodeState(anchor.nodeId()), topology, store, new SequenceNumberGenerator(0));
	}
}
