package com.xiafan.agent.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** OpenAPI / Swagger UI metadata for the blog-agent API. */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI blogAgentOpenApi() {
        return new OpenAPI().info(new Info()
                .title("blog-agent API")
                .description("Java Spring Boot port of fastApiProject (agent, RAG and knowledge-base service powered by agentscope)")
                .version("v0.1"));
    }
}