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
				    {"id": "lb", "type": "LOAD_BALANCER", "strategy": "ROUND_ROBIN", "capacity": 1, "queueLimit": 0, "latency": 1},
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
				    {"id": "lb", "type": "LOAD_BALANCER", "strategy": "ROUND_ROBIN", "capacity": 1, "queueLimit": 0, "latency": 1},
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
				    {"id": "lb", "type": "LOAD_BALANCER", "strategy": "ROUND_ROBIN", "capacity": 1, "queueLimit": 0, "latency": 1},
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
	void simulatesRoundRobinLoadBalancerWithTwoServices() throws Exception {
		String requestJson = """
				{
				  "requestCount": 4,
				  "entryNodeId": "lb",
				  "nodes": [
				    {"id": "lb",  "type": "LOAD_BALANCER", "strategy": "ROUND_ROBIN", "capacity": 4, "queueLimit": 0, "latency": 0},
				    {"id": "s1",  "type": "SERVICE", "capacity": 2, "queueLimit": 5, "latency": 10},
				    {"id": "s2",  "type": "SERVICE", "capacity": 2, "queueLimit": 5, "latency": 10},
				    {"id": "db",  "type": "DATABASE", "capacity": 4, "queueLimit": 10, "latency": 5}
				  ],
				  "connections": [
				    {"sourceNodeId": "lb", "targetNodeId": "s1"},
				    {"sourceNodeId": "lb", "targetNodeId": "s2"},
				    {"sourceNodeId": "s1", "targetNodeId": "db"},
				    {"sourceNodeId": "s2", "targetNodeId": "db"}
				  ]
				}
				""";

		mockMvc.perform(post("/simulate")
						.contentType(MediaType.APPLICATION_JSON)
						.content(requestJson))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.totalRequests").value(4))
				.andExpect(jsonPath("$.successfulRequests").value(4))
				.andExpect(jsonPath("$.nodeMetrics.s1.processedRequests").value(2))
				.andExpect(jsonPath("$.nodeMetrics.s2.processedRequests").value(2));
	}

	@Test
	void returnsErrorForLoadBalancerWithoutStrategy() throws Exception {
		String requestJson = """
				{
				  "requestCount": 1,
				  "entryNodeId": "lb",
				  "nodes": [
				    {"id": "lb", "type": "LOAD_BALANCER", "capacity": 1, "queueLimit": 0, "latency": 0},
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
				.andExpect(status().isBadRequest());
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
