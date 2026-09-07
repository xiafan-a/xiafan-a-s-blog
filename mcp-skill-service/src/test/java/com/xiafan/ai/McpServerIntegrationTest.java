package com.xiafan.ai;

import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.HttpClientStreamableHttpTransport;
import io.modelcontextprotocol.spec.McpSchema;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.client.RestClient;

import java.util.LinkedHashMap;
import java.util.Map;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class McpServerIntegrationTest {

    @LocalServerPort
    private int port;

    @Test
    void servesSkillToolsOverHttpMcp() throws Exception {
        HttpClientStreamableHttpTransport transport =
                HttpClientStreamableHttpTransport.builder("http://127.0.0.1:" + port)
                        .endpoint("/mcp")
                        .build();

        try (McpSyncClient client = McpClient.sync(transport)
                .clientInfo(new McpSchema.Implementation("mcp-skill-service-test", "1.0"))
                .build()) {
            client.initialize();

            McpSchema.ListToolsResult tools = client.listTools();
            assertThat(tools.tools())
                    .extracting(McpSchema.Tool::name)
                    .contains(
                            "list_skills", "get_skill", "apply_skill",
                            "list_tools", "get_tool", "execute_tool",
                            "file_read", "web_search", "file_write", "knowledge_retrieve",
                            "web_open", "web_scrape", "web_click", "web_input",
                            "web_scroll", "get_Date");

            McpSchema.CallToolResult result =
                    client.callTool(new McpSchema.CallToolRequest("list_skills", Map.of()));
            assertThat(result.isError()).isNotEqualTo(true);
            assertThat(String.valueOf(result.content())).contains("chinese-blog-writer");

            McpSchema.CallToolResult toolsResult =
                    client.callTool(new McpSchema.CallToolRequest("list_tools", Map.of()));
            assertThat(toolsResult.isError()).isNotEqualTo(true);
            assertThat(String.valueOf(toolsResult.content())).contains("web_search", "get_Date");
        }

        RestClient rest = RestClient.create("http://127.0.0.1:" + port);
        Map<String, Object> skills = rest.get().uri("/api/v1/skills").retrieve().body(Map.class);
        assertThat(((Number) skills.get("count")).intValue()).isEqualTo(2);
        assertThat(skills.get("skills")).isNotNull();

        Map<String, Object> tools = rest.get().uri("/api/v1/tools").retrieve().body(Map.class);
        assertThat(((Number) tools.get("count")).intValue()).isGreaterThanOrEqualTo(10);
        assertThat(tools.get("tools").toString()).contains("web_search", "get_Date");

        Map<String, Object> dateResult = rest.post()
                .uri("/api/v1/tools/get_Date/execute")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of())
                .retrieve()
                .body(Map.class);
        assertThat(dateResult.get("success")).isEqualTo(true);
        assertThat(dateResult).containsKey("result");

        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
        HttpRequest streamRequest = HttpRequest.newBuilder()
                .uri(URI.create("http://127.0.0.1:" + port + "/api/v1/tools/get_Date/execute/stream"))
                .timeout(Duration.ofSeconds(20))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("{}", StandardCharsets.UTF_8))
                .build();
        String streamBody = httpClient.send(streamRequest, HttpResponse.BodyHandlers.ofString()).body();
        assertThat(streamBody).contains("started", "result", "get_Date", "\"success\":true");

        Map<String, Object> applyRequest = new LinkedHashMap<>();
        applyRequest.put("userMessage", "Write a blog post");
        applyRequest.put("conversationHistory", java.util.List.of());
        Map<String, Object> applied = rest.post()
                .uri("/api/v1/skills/chinese-blog-writer/apply")
                .contentType(MediaType.APPLICATION_JSON)
                .body(applyRequest)
                .retrieve()
                .body(Map.class);
        assertThat(applied).containsKeys("skill", "system_prompt", "prepared_messages");

        String customTool = "integration_custom_" + System.nanoTime();
        Map<String, Object> createRequest = new LinkedHashMap<>();
        createRequest.put("name", customTool);
        createRequest.put("display_name", "Integration custom");
        createRequest.put("description", "Custom tool registration integration test");
        createRequest.put("category", "custom");
        createRequest.put("api_url", "http://127.0.0.1:9/unused");
        createRequest.put("api_method", "GET");
        createRequest.put("auth_type", "none");
        createRequest.put("enabled", true);
        Map<String, Object> created = rest.post()
                .uri("/api/v1/tools")
                .contentType(MediaType.APPLICATION_JSON)
                .body(createRequest)
                .retrieve()
                .body(Map.class);
        assertThat(created.get("success")).isEqualTo(true);

        Map<String, Object> stored = rest.get()
                .uri("/api/v1/tools/" + customTool)
                .retrieve()
                .body(Map.class);
        assertThat(stored.get("name")).isEqualTo(customTool);

        Map<String, Object> deleted = rest.delete()
                .uri("/api/v1/tools/" + customTool)
                .retrieve()
                .body(Map.class);
        assertThat(deleted.get("success")).isEqualTo(true);

        Map<String, Object> mcpCalls = rest.get()
                .uri(uriBuilder -> uriBuilder.path("/api/v1/records/mcp-calls")
                        .queryParam("toolName", "list_skills")
                        .queryParam("serverName", "blog-agent-capabilities")
                        .queryParam("success", "true")
                        .build())
                .retrieve()
                .body(Map.class);
        assertThat(((Number) mcpCalls.get("total")).intValue()).isGreaterThanOrEqualTo(1);
    }
}
