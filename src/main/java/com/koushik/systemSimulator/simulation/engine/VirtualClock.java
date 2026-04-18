package com.koushik.systemSimulator.simulation.engine;

public final class VirtualClock {

	private long currentTime;

	public long now() {
		return currentTime;
	}

	public void advanceTo(long timestamp) {
		if (timestamp < currentTime) {
			throw new IllegalArgumentException("Virtual time cannot move backwards");
		}
		currentTime = timestamp;
	}
}
