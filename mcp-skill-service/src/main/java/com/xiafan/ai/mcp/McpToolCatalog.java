package com.xiafan.ai.mcp;

import java.util.LinkedHashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class McpToolCatalog {

    public static final String SERVER_NAME = "blog-agent-capabilities";
    public static final String SERVER_VERSION = "1.0.0";

    private McpToolCatalog() {
    }

    public static List<McpToolDefinition> definitions() {
        List<McpToolDefinition> definitions = new ArrayList<>(List.of(
                new McpToolDefinition(
                        "list_skills",
                        "List all available skills",
                        schema(Map.of())),
                new McpToolDefinition(
                        "get_skill",
                        "Get a single skill by name",
                        schema(Map.of(
                                "name", stringProperty("Skill name", true)))),
                new McpToolDefinition(
                        "apply_skill",
                        "Apply a skill to a user message and return a prepared system prompt",
                        schema(Map.of(
                                "name", stringProperty("Skill name", true),
                                "userMessage", stringProperty("User message to apply the skill to", true)))),
                new McpToolDefinition(
                        "list_tools",
                        "List available tools managed by this service",
                        schema(Map.of(
                                "category", stringProperty("Optional tool category filter", false)))),
                new McpToolDefinition(
                        "get_tool",
                        "Get a tool definition by name",
                        schema(Map.of(
                                "name", stringProperty("Tool name", true)))),
                new McpToolDefinition(
                        "execute_tool",
                        "Execute a named tool with JSON arguments",
                        schema(Map.of(
                                "name", stringProperty("Tool name", true),
                                "arguments", objectProperty("JSON arguments accepted by the tool")))),
                new McpToolDefinition(
                        "file_read",
                        "Read a local text file",
                        schema(Map.of(
                                "file_path", stringProperty("Absolute file path", true),
                                "max_chars", integerProperty("Maximum characters to read", false)))),
                new McpToolDefinition(
                        "web_search",
                        "Search the web for current information",
                        schema(Map.of(
                                "query", stringProperty("Search query", true),
                                "num_results", integerProperty("Result count", false)))),
                new McpToolDefinition(
                        "file_write",
                        "Write content to a local text file",
                        schema(Map.of(
                                "file_path", stringProperty("Absolute file path", true),
                                "content", stringProperty("File content", true),
                                "encoding", stringProperty("File encoding", false)))),
                new McpToolDefinition(
                        "knowledge_retrieve",
                        "Retrieve knowledge chunks around a chunk index",
                        schema(Map.of(
                                "knowledge_base_id", integerProperty("Knowledge base id", true),
                                "chunk_index", integerProperty("Reference chunk index", true),
                                "direction", stringProperty("before, after or both", false),
                                "limit", integerProperty("Maximum chunk count", false)))),
                new McpToolDefinition(
                        "web_open",
                        "Open a web page and return its title",
                        schema(Map.of(
                                "url", stringProperty("Web page URL", true)))),
                new McpToolDefinition(
                        "web_scrape",
                        "Fetch and extract text from a web page",
                        schema(Map.of(
                                "url", stringProperty("Web page URL", true),
                                "selector", stringProperty("Optional CSS selector", false)))),
                new McpToolDefinition(
                        "web_click",
                        "Simulate clicking an element by CSS selector",
                        schema(Map.of(
                                "selector", stringProperty("CSS selector", true)))),
                new McpToolDefinition(
                        "web_input",
                        "Simulate typing into an element by CSS selector",
                        schema(Map.of(
                                "selector", stringProperty("CSS selector", true),
                                "value", stringProperty("Text value", true)))),
                new McpToolDefinition(
                        "web_scroll",
                        "Simulate scrolling the active page",
                        schema(Map.of(
                                "direction", enumProperty("Scroll direction", List.of("up", "down"), true),
                                "pixels", integerProperty("Scroll distance in pixels", false)))),
                new McpToolDefinition(
                        "get_Date",
                        "Get the current date",
                        schema(Map.of()))));
        return definitions;
    }

    private static Map<String, Object> schema(Map<String, Map<String, Object>> properties) {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("properties", properties);
        List<String> required = new ArrayList<>();
        for (Map.Entry<String, Map<String, Object>> entry : properties.entrySet()) {
            if (Boolean.TRUE.equals(entry.getValue().get("required"))) {
                required.add(entry.getKey());
            }
        }
        if (!required.isEmpty()) {
            schema.put("required", required);
        }
        return schema;
    }

    private static Map<String, Object> stringProperty(String description, boolean required) {
        return property("string", description, required, null, null);
    }

    private static Map<String, Object> integerProperty(String description, boolean required) {
        return property("integer", description, required, null, null);
    }

    private static Map<String, Object> objectProperty(String description) {
        return property("object", description, true, null, null);
    }

    private static Map<String, Object> enumProperty(String description, List<String> values, boolean required) {
        return property("string", description, required, null, values);
    }

    private static Map<String, Object> property(String type, String description, boolean required,
                                                Object defaultValue, List<String> enumValues) {
        Map<String, Object> property = new LinkedHashMap<>();
        property.put("type", type);
        property.put("description", description);
        property.put("required", required);
        if (defaultValue != null) {
            property.put("default", defaultValue);
        }
        if (enumValues != null) {
            property.put("enum", enumValues);
        }
        return property;
    }
}
