package com.xiafan.agent.service.agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiafan.agent.config.AppProperties;
import com.xiafan.agent.entity.agent.ToolCall;
import com.xiafan.agent.entity.agent.ToolCreate;
import com.xiafan.agent.entity.agent.ToolDefinition;
import com.xiafan.agent.entity.agent.ToolParameter;
import com.xiafan.agent.entity.agent.ToolResult;
import io.agentscope.core.tool.Toolkit;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Mirrors service/toolRegistryService.py + the in-memory registration in api/agent.py.
 * Holds built-in and custom tool definitions, builds per-request {@link Toolkit}s for the
 * ReAct agent, and executes tools directly for the /agent/tools endpoints.
 */
@Service
public class ToolRegistryService {

    private static final Logger log = LoggerFactory.getLogger(ToolRegistryService.class);
    private static final List<String> BUILT_IN_TOOLS = List.of("file_read", "file_write", "web_search");

    private record ToolEntry(ToolDefinition definition, ToolExecutor executor) {
    }

    private final Map<String, ToolEntry> tools = new LinkedHashMap<>();
    private final BuiltinTools builtinTools;
    private final AppProperties props;
    private final ObjectMapper om;
    private final HttpClient http;

    public ToolRegistryService(BuiltinTools builtinTools, AppProperties props, ObjectMapper om) {
        this.builtinTools = builtinTools;
        this.props = props;
        this.om = om;
        this.http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    }

    @PostConstruct
    public void registerBuiltins() {
        for (BuiltinTools.Spec spec : builtinTools.all()) {
            tools.put(spec.definition().getName(), new ToolEntry(spec.definition(), spec.executor()));
        }
        log.info("Built-in tools registered: {}", tools.keySet());
    }

    // ============================================ registry API ============================================

    /** Registers a custom API tool. Throws if the name already exists. */
    public ToolDefinition registerCustom(ToolCreate request) {
        if (tools.containsKey(request.getName())) {
            throw new IllegalArgumentException("Tool '" + request.getName() + "' already exists");
        }
        ToolDefinition def = new ToolDefinition();
        def.setName(request.getName());
        def.setDisplayName(request.getDisplayName());
        def.setDescription(request.getDescription());
        def.setParameters(toParameters(request.getParameters()));
        def.setCategory(request.getCategory());
        def.setEnabled(request.isEnabled());
        def.setTimeout(request.getTimeout());
        def.setRequiresAuth(!"none".equals(request.getAuthType()) && request.getAuthType() != null);
        def.setCreatedAt(LocalDateTime.now().toString());
        def.setUpdatedAt(LocalDateTime.now().toString());
        def.setId(tools.size() + 1);
        ToolExecutor executor = params -> executeApiTool(request, params);
        tools.put(def.getName(), new ToolEntry(def, executor));
        log.info("Registered custom tool: {}", def.getName());
        return def;
    }

    public boolean unregister(String name) {
        return tools.remove(name) != null;
    }

    public boolean isBuiltIn(String name) {
        return BUILT_IN_TOOLS.contains(name);
    }

    public ToolDefinition getTool(String name) {
        ToolEntry e = tools.get(name);
        return e == null ? null : e.definition();
    }

    public boolean contains(String name) {
        return tools.containsKey(name);
    }

    public List<ToolDefinition> listTools(String category, boolean enabledOnly) {
        List<ToolDefinition> result = new ArrayList<>();
        for (ToolEntry e : tools.values()) {
            ToolDefinition d = e.definition();
            if (category != null && !category.isEmpty() && !category.equals(d.getCategory())) {
                continue;
            }
            if (enabledOnly && !d.isEnabled()) {
                continue;
            }
            result.add(d);
        }
        return result;
    }

    public String[] defaultTools() {
        String configured = props.getAgent().getDefaultTools();
        if (configured != null && !configured.trim().isEmpty()) {
            List<String> names = new ArrayList<>();
            for (String t : configured.split(",")) {
                String name = t.trim();
                if (!name.isEmpty()) {
                    names.add(name);
                }
            }
            return names.toArray(new String[0]);
        }
        return listTools("web", true).stream().map(ToolDefinition::getName).toArray(String[]::new);
    }

