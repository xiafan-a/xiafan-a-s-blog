package com.xiafan.ai.mcp;

import com.xiafan.ai.persistence.RecordRepository;
import com.xiafan.ai.skill.SkillApplyResponse;
import com.xiafan.ai.skill.SkillRegistry;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;

@Component
public class McpSkillTools {

    private final SkillRegistry registry;
    private final RecordRepository records;
    private final ObjectMapper om;

    public McpSkillTools(SkillRegistry registry, RecordRepository records, ObjectMapper om) {
        this.registry = registry;
        this.records = records;
        this.om = om;
    }

    @Tool(description = "List all available skills")
    public Map<String, Object> list_skills() {
        return execute("list_skills", Map.of(), registry::listSummary);
    }

    @Tool(description = "Get a single skill by name")
    public Map<String, Object> get_skill(
            @ToolParam(description = "Skill name") String name) {
        return execute("get_skill", Map.of("name", name), () -> registry.skillSummary(name));
    }

    @Tool(description = "Apply a skill to a user message and return a prepared system prompt")
    public Map<String, Object> apply_skill(
            @ToolParam(description = "Skill name") String name,
            @ToolParam(description = "User message to apply the skill to") String userMessage) {
        return execute("apply_skill", Map.of("name", name, "userMessage", userMessage), () -> {
            SkillApplyResponse result = registry.apply(name, userMessage, java.util.List.of());
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("skill", result.skill().toMap());
            body.put("system_prompt", result.systemPrompt());
            body.put("prepared_messages", result.preparedMessages());
            return body;
        });
    }

    private <T> T execute(String toolName, Map<String, Object> arguments, Supplier<T> action) {
        long start = System.nanoTime();
        try {
            T result = action.get();
            records.recordMcpCall(McpToolCatalog.SERVER_NAME, toolName, arguments, summarize(result),
                    true, null, elapsedMs(start));
            return result;
        } catch (Exception e) {
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

    private static String message(Exception e) {
        return e.getMessage() == null ? e.toString() : e.getMessage();
    }

    private static long elapsedMs(long startNanos) {
        return (System.nanoTime() - startNanos) / 1_000_000;
    }
}
