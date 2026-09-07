package com.xiafan.ai.search;

import com.xiafan.ai.config.McpSearchProperties;
import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.ServerParameters;
import io.modelcontextprotocol.client.transport.StdioClientTransport;
import io.modelcontextprotocol.json.McpJsonDefaults;
import io.modelcontextprotocol.spec.McpSchema;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Component
public class McpWebSearchClient {

    private static final Logger log = LoggerFactory.getLogger(McpWebSearchClient.class);
    private static final String SERVER_NAME = "bing-search";
    private static final int MAX_RESULTS = 20;
    private static final List<String> LIST_KEYS =
            List.of("webPages", "results", "data", "items", "value", "searchResults", "matches");
    private static final List<String> QUERY_KEYS =
            List.of("query", "q", "keyword", "keywords", "search_query", "searchQuery");
    private static final List<String> COUNT_KEYS =
            List.of("count", "num_results", "numResults", "limit", "top_k", "max_results", "page_size", "pageSize", "size");

    private final McpSearchProperties props;
    private final ObjectMapper om;
    private final Object lock = new Object();
    private volatile McpSyncClient client;

    public McpWebSearchClient(McpSearchProperties props, ObjectMapper om) {
        this.props = props;
        this.om = om;
    }

    public synchronized List<Map<String, Object>> search(String query, int numResults) throws Exception {
        if (query == null || query.isBlank()) {
            throw new IllegalArgumentException("query must not be blank");
        }
        int limit = Math.max(1, Math.min(numResults <= 0 ? 5 : numResults, MAX_RESULTS));
        clearStaleInterrupt();
        McpSyncClient connection = retryInterrupted(this::client);
        McpSchema.Tool tool = retryInterrupted(() -> searchTool(connection.listTools()));
        Map<String, Object> arguments = arguments(tool, query, limit);
        McpSchema.CallToolResult response = retryInterrupted(
                () -> connection.callTool(new McpSchema.CallToolRequest(tool.name(), arguments)));
        if (Boolean.TRUE.equals(response.isError())) {
            throw new IllegalStateException("MCP search failed: " + responseText(response));
        }
        return parseResults(response, limit);
    }

    @FunctionalInterface
    private interface McpCall<T> {
        T call() throws Exception;
    }

    private static <T> T retryInterrupted(McpCall<T> action) throws Exception {
        try {
            return action.call();
        } catch (InterruptedException e) {
            Thread.interrupted();
            log.warn("MCP call interrupted, retrying once: {}", e.getMessage());
            return action.call();
        }
    }

    private static void clearStaleInterrupt() {
        if (Thread.interrupted()) {
            log.debug("Cleared stale interrupt before MCP call");
        }
    }

    private McpSyncClient client() throws Exception {
        McpSyncClient current = this.client;
        if (current != null) {
            return current;
        }
        synchronized (lock) {
            if (this.client == null) {
                McpSyncClient created = newClient();
                try {
                    created.initialize();
                } catch (Exception e) {
                    created.close();
                    throw e;
                }
                this.client = created;
            }
            return this.client;
        }
    }

    @PreDestroy
    public void shutdown() {
        McpSyncClient current = this.client;
        this.client = null;
        if (current == null) {
            return;
        }
        try {
            current.close();
        } catch (Exception e) {
            log.debug("MCP client close failed: {}", e.getMessage());
        }
    }

    private McpSyncClient newClient() {
        Duration timeout = Duration.ofSeconds(Math.max(1, props.getTimeoutSeconds()));
        ServerParameters parameters = parameters(props.server(SERVER_NAME));
        StdioClientTransport transport = new StdioClientTransport(parameters, McpJsonDefaults.getMapper());
        return McpClient.sync(transport)
                .clientInfo(new McpSchema.Implementation("mcp-skill-service-web-search", "1.0"))
                .requestTimeout(timeout)
                .initializationTimeout(timeout)
                .build();
    }

    private static ServerParameters parameters(McpSearchProperties.ServerConfig server) {
        String command = server == null || isBlank(server.getCommand()) ? "npx" : server.getCommand();
        List<String> args = server == null || server.getArgs() == null || server.getArgs().isEmpty()
                ? new ArrayList<>(List.of("-y", "bing-cn-mcp")) : new ArrayList<>(server.getArgs());
        if (isWindows() && !isWindowsShell(command)) {
            List<String> wrapped = new ArrayList<>();
            wrapped.add("/c");
            wrapped.add(command);
            wrapped.addAll(args);
            return ServerParameters.builder("cmd.exe").args(wrapped).build();
        }
        return ServerParameters.builder(command).args(args).build();
    }

