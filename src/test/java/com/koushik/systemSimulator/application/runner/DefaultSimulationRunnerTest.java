package com.koushik.systemSimulator.application.runner;

import com.koushik.systemSimulator.application.adapter.DatabaseNodeConfigMapper;
import com.koushik.systemSimulator.application.adapter.LoadBalancerNodeConfigMapper;
import com.koushik.systemSimulator.application.adapter.NodeConfigMapperRegistry;
import com.koushik.systemSimulator.application.adapter.ServiceNodeConfigMapper;
import com.koushik.systemSimulator.application.adapter.SimulationScenarioAdapter;
import com.koushik.systemSimulator.application.builder.ScenarioBuilder;
import com.koushik.systemSimulator.application.factory.DatabaseNodeFactory;
import com.koushik.systemSimulator.application.factory.LeastConnectionsLoadBalancerNodeFactory;
import com.koushik.systemSimulator.application.factory.RoundRobinLoadBalancerNodeFactory;
import com.koushik.systemSimulator.application.factory.ServiceNodeFactory;
import com.koushik.systemSimulator.application.factory.SimNodeFactoryRegistry;
import com.koushik.systemSimulator.application.factory.SimulationEngineFactory;
import com.koushik.systemSimulator.application.model.LbStrategy;
import com.koushik.systemSimulator.application.model.NodeLatencyBreakdown;
import com.koushik.systemSimulator.application.model.RequestOutcome;
import com.koushik.systemSimulator.application.model.RequestTrace;
import com.koushik.systemSimulator.application.model.SimulationCommand;
import com.koushik.systemSimulator.application.model.SimulationResult;
import com.koushik.systemSimulator.application.model.TimeSeriesPoint;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DefaultSimulationRunnerTest {

	private DefaultSimulationRunner buildRunner() {
		return new DefaultSimulationRunner(
				new SimulationScenarioAdapter(new NodeConfigMapperRegistry(List.of(
						new LoadBalancerNodeConfigMapper(),
						new ServiceNodeConfigMapper(),
						new DatabaseNodeConfigMapper()
				))),
				new SimulationEngineFactory(new SimNodeFactoryRegistry(List.of(
						new RoundRobinLoadBalancerNodeFactory(),
						new LeastConnectionsLoadBalancerNodeFactory(),
						new ServiceNodeFactory(),
						new DatabaseNodeFactory()
				))),
				new SimulationResultAssembler()
		);
	}

	/** For simulations with ≤5 requests all traces appear in samples.first. */
	private List<RequestTrace> allSamples(SimulationResult result) {
		List<RequestTrace> all = new ArrayList<>(result.getSamples().getFirst());
		all.addAll(result.getSamples().getSlowest());
		return all;
	}

	@Test
	void runsScenarioAndBuildsSummaryReport() {
		SimulationCommand command = ScenarioBuilder.create()
				.addLoadBalancer("lb", LbStrategy.ROUND_ROBIN, 1, 0, 1)
				.addService("service", 1, 1, 5)
				.addDatabase("db", 1, 1, 10)
				.connect("lb", "service")
				.connect("service", "db")
				.withRequestCount(1)
				.withEntryNode("lb")
				.build();

		SimulationResult report = buildRunner().run(command);

		assertEquals(1, report.getTotalRequests());
		assertEquals(1, report.getSuccessfulRequests());
		assertEquals(0, report.getFailedRequests());
		assertEquals(16.0, report.getAverageLatency());
		assertEquals(1L, report.getNodeMetrics().get("lb").getProcessedRequests());
		assertEquals(1L, report.getNodeMetrics().get("service").getProcessedRequests());
		assertEquals(1L, report.getNodeMetrics().get("db").getProcessedRequests());
	}

	@Test
	void runsRoundRobinLbWithTwoServicesDistributesEvenly() {
		SimulationCommand command = ScenarioBuilder.create()
				.addLoadBalancer("lb", LbStrategy.ROUND_ROBIN, 4, 0, 0)
				.addService("s1", 2, 5, 10)
				.addService("s2", 2, 5, 10)
				.addDatabase("db", 4, 10, 5)
				.connect("lb", "s1")
				.connect("lb", "s2")
				.connect("s1", "db")
				.connect("s2", "db")
				.withRequestCount(4)
				.withEntryNode("lb")
				.build();

		SimulationResult report = buildRunner().run(command);

		assertEquals(4, report.getTotalRequests());
		assertEquals(4, report.getSuccessfulRequests());
		assertEquals(2L, report.getNodeMetrics().get("s1").getProcessedRequests());
		assertEquals(2L, report.getNodeMetrics().get("s2").getProcessedRequests());
	}

	@Test
	void tracksHopPathForCompletedRequest() {
		SimulationCommand command = ScenarioBuilder.create()
				.addLoadBalancer("lb", LbStrategy.ROUND_ROBIN, 1, 0, 1)
				.addService("service", 1, 1, 5)
				.addDatabase("db", 1, 1, 10)
				.connect("lb", "service")
				.connect("service", "db")
				.withRequestCount(1)
				.withEntryNode("lb")
				.build();

		SimulationResult result = buildRunner().run(command);

		// Single request → appears in samples.first
		assertEquals(1, result.getSamples().getFirst().size());
		RequestTrace trace = result.getSamples().getFirst().get(0);
		assertEquals(List.of("lb", "service", "db"), trace.getPath());
		assertEquals(RequestOutcome.COMPLETED, trace.getOutcome());

		// Flow aggregation: single path group
		assertEquals(1, result.getFlowGroups().size());
		assertEquals(List.of("lb", "service", "db"), result.getFlowGroups().get(0).getPath());
		assertEquals("COMPLETED", result.getFlowGroups().get(0).getOutcome());
		assertEquals(1, result.getFlowGroups().get(0).getCount());
	}

	@Test
	void tracksHopPathForAllRequestsWithRoundRobinLb() {
		SimulationCommand command = ScenarioBuilder.create()
				.addLoadBalancer("lb", LbStrategy.ROUND_ROBIN, 4, 0, 0)
				.addService("s1", 2, 5, 10)
				.addService("s2", 2, 5, 10)
				.addDatabase("db", 4, 10, 5)
				.connect("lb", "s1")
				.connect("lb", "s2")
				.connect("s1", "db")
				.connect("s2", "db")
				.withRequestCount(4)
				.withEntryNode("lb")
				.build();

		SimulationResult result = buildRunner().run(command);

		// 4 requests (≤5) → all in samples.first
		List<RequestTrace> traces = result.getSamples().getFirst();
		assertEquals(4, traces.size());
		assertTrue(traces.stream().allMatch(t -> t.getPath().get(0).equals("lb")));
		assertTrue(traces.stream().allMatch(t -> t.getPath().get(2).equals("db")));
		long s1Count = traces.stream().filter(t -> t.getPath().get(1).equals("s1")).count();
		long s2Count = traces.stream().filter(t -> t.getPath().get(1).equals("s2")).count();
		assertEquals(2, s1Count);
		assertEquals(2, s2Count);

		// Flow groups: 2 distinct paths (lb→s1→db and lb→s2→db)
		assertEquals(2, result.getFlowGroups().size());
		result.getFlowGroups().forEach(g -> assertEquals(2, g.getCount()));
	}

	@Test
	void breakdownShowsZeroQueueTimeWhenNoContention() {
		SimulationCommand command = ScenarioBuilder.create()
				.addLoadBalancer("lb", LbStrategy.ROUND_ROBIN, 1, 0, 1)
				.addService("service", 1, 1, 5)
				.addDatabase("db", 1, 1, 10)
				.connect("lb", "service")
				.connect("service", "db")
				.withRequestCount(1)
				.withEntryNode("lb")
				.build();

		SimulationResult result = buildRunner().run(command);

		RequestTrace trace = result.getSamples().getFirst().get(0);
		assertEquals(16, trace.getTotalLatency());
		assertEquals(3, trace.getBreakdown().size());

		NodeLatencyBreakdown lbBreakdown = trace.getBreakdown().get(0);
		assertEquals("lb", lbBreakdown.getNodeId());
		assertEquals(0, lbBreakdown.getQueueTime());
		assertEquals(1, lbBreakdown.getProcessingTime());

		NodeLatencyBreakdown svcBreakdown = trace.getBreakdown().get(1);
		assertEquals("service", svcBreakdown.getNodeId());
		assertEquals(0, svcBreakdown.getQueueTime());
		assertEquals(5, svcBreakdown.getProcessingTime());

		NodeLatencyBreakdown dbBreakdown = trace.getBreakdown().get(2);
		assertEquals("db", dbBreakdown.getNodeId());
		assertEquals(0, dbBreakdown.getQueueTime());
		assertEquals(10, dbBreakdown.getProcessingTime());
	}

	// ── Time-series mode ──────────────────────────────────────────────────────────

	@Test
	void timeBasedMode_totalRequestsIsRateTimesDuration() {
		SimulationCommand command = ScenarioBuilder.create()
				.addLoadBalancer("lb", LbStrategy.ROUND_ROBIN, 10, 0, 1)
				.addService("svc", 10, 10, 5)
				.addDatabase("db", 10, 10, 10)
				.connect("lb", "svc").connect("svc", "db")
				.withTimeSeries(3, 4)
				.withEntryNode("lb")
				.build();

		SimulationResult result = buildRunner().run(command);

		assertEquals(12, result.getTotalRequests());
		assertEquals(12, result.getSuccessfulRequests());
		assertEquals(0, result.getFailedRequests());
	}

	@Test
	void timeBasedMode_timeSeriesIsNonEmptyAndIncomingCountsMatchTotalRequests() {
		SimulationCommand command = ScenarioBuilder.create()
				.addLoadBalancer("lb", LbStrategy.ROUND_ROBIN, 10, 0, 1)
				.addService("svc", 10, 10, 5)
				.addDatabase("db", 10, 10, 10)
				.connect("lb", "svc").connect("svc", "db")
				.withTimeSeries(5, 3)  // 15 total requests
				.withEntryNode("lb")
				.build();

		SimulationResult result = buildRunner().run(command);

		List<TimeSeriesPoint> ts = result.getTimeSeries();
		assertFalse(ts.isEmpty(), "timeSeries must not be empty for time-based runs");

		int totalIncoming = ts.stream().mapToInt(TimeSeriesPoint::getIncoming).sum();
		assertEquals(15, totalIncoming, "sum of incoming across all buckets must equal total requests");

		int totalProcessed = ts.stream().mapToInt(TimeSeriesPoint::getProcessed).sum();
		assertEquals(15, totalProcessed, "sum of processed across all buckets must equal completed requests");
	}

	@Test
	void timeBasedMode_queueDepthBuildUpUnderOverload() {
		// svc capacity=1 latency=20 → 5 arrivals at t=0 cause 4 to queue
		SimulationCommand command = ScenarioBuilder.create()
				.addLoadBalancer("lb", LbStrategy.ROUND_ROBIN, 10, 0, 0)
				.addService("svc", 1, 10, 20)
				.addDatabase("db", 10, 10, 5)
				.connect("lb", "svc").connect("svc", "db")
				.withTimeSeries(5, 2)  // 10 requests total; 5 at t=0, 5 at t=1
				.withEntryNode("lb")
				.build();

		SimulationResult result = buildRunner().run(command);

		// At least one bucket must show svc queue depth > 0
		boolean queueObserved = result.getTimeSeries().stream()
				.anyMatch(p -> p.getQueues().getOrDefault("svc", 0) > 0);
		assertTrue(queueObserved, "svc queue depth should be > 0 in at least one time bucket under overload");
	}

	@Test
	void timeBasedMode_batchModeFallback_timeSeriesIsEmptyWhenAllAtT0() {
		// Batch mode (requestCount) — all requests at t=0, no spread.
		// timeSeries is still populated; verify it contains the right incoming total.
		SimulationCommand command = ScenarioBuilder.create()
				.addLoadBalancer("lb", LbStrategy.ROUND_ROBIN, 10, 0, 1)
				.addService("svc", 10, 10, 5)
				.addDatabase("db", 10, 10, 10)
				.connect("lb", "svc").connect("svc", "db")
				.withRequestCount(3)
				.withEntryNode("lb")
				.build();

		SimulationResult result = buildRunner().run(command);

		// In batch mode all requests are created at t=0 and complete at t=16.
		// buildTimeSeries sees maxTime=16, bucketSize=1, should produce exactly 1 bucket
		// where incoming=3 (all at createdAt=0, which falls in bucket 0).
		List<TimeSeriesPoint> ts = result.getTimeSeries();
		assertFalse(ts.isEmpty());
		int totalIncoming = ts.stream().mapToInt(TimeSeriesPoint::getIncoming).sum();
		assertEquals(3, totalIncoming);
	}

	@Test
	void breakdownShowsQueueTimeWhenCongested() {
		SimulationCommand command = ScenarioBuilder.create()
				.addLoadBalancer("lb", LbStrategy.ROUND_ROBIN, 2, 0, 0)
				.addService("service", 1, 2, 10)
				.addDatabase("db", 2, 2, 5)
				.connect("lb", "service")
				.connect("service", "db")
				.withRequestCount(2)
				.withEntryNode("lb")
				.build();

		SimulationResult result = buildRunner().run(command);

		// Both requests in samples.first (2 ≤ 5)
		RequestTrace r1 = result.getSamples().getFirst().stream()
				.filter(t -> t.getRequestId().equals("request-1")).findFirst().orElseThrow();
		RequestTrace r2 = result.getSamples().getFirst().stream()
				.filter(t -> t.getRequestId().equals("request-2")).findFirst().orElseThrow();

		assertEquals(0, r1.getBreakdown().get(1).getQueueTime());
		assertEquals(10, r2.getBreakdown().get(1).getQueueTime());
	}
}
