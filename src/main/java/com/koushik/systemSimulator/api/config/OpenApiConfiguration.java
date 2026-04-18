package com.koushik.systemSimulator.api.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfiguration {

	@Bean
	public OpenAPI systemSimulatorOpenApi() {
		return new OpenAPI().info(new Info()
				.title("System Simulator API")
				.description("REST API for running deterministic distributed system simulations.")
				.version("v1")
				.contact(new Contact()
						.name("System Simulator")
                        .url("http://localhost:8080")
						.url("https://github.com/your-org/system-simulator")));
	}
}
