package com.koushik.systemSimulator.application.service;

import com.koushik.systemSimulator.api.dto.request.ApiNodeType;
import com.koushik.systemSimulator.application.builder.ScenarioBuilder;
import com.koushik.systemSimulator.application.model.NodeCommand;
import org.springframework.stereotype.Component;

@Component
public class LoadBalancerScenarioConfigurer implements NodeScenarioConfigurer {

	@Override
	public ApiNodeType supportedType() {
		return ApiNodeType.LOAD_BALANCER;
	}

	@Override
	public void configure(ScenarioBuilder builder, NodeCommand nodeCommand) {
		builder.addLoadBalancer(nodeCommand.nodeId(), defaultLong(nodeCommand.latency(), 0L));
	}

	private long defaultLong(Long value, long defaultValue) {
		return value == null ? defaultValue : value;
	}
}
