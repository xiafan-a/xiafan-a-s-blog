package com.xiafan.ai.tool;

import com.xiafan.ai.mcp.McpToolDefinition;
import com.xiafan.ai.persistence.RecordRepository;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.function.FunctionToolCallback;
import org.springframework.stereotype.Service;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class ToolRegistryService {

    private static final Logger log = LoggerFactory.getLogger(ToolRegistryService.class);
    private static final DateTimeFormatter TIME = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    private record ToolEntry(ToolDefinition definition, ToolExecutor executor) {
    }

    private final Map<String, ToolEntry> tools = new LinkedHashMap<>();
    private final BuiltinTools builtinTools;
    private final RecordRepository records;
    private final ObjectMapper om;
    private final HttpClient http;

    public ToolRegistryService(BuiltinTools builtinTools, RecordRepository records, ObjectMapper om) {
        this.builtinTools = builtinTools;
        this.records = records;
        this.om = om;
        this.http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    }

    @PostConstruct
    public synchronized void initialize() {
        for (BuiltinTools.Spec spec : builtinTools.all()) {
            ToolDefinition definition = spec.definition();
            tools.put(definition.getName(), new ToolEntry(definition, spec.executor()));
            persistToolQuietly(definition);
        }
        try {
            for (ToolDefinition persisted : records.listToolDefinitions(false)) {
                if (tools.containsKey(persisted.getName())) {
                    continue;
                }
                registerPersisted(persisted);
            }
        } catch (Exception e) {
            log.warn("Unable to load persisted custom tools: {}", e.getMessage());
        }
        log.info("Tool registry initialized: {}", tools.keySet());
    }

    // ============================================ registry API ============================================

    public ToolDefinition registerCustom(ToolCreateRequest request) {
        if (request == null || request.name() == null || request.name().isBlank()) {
            throw new IllegalArgumentException("Tool name must not be blank");
        }
        String name = request.name().trim();
        synchronized (tools) {
            if (tools.containsKey(name)) {
                throw new IllegalArgumentException("Tool '" + name + "' already exists");
            }
            ToolDefinition definition = new ToolDefinition();
            definition.setName(name);
            definition.setDisplayName(blankToDefault(request.display_name(), name));
            definition.setDescription(blankToDefault(request.description(), "Custom API tool " + name));
            definition.setParameters(toParameters(request.parameters()));
            definition.setCategory(blankToDefault(request.category(), "custom"));
            definition.setEnabled(request.enabled() == null || request.enabled());
            definition.setTimeout(request.timeout() == null ? 30 : Math.max(1, request.timeout()));
            definition.setRequiresAuth(!"none".equalsIgnoreCase(request.auth_type()) && request.auth_type() != null);
            definition.setCreatedAt(LocalDateTime.now().format(TIME));
            definition.setUpdatedAt(definition.getCreatedAt());
            definition.setBuiltIn(false);
            definition.setApiUrl(request.api_url());
            definition.setApiMethod(blankToDefault(request.api_method(), "POST"));
            definition.setApiHeaders(request.api_headers());
            definition.setAuthType(blankToDefault(request.auth_type(), "none"));
            definition.setAuthConfig(request.auth_config());
            definition.setResponsePath(request.response_path());
            if (definition.getApiUrl() == null || definition.getApiUrl().isBlank()) {
                throw new IllegalArgumentException("api_url must not be blank");
            }
            ToolExecutor executor = params -> executeApiTool(definition, params);
            tools.put(name, new ToolEntry(definition, executor));
            persistToolQuietly(definition);
            persistMcpToolQuietly(definition);
            return definition;
        }
    }

    public boolean unregister(String name) {
        synchronized (tools) {
            ToolEntry entry = tools.get(name);
            if (entry == null || entry.definition().isBuiltIn()) {
                return false;
            }
            tools.remove(name);
            try {
                records.deleteTool(name);
            } catch (Exception e) {
                log.warn("Unable to delete persisted tool '{}': {}", name, e.getMessage());
            }
            return true;
        }
    }

    public boolean contains(String name) {
        synchronized (tools) {
            return tools.containsKey(name);
        }
    }

    public boolean isBuiltIn(String name) {
        synchronized (tools) {
            ToolEntry entry = tools.get(name);
            return entry != null && entry.definition().isBuiltIn();
        }
    }

    public ToolDefinition getTool(String name) {
        synchronized (tools) {
            ToolEntry entry = tools.get(name);
            return entry == null ? null : entry.definition();
        }
    }

    public List<ToolDefinition> listTools(String category, boolean enabledOnly) {
        List<ToolDefinition> result = new ArrayList<>();
        synchronized (tools) {
            for (ToolEntry entry : tools.values()) {
                ToolDefinition definition = entry.definition();
                if (category != null && !category.isBlank() && !category.equals(definition.getCategory())) {
                    continue;
                }
                if (enabledOnly && !definition.isEnabled()) {
                    continue;
                }
                result.add(definition);
            }
        }
        return result;
    }

    public String[] defaultTools() {
        return listTools("web", true).stream().map(ToolDefinition::getName).toArray(String[]::new);
    }

    public Map<String, List<Map<String, Object>>> listCategories() {
        Map<String, List<Map<String, Object>>> categories = new LinkedHashMap<>();
        for (ToolDefinition definition : listTools(null, false)) {
            List<Map<String, Object>> bucket = categories.computeIfAbsent(
                    definition.getCategory(), key -> new ArrayList<>());
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("name", definition.getName());
            entry.put("display_name", definition.getDisplayName());
            entry.put("description", definition.getDescription());
            bucket.add(entry);
        }
        return categories;
    }

    public ToolResult execute(ToolCall call, String channel) {
        long start = System.nanoTime();
        String callId = call.getCallId() == null || call.getCallId().isBlank()
                ? UUID.randomUUID().toString() : call.getCallId();
        ToolResult result = new ToolResult();
        result.setCallId(callId);
        result.setToolName(call.getToolName());
        result.setSuccess(false);
        result.setExecutionTime(0);
        synchronized (tools) {
            ToolEntry entry = tools.get(call.getToolName());
            if (entry == null) {
                result.setError("Tool '" + call.getToolName() + "' not found");
                recordUsage(result, call.getParameters(), channel, elapsedMs(start));
                return result;
            }
            ToolDefinition definition = entry.definition();
            if (!definition.isEnabled()) {
                result.setError("Tool '" + call.getToolName() + "' is disabled");
                recordUsage(result, call.getParameters(), channel, elapsedMs(start));
                return result;
            }
            try {
                Map<String, Object> params = call.getParameters() == null ? Map.of() : call.getParameters();
                Map<String, Object> validated = validateAndFill(definition, params);
                Object value = entry.executor().execute(validated);
                result.setSuccess(true);
                result.setResult(value);
                result.setExecutionTime(elapsedSeconds(start));
                recordUsage(result, params, channel, elapsedMs(start));
            } catch (Exception e) {
                result.setError(message(e));
                result.setExecutionTime(elapsedSeconds(start));
                recordUsage(result, call.getParameters(), channel, elapsedMs(start));
            }
        }
        return result;
    }

    public ToolCallback[] callbacks() {
        List<ToolCallback> callbacks = new ArrayList<>();
        synchronized (tools) {
            for (ToolEntry entry : tools.values()) {
                if (!entry.definition().isEnabled()) {
                    continue;
                }
                callbacks.add(callback(entry));
            }
        }
        return callbacks.toArray(ToolCallback[]::new);
    }

    // ============================================ MCP callbacks ============================================

    @SuppressWarnings({"unchecked", "rawtypes"})
    private ToolCallback callback(ToolEntry entry) {
        ToolDefinition definition = entry.definition();
        return FunctionToolCallback.builder(definition.getName(),
                        (Map<String, Object> input) -> {
                            ToolCall call = new ToolCall();
                            call.setToolName(definition.getName());
                            call.setParameters(input == null ? Map.of() : input);
                            call.setCallId(UUID.randomUUID().toString());
                            ToolResult result = execute(call, "MCP");
                            if (result.isSuccess()) {
                                return result.getResult();
                            }
                            throw new IllegalStateException(result.getError());
                        })
                .description(definition.getDescription())
                .inputType(Map.class)
                .inputSchema(inputSchema(definition))
                .build();
    }

    private String inputSchema(ToolDefinition definition) {
        try {
            return om.writeValueAsString(inputSchemaMap(definition));
        } catch (JacksonException e) {
            throw new IllegalStateException("Unable to build input schema for " + definition.getName(), e);
        }
    }

    private Map<String, Object> inputSchemaMap(ToolDefinition definition) {
        Map<String, Object> schema = new LinkedHashMap<>();
        Map<String, Object> properties = new LinkedHashMap<>();
        List<String> required = new ArrayList<>();
        if (definition.getParameters() != null) {
            for (ToolParameter parameter : definition.getParameters()) {
                Map<String, Object> property = new LinkedHashMap<>();
                property.put("type", parameter.getType());
                property.put("description", parameter.getDescription() == null ? "" : parameter.getDescription());
                if (parameter.getDefaultValue() != null) {
                    property.put("default", parameter.getDefaultValue());
                }
                if (parameter.getEnumValues() != null && !parameter.getEnumValues().isEmpty()) {
                    property.put("enum", parameter.getEnumValues());
                }
                properties.put(parameter.getName(), property);
                if (parameter.isRequired()) {
                    required.add(parameter.getName());
                }
            }
        }
        schema.put("type", "object");
        schema.put("properties", properties);
        if (!required.isEmpty()) {
            schema.put("required", required);
        }
        return schema;
    }

    // ============================================ custom API executor ============================================

    private Object executeApiTool(ToolDefinition definition, Map<String, Object> params) throws Exception {
        Map<String, String> headers = new HashMap<>();
        if (definition.getApiHeaders() != null) {
            headers.putAll(definition.getApiHeaders());
        }
        if ("bearer".equalsIgnoreCase(definition.getAuthType()) && definition.getAuthConfig() != null) {
            Object token = definition.getAuthConfig().get("token");
            headers.put("Authorization", "Bearer " + (token == null ? "" : token));
        } else if ("api_key".equalsIgnoreCase(definition.getAuthType()) && definition.getAuthConfig() != null) {
            String keyName = definition.getAuthConfig().get("key_name") == null
                    ? "X-API-Key" : String.valueOf(definition.getAuthConfig().get("key_name"));
            Object apiKey = definition.getAuthConfig().get("api_key");
            headers.put(keyName, apiKey == null ? "" : String.valueOf(apiKey));
        }
        Duration timeout = Duration.ofSeconds(Math.max(1, definition.getTimeout()));
        URI uri = buildUri(definition.getApiUrl(), definition.getApiMethod(), params);
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(uri)
                .timeout(timeout)
                .header("Content-Type", "application/json")
                .header("Accept", "application/json,text/plain,*/*");
        headers.forEach(builder::header);
        HttpRequest request;
        if ("GET".equalsIgnoreCase(definition.getApiMethod())) {
            request = builder.GET().build();
        } else {
            String body = om.writeValueAsString(params);
            request = builder.method(definition.getApiMethod().toUpperCase(),
                    HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8)).build();
        }
        HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        String rawBody = response.body();
        Object parsed = parseBody(rawBody);
        if (definition.getResponsePath() != null && !definition.getResponsePath().isBlank()) {
            parsed = traverse(parsed, definition.getResponsePath());
        }
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new RuntimeException("API returned HTTP " + response.statusCode() + ": " + rawBody);
        }
        return parsed;
    }

    private URI buildUri(String apiUrl, String method, Map<String, Object> params) {
        if ("GET".equalsIgnoreCase(method) && params != null && !params.isEmpty()) {
            StringBuilder sb = new StringBuilder(apiUrl);
            char sep = apiUrl.contains("?") ? '&' : '?';
            for (Map.Entry<String, Object> entry : params.entrySet()) {
                sb.append(sep).append(entry.getKey()).append('=')
                        .append(URLEncoder.encode(String.valueOf(entry.getValue()), StandardCharsets.UTF_8));
                sep = '&';
            }
            return URI.create(sb.toString());
        }
        return URI.create(apiUrl);
    }

    private Object parseBody(String body) {
        if (body == null || body.isBlank()) {
            return null;
        }
        try {
            JsonNode node = om.readTree(body);
            return om.convertValue(node, Object.class);
        } catch (JacksonException e) {
            return body;
        }
    }

    private static Object traverse(Object value, String path) {
        Object current = value;
        for (String key : path.split("\\.")) {
            if (key.isBlank()) {
                continue;
            }
            if (current instanceof Map<?, ?> map) {
                current = map.get(key);
                if (current == null) {
                    break;
                }
            } else {
                break;
            }
        }
        return current;
    }

    // ============================================ persistence ============================================

    private void registerPersisted(ToolDefinition definition) {
        definition.setBuiltIn(false);
        ToolExecutor executor = params -> executeApiTool(definition, params);
        synchronized (tools) {
            tools.put(definition.getName(), new ToolEntry(definition, executor));
        }
        persistMcpToolQuietly(definition);
    }

    private void persistToolQuietly(ToolDefinition definition) {
        try {
            records.upsertTool(definition);
        } catch (Exception e) {
            log.warn("Unable to persist tool '{}': {}", definition.getName(), e.getMessage());
        }
    }

    private void persistMcpToolQuietly(ToolDefinition definition) {
        try {
            records.upsertMcpTool(new McpToolDefinition(
                    definition.getName(), definition.getDescription(), inputSchemaMap(definition)));
        } catch (Exception e) {
            log.warn("Unable to persist MCP metadata for tool '{}': {}", definition.getName(), e.getMessage());
        }
    }

    private void recordUsage(ToolResult result, Map<String, Object> arguments, String channel, long durationMs) {
        try {
            records.recordToolUsage(result.getToolName(), channel, "EXECUTE", arguments,
                    summarize(result.getResult()), result.isSuccess(), result.getError(), durationMs);
        } catch (Exception e) {
            log.warn("Unable to persist tool usage audit: {}", e.getMessage());
        }
    }

    private String summarize(Object value) {
        if (value == null) {
            return null;
        }
        try {
            String json = om.writeValueAsString(value);
            return json.length() > 2000 ? json.substring(0, 2000) + "..." : json;
        } catch (JacksonException e) {
            return String.valueOf(value);
        }
    }

    // ============================================ helpers ============================================

    private Map<String, Object> validateAndFill(ToolDefinition definition, Map<String, Object> params) {
        Map<String, Object> validated = new LinkedHashMap<>();
        if (definition.getParameters() != null) {
            for (ToolParameter parameter : definition.getParameters()) {
                if (parameter.isRequired() && !params.containsKey(parameter.getName())) {
                    if (parameter.getDefaultValue() != null) {
                        validated.put(parameter.getName(), parameter.getDefaultValue());
                    } else {
                        throw new IllegalArgumentException("Missing required parameter: " + parameter.getName());
                    }
                }
            }
        }
        for (Map.Entry<String, Object> entry : params.entrySet()) {
            if (knownParameter(definition, entry.getKey())) {
                validated.put(entry.getKey(), entry.getValue());
            } else {
                log.warn("Unknown parameter '{}' for tool '{}'", entry.getKey(), definition.getName());
            }
        }
        if (definition.getParameters() != null) {
            for (ToolParameter parameter : definition.getParameters()) {
                if (!validated.containsKey(parameter.getName()) && parameter.getDefaultValue() != null) {
                    validated.put(parameter.getName(), parameter.getDefaultValue());
                }
            }
        }
        return validated;
    }

    private static boolean knownParameter(ToolDefinition definition, String name) {
        if (definition.getParameters() == null) {
            return false;
        }
        for (ToolParameter parameter : definition.getParameters()) {
            if (parameter.getName() != null && parameter.getName().equals(name)) {
                return true;
            }
        }
        return false;
    }

    private static List<ToolParameter> toParameters(List<Map<String, Object>> raw) {
        List<ToolParameter> params = new ArrayList<>();
        if (raw == null) {
            return params;
        }
        for (Map<String, Object> map : raw) {
            params.add(ToolParameter.fromMap(map));
        }
        return params;
    }

    private static String blankToDefault(String value, String defaultValue) {
        return value == null || value.isBlank() ? defaultValue : value;
    }

    private static String message(Exception e) {
        return e.getMessage() == null ? e.toString() : e.getMessage();
    }

    private static long elapsedMs(long startNanos) {
        return (System.nanoTime() - startNanos) / 1_000_000;
    }

    private static double elapsedSeconds(long startNanos) {
        return (System.nanoTime() - startNanos) / 1_000_000_000.0;
    }
}