    private static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
    }

    private static boolean isWindowsShell(String command) {
        String normalized = command.toLowerCase(Locale.ROOT);
        return normalized.equals("cmd")
                || normalized.equals("cmd.exe")
                || normalized.endsWith("\\cmd.exe")
                || normalized.equals("powershell")
                || normalized.equals("powershell.exe")
                || normalized.endsWith("\\powershell.exe");
    }

    private static McpSchema.Tool searchTool(McpSchema.ListToolsResult list) {
        List<McpSchema.Tool> tools = list == null || list.tools() == null ? List.of() : list.tools();
        if (tools.isEmpty()) {
            throw new IllegalStateException("MCP server exposed no search tools");
        }
        for (McpSchema.Tool tool : tools) {
            if (tool.name() != null && tool.name().toLowerCase(Locale.ROOT).contains("search")) {
                return tool;
            }
        }
        return tools.get(0);
    }

    private static Map<String, Object> arguments(McpSchema.Tool tool, String query, int limit) {
        Map<String, Object> properties = schemaProperties(tool == null ? null : tool.inputSchema());
        String queryKey = firstExisting(properties, QUERY_KEYS);
        if (queryKey == null) {
            queryKey = "query";
        }
        String countKey = firstExisting(properties, COUNT_KEYS);
        Map<String, Object> arguments = new LinkedHashMap<>();
        arguments.put(queryKey, query);
        if (countKey != null) {
            arguments.put(countKey, limit);
        }
        return arguments;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> schemaProperties(Map<String, Object> schema) {
        if (schema == null) {
            return Map.of();
        }
        Object properties = schema.get("properties");
        if (properties instanceof Map<?, ?> map) {
            return (Map<String, Object>) map;
        }
        return Map.of();
    }

    private static String firstExisting(Map<String, Object> properties, List<String> candidates) {
        for (String key : candidates) {
            if (properties.containsKey(key)) {
                return key;
            }
        }
        return null;
    }

    private List<Map<String, Object>> parseResults(McpSchema.CallToolResult response, int limit) throws Exception {
        JsonNode structured = toJson(response.structuredContent());
        List<Map<String, Object>> rows = new ArrayList<>();
        if (structured != null && !structured.isNull()) {
            rows = rows(structured, limit);
        }
        String text = responseText(response);
        if (rows.isEmpty() && !text.isBlank()) {
            JsonNode parsed = tryJson(text);
            if (parsed != null) {
                rows = rows(parsed, limit);
            }
        }
        if (rows.isEmpty() && !text.isBlank()) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("title", "");
            row.put("url", "");
            row.put("snippet", text);
            rows.add(row);
        }
        return rows;
    }

    private JsonNode toJson(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return om.readTree(om.writeValueAsString(value));
        } catch (JacksonException e) {
            return null;
        }
    }

    private JsonNode tryJson(String text) {
        String trimmed = text.trim();
        if (!trimmed.startsWith("{") && !trimmed.startsWith("[")) {
            return null;
        }
        try {
            return om.readTree(trimmed);
        } catch (JacksonException e) {
            return null;
        }
    }

    private static List<Map<String, Object>> rows(JsonNode node, int limit) {
        JsonNode list = listNode(node);
        List<Map<String, Object>> rows = new ArrayList<>();
        if (list == null || list.isNull() || list.isMissingNode()) {
            return rows;
        }
        if (list.isArray()) {
            for (JsonNode item : list) {
                if (rows.size() >= limit) {
                    break;
                }
                Map<String, Object> row = normalize(item);
                if (!row.isEmpty()) {
                    rows.add(row);
                }
            }
        } else {
            Map<String, Object> row = normalize(list);
            if (!row.isEmpty()) {
                rows.add(row);
            }
        }
        return rows;
    }

    private static JsonNode listNode(JsonNode node) {
        if (node == null || node.isNull() || node.isMissingNode()) {
            return null;
        }
        if (node.isArray()) {
            return node;
        }
        if (node.isObject()) {
            for (String key : LIST_KEYS) {
                JsonNode candidate = node.get(key);
                if (candidate != null && !candidate.isNull()) {
                    JsonNode nested = listNode(candidate);
                    if (nested != null) {
                        return nested;
                    }
                }
            }
        }
        return node;
    }

    private static Map<String, Object> normalize(JsonNode node) {
        if (node == null || node.isNull() || node.isMissingNode()) {
            return Map.of();
        }
        Map<String, Object> row = new LinkedHashMap<>();
        if (node.isObject()) {
            String title = text(node, "title", "name", "headline");
            String url = text(node, "url", "link", "href", "display_url", "displayUrl", "display_link", "displayLink");
            String snippet = text(node, "snippet", "content", "description", "summary");
            if (title.isBlank() && url.isBlank() && snippet.isBlank()) {
                row.put("content", textOrJson(node));
            } else {
                row.put("title", title);
                row.put("url", url);
                row.put("snippet", snippet);
            }
        } else if (node.isTextual()) {
            row.put("content", node.asText());
        } else {
            row.put("content", node.toString());
        }
        return row;
    }

    private static String textOrJson(JsonNode node) {
        String raw = text(node, "text", "value");
        return raw.isBlank() ? node.toString() : raw;
    }

    private static String text(JsonNode node, String... keys) {
        for (String key : keys) {
            JsonNode value = node.get(key);
            if (value != null && !value.isNull() && !value.isMissingNode() && !value.asText().isBlank()) {
                return value.asText();
            }
        }
        return "";
    }

    private static String responseText(McpSchema.CallToolResult response) {
        if (response.content() == null) {
            return "";
        }
        StringBuilder text = new StringBuilder();
        for (McpSchema.Content content : response.content()) {
            if (content instanceof McpSchema.TextContent textContent && textContent.text() != null) {
                if (!text.isEmpty()) {
                    text.append('\n');
                }
                text.append(textContent.text());
            }
        }
        return text.toString().trim();
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
