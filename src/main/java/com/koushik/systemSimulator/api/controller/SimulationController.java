package com.koushik.systemSimulator.api.controller;

import com.koushik.systemSimulator.api.dto.request.SimulationRequest;
import com.koushik.systemSimulator.api.dto.response.SimulationResponse;
import com.koushik.systemSimulator.api.mapper.SimulationRequestMapper;
import com.koushik.systemSimulator.api.mapper.SimulationResponseMapper;
import com.koushik.systemSimulator.application.model.SimulationSummaryReport;
import com.koushik.systemSimulator.application.service.SimulationApplicationService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/simulate")
public class SimulationController {

	private final SimulationApplicationService simulationApplicationService;
	private final SimulationRequestMapper simulationRequestMapper;
	private final SimulationResponseMapper simulationResponseMapper;

	public SimulationController(
			SimulationApplicationService simulationApplicationService,
			SimulationRequestMapper simulationRequestMapper,
			SimulationResponseMapper simulationResponseMapper
	) {
		this.simulationApplicationService = simulationApplicationService;
		this.simulationRequestMapper = simulationRequestMapper;
		this.simulationResponseMapper = simulationResponseMapper;
	}

	@PostMapping
	public ResponseEntity<SimulationResponse> simulate(@Valid @RequestBody SimulationRequest request) {
		SimulationSummaryReport report = simulationApplicationService.simulate(simulationRequestMapper.toCommand(request));
		return ResponseEntity.ok(simulationResponseMapper.toResponse(report));
	}
}
