package com.koushik.systemSimulator.api.controller;

import com.koushik.systemSimulator.api.dto.request.SimulationRequest;
import com.koushik.systemSimulator.api.dto.response.ApiErrorResponse;
import com.koushik.systemSimulator.api.dto.response.SimulationResponse;
import com.koushik.systemSimulator.api.mapper.SimulationRequestMapper;
import com.koushik.systemSimulator.api.mapper.SimulationResponseMapper;
import com.koushik.systemSimulator.application.model.SimulationCommand;
import com.koushik.systemSimulator.application.model.SimulationResult;
import com.koushik.systemSimulator.application.service.SimulationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/simulate")
@Tag(name = "Simulation", description = "Run deterministic distributed system simulations")
public class SimulationController {

	private final SimulationService simulationService;
	private final SimulationRequestMapper simulationRequestMapper;
	private final SimulationResponseMapper simulationResponseMapper;

	public SimulationController(
			SimulationService simulationService,
			SimulationRequestMapper simulationRequestMapper,
			SimulationResponseMapper simulationResponseMapper
	) {
		this.simulationService = simulationService;
		this.simulationRequestMapper = simulationRequestMapper;
		this.simulationResponseMapper = simulationResponseMapper;
	}

	@PostMapping
	@Operation(
			summary = "Run a simulation",
			description = "Builds a scenario from the request payload, executes it synchronously through the deterministic simulation engine, and returns summary metrics.",
			responses = {
					@ApiResponse(
							responseCode = "200",
							description = "Simulation completed successfully",
							content = @Content(
									schema = @Schema(implementation = SimulationResponse.class),
									examples = @ExampleObject(
											name = "success",
											value = """
													{
													  "totalRequests": 100,
													  "successfulRequests": 95,
													  "failedRequests": 5,
													  "averageLatency": 287.5,
													  "nodeMetrics": {
													    "lb": {
													      "processedRequests": 100,
													      "droppedRequests": 0
													    },
													    "service": {
													      "processedRequests": 95,
													      "droppedRequests": 5
													    },
													    "db": {
													      "processedRequests": 95,
													      "droppedRequests": 0
													    }
													  }
													}
													"""
									)
							)
					),
					@ApiResponse(
							responseCode = "400",
							description = "Validation or topology error",
							content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))
					),
					@ApiResponse(
							responseCode = "500",
							description = "Unexpected server error",
							content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))
					)
			}
	)
	public ResponseEntity<SimulationResponse> simulate(@Valid @RequestBody SimulationRequest request) {
		SimulationCommand command = simulationRequestMapper.toCommand(request);
		SimulationResult result = simulationService.run(command);
		return ResponseEntity.ok(simulationResponseMapper.toResponse(result));
	}
}
