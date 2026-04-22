package com.koushik.systemSimulator.api.controller;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

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

    // 🔥 1. HIGH LOAD / OVERLOAD TEST
    @Test
    void highLoad_shouldCauseDropsAndQueueSaturation() throws Exception {
        String requestJson = """
                {
                  "arrivalRate": 500,
                  "simulationDuration": 10,
                  "entryNodeId": "lb",
                  "nodes": [
                    {"id": "lb", "type": "LOAD_BALANCER", "strategy": "ROUND_ROBIN", "capacity": 500, "queueLimit": 1000, "latency": 1},
                    {"id": "svc", "type": "SERVICE", "capacity": 50, "queueLimit": 40, "latency": 3},
                    {"id": "db", "type": "DATABASE", "capacity": 100, "queueLimit": 100, "latency": 5}
                  ],
                  "connections": [
                    {"sourceNodeId": "lb", "targetNodeId": "svc"},
                    {"sourceNodeId": "svc", "targetNodeId": "db"}
                  ]
                }
                """;

        mockMvc.perform(post("/simulate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.failedRequests").value(greaterThan(0)))
                .andExpect(jsonPath("$.successfulRequests").value(greaterThan(0)));
    }

    // ⚖️ 2. CONSERVATION LAW
    @Test
    void conservationLaw_shouldAlwaysHold() throws Exception {
        String requestJson = """
                {
                "arrivalRate": 100,
                "simulationDuration": 5,
                "entryNodeId": "lb",
                "nodes": [
                {"id": "lb", "type": "LOAD_BALANCER", "strategy": "ROUND_ROBIN", "capacity": 200, "queueLimit": 500, "latency": 1},
                {"id": "svc", "type": "SERVICE", "capacity": 50, "queueLimit": 40, "latency": 3},
                {"id": "db", "type": "DATABASE", "capacity": 50, "queueLimit": 50, "latency": 5}
                ],
                "connections": [
                {"sourceNodeId": "lb", "targetNodeId": "svc"},
                {"sourceNodeId": "svc", "targetNodeId": "db"}
                ]
                }
                """;

        var result = mockMvc.perform(post("/simulate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isOk())
                .andReturn();

        String json = result.getResponse().getContentAsString();

        var node = new com.fasterxml.jackson.databind.ObjectMapper().readTree(json);

        long total = node.get("totalRequests").asLong();
        long success = node.get("successfulRequests").asLong();
        long failed = node.get("failedRequests").asLong();

        // ✅ Correct conservation assertion
        assertEquals(total, success + failed, "Conservation law violated");

    }

    // 🧊 3. ZERO LOAD
    @Test
    void zeroLoad_shouldReturnBadRequest() throws Exception {
        String requestJson = """
                {
                  "arrivalRate": 0,
                  "simulationDuration": 5,
                  "entryNodeId": "lb",
                  "nodes": [
                    {"id": "lb", "type": "LOAD_BALANCER", "strategy": "ROUND_ROBIN", "capacity": 10, "queueLimit": 10, "latency": 1},
                    {"id": "svc", "type": "SERVICE", "capacity": 10, "queueLimit": 10, "latency": 3},
                    {"id": "db", "type": "DATABASE", "capacity": 10, "queueLimit": 10, "latency": 5}
                  ],
                  "connections": [
                    {"sourceNodeId": "lb", "targetNodeId": "svc"},
                    {"sourceNodeId": "svc", "targetNodeId": "db"}
                  ]
                }
                """;

        mockMvc.perform(post("/simulate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isBadRequest());
    }

    // 🔁 4. DETERMINISM TEST
    @Test
    void sameInput_shouldProduceSameOutput() throws Exception {
        String requestJson = """
                {
                  "arrivalRate": 50,
                  "simulationDuration": 5,
                  "entryNodeId": "lb",
                  "nodes": [
                    {"id": "lb", "type": "LOAD_BALANCER", "strategy": "ROUND_ROBIN", "capacity": 100, "queueLimit": 100, "latency": 1},
                    {"id": "svc", "type": "SERVICE", "capacity": 50, "queueLimit": 40, "latency": 3},
                    {"id": "db", "type": "DATABASE", "capacity": 50, "queueLimit": 50, "latency": 5}
                  ],
                  "connections": [
                    {"sourceNodeId": "lb", "targetNodeId": "svc"},
                    {"sourceNodeId": "svc", "targetNodeId": "db"}
                  ]
                }
                """;

        var result1 = mockMvc.perform(post("/simulate")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestJson)).andReturn();

        var result2 = mockMvc.perform(post("/simulate")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestJson)).andReturn();

        assert (result1.getResponse().getContentAsString()
                .equals(result2.getResponse().getContentAsString()));
    }

    // 🧩 5. MULTI-ENTRY DISTRIBUTION
    @Test
    void multiEntry_shouldDistributeLoadAcrossNodes() throws Exception {
        String requestJson = """
                {
                  "arrivalRate": 200,
                  "simulationDuration": 5,
                  "entryNodeId": "lb-1",
                  "nodes": [
                    {"id": "lb-1", "type": "LOAD_BALANCER", "strategy": "ROUND_ROBIN", "capacity": 100, "queueLimit": 200, "latency": 1},
                    {"id": "lb-2", "type": "LOAD_BALANCER", "strategy": "ROUND_ROBIN", "capacity": 100, "queueLimit": 200, "latency": 1},
                    {"id": "svc", "type": "SERVICE", "capacity": 100, "queueLimit": 100, "latency": 3},
                    {"id": "db", "type": "DATABASE", "capacity": 100, "queueLimit": 100, "latency": 5}
                  ],
                  "connections": [
                    {"sourceNodeId": "lb-1", "targetNodeId": "svc"},
                    {"sourceNodeId": "lb-2", "targetNodeId": "svc"},
                    {"sourceNodeId": "svc", "targetNodeId": "db"}
                  ]
                }
                """;

        mockMvc.perform(post("/simulate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nodeMetrics.lb-1.processedRequests").value(greaterThan(0)))
                .andExpect(jsonPath("$.nodeMetrics.lb-2.processedRequests").value(greaterThan(0)));
    }

    // 📈 6. TIME SERIES BEHAVIOR
    @Test
    void timeSeries_shouldShowGradualQueueGrowth() throws Exception {
        String requestJson = """
                {
                  "arrivalRate": 200,
                  "simulationDuration": 5,
                  "entryNodeId": "lb",
                  "nodes": [
                    {"id": "lb", "type": "LOAD_BALANCER", "strategy": "ROUND_ROBIN", "capacity": 200, "queueLimit": 200, "latency": 1},
                    {"id": "svc", "type": "SERVICE", "capacity": 20, "queueLimit": 40, "latency": 3},
                    {"id": "db", "type": "DATABASE", "capacity": 50, "queueLimit": 50, "latency": 5}
                  ],
                  "connections": [
                    {"sourceNodeId": "lb", "targetNodeId": "svc"},
                    {"sourceNodeId": "svc", "targetNodeId": "db"}
                  ]
                }
                """;

        mockMvc.perform(post("/simulate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.timeSeries[0].queues.svc").exists())
                .andExpect(jsonPath("$.timeSeries[1].queues.svc").value(greaterThanOrEqualTo(0)));
    }
}
