package com.xiafan.ai.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** OpenAPI / Swagger UI metadata for the mcp-skill-service API. */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI mcpSkillServiceOpenApi() {
        return new OpenAPI().info(new Info()
                .title("mcp-skill-service API")
                .description("MCP and skill capability service for blog-agent conversations")
                .version("v0.1"));
    }
}