package com.koushik.systemSimulator.application.service;

import com.koushik.systemSimulator.api.dto.request.ApiNodeType;
import com.koushik.systemSimulator.application.builder.ScenarioBuilder;
import com.koushik.systemSimulator.application.model.NodeCommand;

public interface NodeScenarioConfigurer {

	ApiNodeType supportedType();

	void configure(ScenarioBuilder builder, NodeCommand nodeCommand);
}
