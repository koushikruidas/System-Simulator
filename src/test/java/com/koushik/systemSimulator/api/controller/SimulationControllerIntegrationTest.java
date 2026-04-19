package com.koushik.systemSimulator.api.controller;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
		"spring.autoconfigure.exclude=org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration,"
				+ "org.springframework.boot.orm.jpa.autoconfigure.HibernateJpaAutoConfiguration"
})
class SimulationControllerIntegrationTest {

	private MockMvc mockMvc;

	@Autowired
	private WebApplicationContext webApplicationContext;

	@BeforeEach
	void setUp() {
		mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();
	}

	@Test
	void simulatesScenarioThroughHttpEndpoint() throws Exception {
		String requestJson = """
				{
				  "requestCount": 1,
				  "entryNodeId": "lb",
				  "nodes": [
				    {"id": "lb", "type": "LOAD_BALANCER", "latency": 1},
				    {"id": "service", "type": "SERVICE", "capacity": 1, "queueLimit": 1, "latency": 5},
				    {"id": "db", "type": "DATABASE", "capacity": 1, "queueLimit": 1, "latency": 10}
				  ],
				  "connections": [
				    {"sourceNodeId": "lb", "targetNodeId": "service"},
				    {"sourceNodeId": "service", "targetNodeId": "db"}
				  ]
				}
				""";

		mockMvc.perform(post("/simulate")
						.contentType(MediaType.APPLICATION_JSON)
						.content(requestJson))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.totalRequests").value(1))
				.andExpect(jsonPath("$.successfulRequests").value(1))
				.andExpect(jsonPath("$.failedRequests").value(0))
				.andExpect(jsonPath("$.averageLatency").value(16.0))
				.andExpect(jsonPath("$.nodeMetrics.lb.processedRequests").value(1))
				.andExpect(jsonPath("$.nodeMetrics.service.processedRequests").value(1))
				.andExpect(jsonPath("$.nodeMetrics.db.processedRequests").value(1));
	}

	@Test
	void returnsStructuredValidationErrorForInvalidRequest() throws Exception {
		String requestJson = """
				{
				  "requestCount": 0,
				  "nodes": [],
				  "connections": []
				}
				""";

		mockMvc.perform(post("/simulate")
						.contentType(MediaType.APPLICATION_JSON)
						.content(requestJson))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("INVALID_SIMULATION_REQUEST"))
				.andExpect(jsonPath("$.message").value("Request validation failed"))
				.andExpect(jsonPath("$.path").value("/simulate"))
				.andExpect(jsonPath("$.fieldErrors").isArray());
	}

	@Test
	void returnsFieldErrorForMissingEntryNodeId() throws Exception {
		String requestJson = """
				{
				  "requestCount": 1,
				  "nodes": [
				    {"id": "lb", "type": "LOAD_BALANCER", "latency": 1},
				    {"id": "service", "type": "SERVICE", "capacity": 1, "queueLimit": 1, "latency": 5},
				    {"id": "db", "type": "DATABASE", "capacity": 1, "queueLimit": 1, "latency": 10}
				  ],
				  "connections": [
				    {"sourceNodeId": "lb", "targetNodeId": "service"},
				    {"sourceNodeId": "service", "targetNodeId": "db"}
				  ]
				}
				""";

		mockMvc.perform(post("/simulate")
						.contentType(MediaType.APPLICATION_JSON)
						.content(requestJson))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.fieldErrors[0].field").value("entryNodeId"));
	}

	@Test
	void returnsErrorForUnknownEntryNodeId() throws Exception {
		String requestJson = """
				{
				  "requestCount": 1,
				  "entryNodeId": "ghost",
				  "nodes": [
				    {"id": "lb", "type": "LOAD_BALANCER", "latency": 1},
				    {"id": "service", "type": "SERVICE", "capacity": 1, "queueLimit": 1, "latency": 5},
				    {"id": "db", "type": "DATABASE", "capacity": 1, "queueLimit": 1, "latency": 10}
				  ],
				  "connections": [
				    {"sourceNodeId": "lb", "targetNodeId": "service"},
				    {"sourceNodeId": "service", "targetNodeId": "db"}
				  ]
				}
				""";

		mockMvc.perform(post("/simulate")
						.contentType(MediaType.APPLICATION_JSON)
						.content(requestJson))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message").value("Entry node 'ghost' does not exist in the topology"));
	}

	@Test
	void exposesOpenApiDocumentationAndSwaggerUi() throws Exception {
		mockMvc.perform(get("/v3/api-docs"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.paths['/simulate']").exists())
				.andExpect(jsonPath("$.info.title").value("System Simulator API"));

		mockMvc.perform(get("/swagger-ui.html"))
				.andExpect(status().is3xxRedirection())
				.andExpect(redirectedUrl("/swagger-ui/index.html"));
	}
}
