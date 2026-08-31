package com.xiafan.agent.service.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiafan.agent.entity.agent.ToolDefinition;
import com.xiafan.agent.entity.agent.ToolParameter;
import io.agentscope.core.message.ToolResultBlock;
import io.agentscope.core.message.ToolResultState;
import io.agentscope.core.message.ToolUseBlock;
import io.agentscope.core.message.TextBlock;
import io.agentscope.core.tool.AgentTool;
import io.agentscope.core.tool.ToolCallParam;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Generic agentscope {@link AgentTool} backed by a {@link ToolDefinition} and a
 * {@link ToolExecutor}. Mirrors toolRegistryService: parameter validation/default-filling,
 * execution timing, and success/error {@link ToolResultBlock} conversion. Used for both the
 * built-in tools and dynamically registered custom API tools.
 */
public class ExecutableAgentTool implements AgentTool {

    private static final Logger log = LoggerFactory.getLogger(ExecutableAgentTool.class);

    private final ToolDefinition definition;
    private final ToolExecutor executor;
    private final ObjectMapper om;

    public ExecutableAgentTool(ToolDefinition definition, ToolExecutor executor, ObjectMapper om) {
        this.definition = definition;
        this.executor = executor;
        this.om = om;
    }

    public ToolDefinition definition() {
        return definition;
    }

    @Override
    public String getName() {
        return definition.getName();
    }

    @Override
    public String getDescription() {
        return definition.getDescription();
    }

    @Override
    public Map<String, Object> getParameters() {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("type", "object");
        Map<String, Object> properties = new LinkedHashMap<>();
        List<String> required = new ArrayList<>();
        for (ToolParameter p : definition.getParameters()) {
            Map<String, Object> prop = new LinkedHashMap<>();
            prop.put("type", p.getType());
            prop.put("description", p.getDescription() == null ? "" : p.getDescription());
            if (p.getEnumValues() != null && !p.getEnumValues().isEmpty()) {
                prop.put("enum", p.getEnumValues());
            }
            if (p.getDefaultValue() != null) {
                prop.put("default", p.getDefaultValue());
            }
            properties.put(p.getName(), prop);
            if (p.isRequired()) {
                required.add(p.getName());
            }
        }
        params.put("properties", properties);
        params.put("required", required);
        return params;
    }

    @Override
    public Mono<ToolResultBlock> callAsync(ToolCallParam param) {
        return Mono.fromCallable(() -> run(param))
                .onErrorResume(e -> Mono.just(errorBlock(param, e)));
    }

    private ToolResultBlock run(ToolCallParam param) throws Exception {
        String toolId = param.getToolUseBlock() != null ? param.getToolUseBlock().getId() : "";
        Map<String, Object> input = param.getInput() != null ? param.getInput() : Map.of();
        Map<String, Object> validated = validateAndFill(input);
        long start = System.nanoTime();
        Object result = executor.execute(validated);
        double seconds = (System.nanoTime() - start) / 1_000_000_000.0;
        String json = om.writeValueAsString(result);
        return new ToolResultBlock(toolId, definition.getName(),
                List.of(TextBlock.builder().text(json).build()), Map.of("execution_time", seconds),
                ToolResultState.SUCCESS);
    }

    private ToolResultBlock errorBlock(ToolCallParam param, Throwable e) {
        String toolId = param.getToolUseBlock() != null ? param.getToolUseBlock().getId() : "";
        String message = e.getMessage() == null ? e.toString() : e.getMessage();
        return new ToolResultBlock(toolId, definition.getName(),
                List.of(TextBlock.builder().text(message).build()), Map.of(),
                ToolResultState.ERROR);
    }

    /** Mirrors toolRegistryService._validate_parameters: required check, defaults, unknown-param drop. */
    public Map<String, Object> validateAndFill(Map<String, Object> params) {
        Map<String, Object> validated = new LinkedHashMap<>();
        for (ToolParameter p : definition.getParameters()) {
            if (p.isRequired() && !params.containsKey(p.getName())) {
                if (p.getDefaultValue() != null) {
                    validated.put(p.getName(), p.getDefaultValue());
                } else {
                    throw new IllegalArgumentException("Missing required parameter: " + p.getName());
                }
            }
        }
        for (Map.Entry<String, Object> e : params.entrySet()) {
            boolean known = false;
            for (ToolParameter p : definition.getParameters()) {
                if (p.getName().equals(e.getKey())) {
                    known = true;
                    break;
                }
            }
            if (known) {
                validated.put(e.getKey(), e.getValue());
            } else {
                log.warn("Unknown parameter '{}' for tool '{}'", e.getKey(), definition.getName());
            }
        }
        for (ToolParameter p : definition.getParameters()) {
            if (!validated.containsKey(p.getName()) && p.getDefaultValue() != null) {
                validated.put(p.getName(), p.getDefaultValue());
            }
        }
        return validated;
    }
}