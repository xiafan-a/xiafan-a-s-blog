package com.xiafan.agent.service.agent;

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

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Remote-backed tool facade for blog-agent. Definitions, persistence, execution, and audit
 * records now live in mcp-skill-service; this registry only caches tool metadata so AgentScope
 * can expose the same tools to the model and forward each call over HTTP.
 */
@Service
public class ToolRegistryService {

    private static final Logger log = LoggerFactory.getLogger(ToolRegistryService.class);

    private record ToolEntry(ToolDefinition definition, ToolExecutor executor) {
    }

    private final Map<String, ToolEntry> tools = new LinkedHashMap<>();
    private final CapabilityServiceClient capability;
    private final AppProperties props;
    private final ObjectMapper om;
    private boolean loadAttempted;

    public ToolRegistryService(CapabilityServiceClient capability, AppProperties props, ObjectMapper om) {
        this.capability = capability;
        this.props = props;
        this.om = om;
    }

    @PostConstruct
    public synchronized void initialize() {
        refresh();
    }

    // ============================================ registry API ============================================

    public synchronized boolean refresh() {
        if (!capability.isEnabled()) {
            log.warn("Capability service is disabled; no tools will be exposed to the agent");
            return false;
        }
        loadAttempted = true;
        try {
            List<Map<String, Object>> remote = capability.listTools();
            Map<String, ToolEntry> loaded = new LinkedHashMap<>();
            for (Map<String, Object> item : remote) {
                ToolDefinition definition = definitionFrom(item);
                if (definition.getName() == null || definition.getName().isBlank()) {
                    continue;
                }
                loaded.put(definition.getName(), new ToolEntry(definition, remoteExecutor(definition)));
            }
            synchronized (tools) {
                tools.clear();
                tools.putAll(loaded);
            }
            log.info("Tool registry loaded {} tool(s) from mcp-skill-service", tools.size());
            return !tools.isEmpty();
        } catch (Exception e) {
            log.warn("Unable to load tools from mcp-skill-service: {}",
                    e.getMessage() == null ? e.toString() : e.getMessage());
            loadAttempted = false;
            return false;
        }
    }

    private void ensureLoaded() {
        synchronized (tools) {
            if (tools.isEmpty() && !loadAttempted) {
                refresh();
            }
        }
    }

    /** Registers a custom API tool in the centralized service. Throws if the name already exists. */
    public synchronized ToolDefinition registerCustom(ToolCreate request) {
        ensureLoaded();
        if (request == null || request.getName() == null || request.getName().isBlank()) {
            throw new IllegalArgumentException("Tool name must not be blank");
        }
        String name = request.getName().trim();
        if (tools.containsKey(name)) {
            throw new IllegalArgumentException("Tool '" + name + "' already exists");
        }
        try {
            Map<String, Object> response = capability.registerTool(request);
            Map<String, Object> remote = asMap(response.get("tool"));
            ToolDefinition definition = definitionFrom(remote == null ? capability.getTool(name) : remote);
            synchronized (tools) {
                tools.put(name, new ToolEntry(definition, remoteExecutor(definition)));
            }
            return definition;
        } catch (CapabilityServiceException e) {
            throw new IllegalArgumentException(e.getMessage() == null ? e.toString() : e.getMessage());
        }
    }

    public synchronized boolean unregister(String name) {
        ensureLoaded();
        ToolEntry entry = tools.get(name);
        if (entry == null || entry.definition().isBuiltIn()) {
            return false;
        }
        try {
            Map<String, Object> response = capability.unregisterTool(name);
            tools.remove(name);
            return Boolean.TRUE.equals(response.get("success"));
        } catch (CapabilityServiceException e) {
            if (e.getStatusCode() == 404) {
                tools.remove(name);
                return false;
            }
            throw e;
        }
    }

    public synchronized boolean contains(String name) {
        ensureLoaded();
        return tools.containsKey(name);
    }

    public synchronized boolean isBuiltIn(String name) {
        ensureLoaded();
        ToolEntry entry = tools.get(name);
        return entry != null && entry.definition().isBuiltIn();
    }

    public synchronized ToolDefinition getTool(String name) {
        ensureLoaded();
        ToolEntry entry = tools.get(name);
        return entry == null ? null : entry.definition();
    }

    public synchronized List<ToolDefinition> listTools(String category, boolean enabledOnly) {
        ensureLoaded();
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
        ensureLoaded();
        String configured = props.getAgent().getDefaultTools();
        if (configured != null && !configured.isBlank()) {
            List<String> names = new ArrayList<>();
            for (String raw : configured.split(",")) {
                String name = raw.trim();
                if (!name.isEmpty() && tools.containsKey(name)) {
                    names.add(name);
                }
            }
            if (!names.isEmpty()) {
                return names.toArray(String[]::new);
            }
        }
        return listTools("web", true).stream().map(ToolDefinition::getName).toArray(String[]::new);
    }

