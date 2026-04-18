package com.koushik.systemSimulator.simulation.engine;

public final class SequenceNumberGenerator {

	private long nextValue;

	public SequenceNumberGenerator(long initialValue) {
		this.nextValue = initialValue;
	}

	public long next() {
		return nextValue++;
	}
}
