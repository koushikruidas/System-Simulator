package com.koushik.systemSimulator.simulation.model;

public enum NodeType {
    LOAD_BALANCER,
    ROUND_ROBIN_LOAD_BALANCER,
    LEAST_CONNECTIONS_LOAD_BALANCER,
	SERVICE,
	DATABASE,
	CACHE
}