    public Map<String, List<Map<String, Object>>> listCategories() {
        Map<String, List<Map<String, Object>>> categories = new LinkedHashMap<>();
        for (ToolDefinition t : listTools(null, false)) {
            List<Map<String, Object>> bucket = categories.computeIfAbsent(t.getCategory(), k -> new ArrayList<>());
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("name", t.getName());
            entry.put("display_name", t.getDisplayName());
            entry.put("description", t.getDescription());
            bucket.add(entry);
        }
        return categories;
    }

    /** Builds a fresh Toolkit containing only the allowed (and enabled) tools. */
    public Toolkit buildToolkit(List<String> allowedToolNames) {
        Toolkit toolkit = new Toolkit();
        for (ToolEntry e : tools.values()) {
            ToolDefinition d = e.definition();
            if (!d.isEnabled()) {
                continue;
            }
            if (allowedToolNames != null && !allowedToolNames.isEmpty() && !allowedToolNames.contains(d.getName())) {
                continue;
            }
            toolkit.registerAgentTool(new ExecutableAgentTool(d, e.executor(), om));
        }
        return toolkit;
    }

    /** Formats the available tools as prompt text (mirrors _format_tools_for_prompt). */
    public String formatToolsForPrompt(List<String> availableTools) {
        List<ToolDefinition> defs = listTools(null, false);
        if (availableTools != null && !availableTools.isEmpty()) {
            defs = defs.stream().filter(t -> availableTools.contains(t.getName())).toList();
        }
        List<String> lines = new ArrayList<>();
        for (ToolDefinition t : defs) {
            List<String> params = new ArrayList<>();
            for (ToolParameter p : t.getParameters()) {
                params.add(p.getName() + "(" + p.getType() + ")");
            }
            lines.add("- " + t.getName() + ": " + t.getDescription() + ", 参数: " + String.join(", ", params));
        }
        return String.join("\n", lines);
    }

    /** Direct execution (mirrors registry.execute) — returns the raw result via a {@link ToolResult}. */
    public ToolResult execute(ToolCall call) {
        long start = System.nanoTime();
        ToolEntry e = tools.get(call.getToolName());
        ToolResult result = new ToolResult();
        result.setCallId(call.getCallId());
        result.setToolName(call.getToolName());
        result.setSuccess(false);
        result.setResult(null);
        result.setError(null);
        result.setExecutionTime(0);
        if (e == null) {
            result.setError("Tool '" + call.getToolName() + "' not found");
            return result;
        }
        if (!e.definition().isEnabled()) {
            result.setError("Tool '" + call.getToolName() + "' is disabled");
            return result;
        }
        try {
            Map<String, Object> params = call.getParameters() == null ? Map.of() : call.getParameters();
            Map<String, Object> validated = new ExecutableAgentTool(e.definition(), e.executor(), om)
                    .validateAndFill(params);
            Object value = e.executor().execute(validated);
            result.setSuccess(true);
            result.setResult(value);
            result.setExecutionTime((System.nanoTime() - start) / 1_000_000_000.0);
        } catch (Exception ex) {
            result.setError(ex.getMessage() == null ? ex.toString() : ex.getMessage());
            result.setExecutionTime((System.nanoTime() - start) / 1_000_000_000.0);
        }
        return result;
    }

    // ============================================ custom API executor ============================================