    public synchronized Map<String, List<Map<String, Object>>> listCategories() {
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

    /** Builds a fresh AgentScope toolkit backed by remote executors. */
    public synchronized Toolkit buildToolkit(List<String> allowedToolNames) {
        ensureLoaded();
        Toolkit toolkit = new Toolkit();
        synchronized (tools) {
            for (ToolEntry entry : tools.values()) {
                ToolDefinition definition = entry.definition();
                if (!definition.isEnabled()) {
                    continue;
                }
                if (allowedToolNames != null && !allowedToolNames.isEmpty()
                        && !allowedToolNames.contains(definition.getName())) {
                    continue;
                }
                toolkit.registerAgentTool(new ExecutableAgentTool(definition, entry.executor(), om));
            }
        }
        return toolkit;
    }

    /** Formats available tools as prompt text. */
    public String formatToolsForPrompt(List<String> availableTools) {
        List<ToolDefinition> definitions = listTools(null, false);
        if (availableTools != null && !availableTools.isEmpty()) {
            definitions = definitions.stream().filter(tool -> availableTools.contains(tool.getName())).toList();
        }
        List<String> lines = new ArrayList<>();
        for (ToolDefinition definition : definitions) {
            List<String> params = new ArrayList<>();
            for (ToolParameter parameter : definition.getParameters()) {
                params.add(parameter.getName() + "(" + parameter.getType() + ")");
            }
            lines.add("- " + definition.getName() + ": " + definition.getDescription()
                    + ", parameters: " + String.join(", ", params));
        }
        return String.join("\n", lines);
    }

    /** Direct execution by forwarding to the centralized service. */
    public synchronized ToolResult execute(ToolCall call) {
        ensureLoaded();
        ToolResult result = new ToolResult();
        result.setCallId(call.getCallId());
        result.setToolName(call.getToolName());
        result.setSuccess(false);
        result.setResult(null);
        result.setError(null);
        result.setExecutionTime(0);
        if (!tools.containsKey(call.getToolName())) {
            result.setError("Tool '" + call.getToolName() + "' not found");
            return result;
        }
        try {
            Map<String, Object> remote = capability.executeTool(call.getToolName(), call.getParameters());
            result.setCallId(text(remote.get("call_id")));
            result.setSuccess(Boolean.TRUE.equals(remote.get("success")));
            result.setResult(remote.get("result"));
            result.setError(text(remote.get("error")));
            if (remote.get("execution_time") instanceof Number number) {
                result.setExecutionTime(number.doubleValue());
            }
            if (!result.isSuccess() && (result.getError() == null || result.getError().isBlank())) {
                result.setError("Remote tool execution failed");
            }
        } catch (CapabilityServiceException e) {
            result.setError(e.getMessage() == null ? e.toString() : e.getMessage());
        } catch (Exception e) {
            result.setError(e.getMessage() == null ? e.toString() : e.getMessage());
        }
        return result;
    }

    // ============================================ remote mapping ============================================

    private ToolExecutor remoteExecutor(ToolDefinition definition) {
        return parameters -> {
            Map<String, Object> remote = capability.executeTool(definition.getName(), parameters);
            if (!Boolean.TRUE.equals(remote.get("success"))) {
                String error = text(remote.get("error"));
                throw new IllegalStateException(error == null || error.isBlank()
                        ? "Remote tool execution failed" : error);
            }
            return remote.get("result");
        };
    }

    private static ToolDefinition definitionFrom(Map<String, Object> remote) {
        ToolDefinition definition = new ToolDefinition();
        definition.setId(integer(remote.get("id")));
        definition.setName(text(remote.get("name")));
        definition.setDisplayName(text(remote.get("display_name")));
        definition.setDescription(text(remote.get("description")));
        definition.setCategory(text(remote.get("category")));
        definition.setParameters(parameters(remote.get("parameters")));
        definition.setEnabled(!Boolean.FALSE.equals(remote.get("enabled")));
        definition.setRequiresAuth(Boolean.TRUE.equals(remote.get("requires_auth")));
        definition.setTimeout(remote.get("timeout") instanceof Number number ? number.intValue() : 60);
        definition.setCreatedAt(text(remote.get("created_at")));
        definition.setUpdatedAt(text(remote.get("updated_at")));
        definition.setBuiltIn(Boolean.TRUE.equals(remote.get("built_in")));
        return definition;
    }

    private static List<ToolParameter> parameters(Object value) {
        List<ToolParameter> result = new ArrayList<>();
        if (!(value instanceof List<?> raw)) {
            return result;
        }
        for (Object item : raw) {
            if (item instanceof Map<?, ?> map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> typed = (Map<String, Object>) map;
                result.add(ToolParameter.fromMap(typed));
            }
        }
        return result;
    }

    private static Map<String, Object> asMap(Object value) {
        return value instanceof Map<?, ?> map ? castMap(map) : null;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> castMap(Map<?, ?> map) {
        return (Map<String, Object>) map;
    }

    private static String text(Object value) {
        if (value == null) {
            return null;
        }
        String raw = String.valueOf(value);
        return "null".equals(raw) ? null : raw;
    }

    private static Integer integer(Object value) {
        return value instanceof Number number ? number.intValue() : null;
    }
}
