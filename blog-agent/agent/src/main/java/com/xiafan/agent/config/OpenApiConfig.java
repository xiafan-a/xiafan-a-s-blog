package com.xiafan.agent.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.Schema;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

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

    /**
     * springdoc generates schema property names from the Java bean names (camelCase), but the API
     * wire format is SNAKE_CASE (spring.jackson.property-naming-strategy). Without this, swagger-ui's
     * "Try it out" sample bodies send camelCase keys that Jackson cannot bind → request fails.
     * Renaming every property (and the matching "required" entries) to snake_case makes the generated
     * examples valid on the wire, so the endpoints are actually testable from the UI.
     */
    @Bean
    public OpenApiCustomizer snakeCaseSchemaCustomizer() {
        return openApi -> {
            if (openApi.getComponents() == null || openApi.getComponents().getSchemas() == null) {
                return;
            }
            openApi.getComponents().getSchemas().forEach((name, schema) -> snakeCase(schema));
        };
    }

    private static void snakeCase(Schema<?> schema) {
        if (schema == null || schema.getProperties() == null) {
            return;
        }
        Map<String, Schema> renamed = new LinkedHashMap<>();
        schema.getProperties().forEach((key, sub) -> {
            renamed.put(toSnake(key), sub);
            snakeCase(sub);
        });
        schema.setProperties(renamed);
        if (schema.getRequired() != null) {
            schema.setRequired(schema.getRequired().stream()
                    .map(OpenApiConfig::toSnake)
                    .collect(Collectors.toList()));
        }
    }

    private static String toSnake(String camel) {
        return camel.replaceAll("([a-z0-9])([A-Z])", "$1_$2").toLowerCase();
    }
}