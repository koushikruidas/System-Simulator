package com.koushik.systemSimulator.application.service;

import com.koushik.systemSimulator.api.dto.request.ApiNodeType;
import com.koushik.systemSimulator.application.builder.ScenarioBuilder;
import com.koushik.systemSimulator.application.builder.ScenarioValidationException;
import com.koushik.systemSimulator.application.model.NodeCommand;
import org.springframework.stereotype.Component;

@Component
public class ServiceScenarioConfigurer implements NodeScenarioConfigurer {

	@Override
	public ApiNodeType supportedType() {
		return ApiNodeType.SERVICE;
	}

	@Override
	public void configure(ScenarioBuilder builder, NodeCommand nodeCommand) {
		int capacity = requiredPositive(nodeCommand.capacity(), "Service node " + nodeCommand.nodeId() + " must define a positive capacity");
		int queueLimit = defaultInteger(nodeCommand.queueLimit(), capacity);
		builder.addService(nodeCommand.nodeId(), capacity, queueLimit, defaultLong(nodeCommand.latency(), 0L));
	}

	private int requiredPositive(Integer value, String message) {
		if (value == null || value <= 0) {
			throw new ScenarioValidationException(message);
		}
		return value;
	}

	private int defaultInteger(Integer value, int defaultValue) {
		return value == null ? defaultValue : value;
	}

	private long defaultLong(Long value, long defaultValue) {
		return value == null ? defaultValue : value;
	}
}
