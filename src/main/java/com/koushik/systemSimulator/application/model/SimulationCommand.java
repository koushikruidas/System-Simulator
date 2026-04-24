package com.koushik.systemSimulator.application.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.Singular;

import java.util.List;

@Getter
@Builder
@AllArgsConstructor
public class SimulationCommand {

	@Singular("node")
	private final List<NodeConfig> nodes;

	@Singular("connection")
	private final List<ConnectionConfig> connections;

	private final int requestCount;

	private final Integer arrivalRate;

	private final Integer simulationDuration;

	private final String entryNodeId;

	private final List<String> entryNodeIds;

	// Set by SimulationRequestMapper for REAL_WORLD mode only; null means TICKS mode (fall back to raw)
	private final Double requestsPerTick;
	private final Integer durationTicks;
	private final boolean realWorldMode;

	public boolean isTimeBased() {
		return arrivalRate != null && simulationDuration != null;
	}

	public int getTotalRequests() {
		return isTimeBased() ? arrivalRate * simulationDuration : requestCount;
	}

	/**
	 * Tick rate for engine injection. REAL_WORLD: requestsPerTick (converted RPS).
	 * TICKS: arrivalRate cast to double. Both null and 0 return 0.0 — the engine injects nothing.
	 * Callers must not treat null and 0 differently; both mean "no arrivals".
	 */
	public double getEffectiveRequestsPerTick() {
		return requestsPerTick != null ? requestsPerTick
				: (arrivalRate != null ? (double) arrivalRate : 0.0);
	}

	/**
	 * Duration in ticks for engine loop. REAL_WORLD: durationTicks (converted seconds).
	 * TICKS: simulationDuration used directly. Both null and 0 return 0 — engine runs zero ticks.
	 */
	public int getEffectiveDurationTicks() {
		return durationTicks != null ? durationTicks
				: (simulationDuration != null ? simulationDuration : 0);
	}
}
