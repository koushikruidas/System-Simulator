package com.koushik.systemSimulator.simulation.scheduler;

import com.koushik.systemSimulator.simulation.model.EventType;
import com.koushik.systemSimulator.simulation.model.Request;
import com.koushik.systemSimulator.simulation.model.SimulationEvent;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PriorityQueueEventSchedulerTest {

	@Test
	void ordersEventsByTimestampThenSequenceNumber() {
		PriorityQueueEventScheduler scheduler = new PriorityQueueEventScheduler();
		Request request = new Request("request-1", "READ", 0, Map.of());

		scheduler.schedule(new SimulationEvent("event-3", 10, 3, EventType.REQUEST_ARRIVED, request, null, "lb"));
		scheduler.schedule(new SimulationEvent("event-1", 5, 2, EventType.REQUEST_ARRIVED, request, null, "lb"));
		scheduler.schedule(new SimulationEvent("event-2", 5, 1, EventType.REQUEST_ARRIVED, request, null, "lb"));

		assertEquals("event-2", scheduler.pollNext().eventId());
		assertEquals("event-1", scheduler.pollNext().eventId());
		assertEquals("event-3", scheduler.pollNext().eventId());
	}
}
