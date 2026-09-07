package com.xiafan.ai.mcp;

import com.xiafan.ai.persistence.RecordRepository;
import com.xiafan.ai.tool.ToolCall;
import com.xiafan.ai.tool.ToolDefinition;
import com.xiafan.ai.tool.ToolRegistryService;
import com.xiafan.ai.tool.ToolResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;

@Component
public class McpToolManagementTools {

    private static final Logger log = LoggerFactory.getLogger(McpToolManagementTools.class);

    private final ToolRegistryService registry;
    private final RecordRepository records;
    private final ObjectMapper om;

    public McpToolManagementTools(ToolRegistryService registry, RecordRepository records, ObjectMapper om) {
        this.registry = registry;
        this.records = records;
        this.om = om;
    }

    @Tool(description = "List available tools managed by this service")
    public String list_tools(
            @ToolParam(description = "Optional tool category filter", required = false) String category) {
        return execute("list_tools", auditArgs("category", category), () -> {
            List<ToolDefinition> tools = registry.listTools(category, false);
            List<Map<String, Object>> items = new ArrayList<>();
            for (ToolDefinition tool : tools) {
                items.add(tool.toMap());
            }
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("category", category);
            body.put("tools", items);
            body.put("count", items.size());
            return toJson(body);
        });
    }

    @Tool(description = "Get a tool definition by name")
    public String get_tool(
            @ToolParam(description = "Tool name") String name) {
        return execute("get_tool", auditArgs("name", name), () -> {
            ToolDefinition tool = registry.getTool(name);
            if (tool == null) {
                throw new IllegalArgumentException("Tool '" + name + "' not found");
            }
            return toJson(tool.toMap());
        });
    }

    @Tool(description = "Execute a named tool with JSON arguments")
    public String execute_tool(
            @ToolParam(description = "Tool name") String name,
            @ToolParam(description = "JSON arguments accepted by the tool", required = false) Map<String, Object> arguments) {
        ToolCall call = new ToolCall();
        call.setToolName(name);
        call.setParameters(arguments == null ? Map.of() : arguments);
        call.setCallId(UUID.randomUUID().toString());
        Map<String, Object> auditArguments = new LinkedHashMap<>();
        auditArguments.put("name", name);
        auditArguments.put("arguments", arguments);
        return execute("execute_tool", auditArguments, () ->
                toJson(registry.execute(call, "MCP").toMap()));
    }

    private static Map<String, Object> auditArgs(String key, Object value) {
        Map<String, Object> arguments = new LinkedHashMap<>();
        arguments.put(key, value);
        return arguments;
    }

    private <T> T execute(String toolName, Map<String, Object> arguments, Supplier<T> action) {
        long start = System.nanoTime();
        try {
            T result = action.get();
            records.recordMcpCall(McpToolCatalog.SERVER_NAME, toolName, arguments, summarize(result),
                    true, null, elapsedMs(start));
            return result;
        } catch (Exception e) {
            log.error("MCP tool '{}' execution failed", toolName, e);
            records.recordMcpCall(McpToolCatalog.SERVER_NAME, toolName, arguments, null,
                    false, message(e), elapsedMs(start));
            throw new RuntimeException(message(e), e);
        }
    }

    private String summarize(Object value) {
        if (value == null) {
            return null;
        }
        try {
            String json = om.writeValueAsString(value);
            return json.length() > 2000 ? json.substring(0, 2000) + "..." : json;
        } catch (Exception e) {
            return String.valueOf(value);
        }
    }

    private String toJson(Object value) {
        try {
            return om.writeValueAsString(value);
        } catch (Exception e) {
            throw new IllegalArgumentException("Unable to serialize tool result", e);
        }
    }

    private static String message(Exception e) {
        return e.getMessage() == null ? e.toString() : e.getMessage();
    }

    private static long elapsedMs(long startNanos) {
        return (System.nanoTime() - startNanos) / 1_000_000;
    }
}