    private Object executeApiTool(ToolCreate request, Map<String, Object> params) throws Exception {
        Map<String, String> headers = new HashMap<>();
        if (request.getApiHeaders() != null) {
            for (Map.Entry<String, String> e : request.getApiHeaders().entrySet()) {
                headers.put(e.getKey(), e.getValue());
            }
        }
        if ("bearer".equalsIgnoreCase(request.getAuthType()) && request.getAuthConfig() != null) {
            Object token = request.getAuthConfig().get("token");
            headers.put("Authorization", "Bearer " + (token == null ? "" : token));
        } else if ("api_key".equalsIgnoreCase(request.getAuthType()) && request.getAuthConfig() != null) {
            String keyName = request.getAuthConfig().get("key_name") == null
                    ? "X-API-Key" : String.valueOf(request.getAuthConfig().get("key_name"));
            Object apiKey = request.getAuthConfig().get("api_key");
            headers.put(keyName, apiKey == null ? "" : String.valueOf(apiKey));
        }
        Duration timeout = Duration.ofSeconds(Math.max(1, request.getTimeout()));
        UriHolder uri = buildUri(request.getApiUrl(), request.getApiMethod(), params);
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(uri.uri)
                .timeout(timeout)
                .header("Content-Type", "application/json")
                .header("Accept", "application/json,text/plain,*/*");
        headers.forEach(builder::header);
        HttpRequest httpRequest;
        if ("GET".equalsIgnoreCase(request.getApiMethod())) {
            httpRequest = builder.GET().build();
        } else {
            String body = om.writeValueAsString(params);
            httpRequest = builder.method(request.getApiMethod().toUpperCase(),
                    HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8)).build();
        }
        HttpResponse<String> resp = http.send(httpRequest, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        String body = resp.body();
        Object parsed = parseBody(body);
        if (request.getResponsePath() != null && !request.getResponsePath().isEmpty()) {
            parsed = traverse(parsed, request.getResponsePath());
        }
        if (resp.statusCode() < 200 || resp.statusCode() >= 300) {
            throw new RuntimeException("API returned HTTP " + resp.statusCode() + ": " + body);
        }
        return parsed;
    }

    private record UriHolder(URI uri) {
    }

    private UriHolder buildUri(String apiUrl, String method, Map<String, Object> params) throws Exception {
        if ("GET".equalsIgnoreCase(method) && params != null && !params.isEmpty()) {
            StringBuilder sb = new StringBuilder(apiUrl);
            char sep = apiUrl.contains("?") ? '&' : '?';
            for (Map.Entry<String, Object> e : params.entrySet()) {
                sb.append(sep).append(e.getKey()).append('=')
                        .append(URLEncoder.encode(String.valueOf(e.getValue()), StandardCharsets.UTF_8));
                sep = '&';
            }
            return new UriHolder(URI.create(sb.toString()));
        }
        return new UriHolder(URI.create(apiUrl));
    }

    private Object parseBody(String body) {
        if (body == null || body.isEmpty()) {
            return null;
        }
        try {
            JsonNode node = om.readTree(body);
            return om.convertValue(node, Object.class);
        } catch (Exception e) {
            String trimmed = body.trim();
            if (trimmed.startsWith("\"")) {
                try {
                    return om.readValue(trimmed, String.class);
                } catch (Exception ignored) {
                    // fall through
                }
            }
            return body;
        }
    }

    private static Object traverse(Object parsed, String path) {
        Object current = parsed;
        for (String key : path.split("\\.")) {
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

    private static List<ToolParameter> toParameters(List<Map<String, Object>> raw) {
        List<ToolParameter> params = new ArrayList<>();
        if (raw == null) {
            return params;
        }
        for (Map<String, Object> m : raw) {
            ToolParameter p = new ToolParameter();
            p.setName(String.valueOf(m.getOrDefault("name", "")));
            p.setType(String.valueOf(m.getOrDefault("type", "string")));
            p.setDescription(String.valueOf(m.getOrDefault("description", "")));
            p.setRequired(!Boolean.FALSE.equals(m.get("required")));
            if (m.containsKey("default")) {
                p.setDefaultValue(m.get("default"));
            } else if (m.containsKey("default_value")) {
                p.setDefaultValue(m.get("default_value"));
            }
            if (m.get("enum") instanceof List<?> enums) {
                List<String> values = new ArrayList<>();
                for (Object o : enums) {
                    values.add(String.valueOf(o));
                }
                p.setEnumValues(values);
            }
            params.add(p);
        }
        return params;
    }
}