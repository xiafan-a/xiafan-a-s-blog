package com.xiafan.ai.persistence;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.xiafan.ai.mcp.McpToolDefinition;
import com.xiafan.ai.persistence.entity.McpCallLogEntity;
import com.xiafan.ai.persistence.entity.McpServerEntity;
import com.xiafan.ai.persistence.entity.McpToolEntity;
import com.xiafan.ai.persistence.entity.SkillDefinitionEntity;
import com.xiafan.ai.persistence.entity.SkillUsageLogEntity;
import com.xiafan.ai.persistence.entity.ToolDefinitionEntity;
import com.xiafan.ai.persistence.entity.ToolUsageLogEntity;
import com.xiafan.ai.persistence.mapper.McpCallLogMapper;
import com.xiafan.ai.persistence.mapper.McpServerMapper;
import com.xiafan.ai.persistence.mapper.McpToolMapper;
import com.xiafan.ai.persistence.mapper.SkillDefinitionMapper;
import com.xiafan.ai.persistence.mapper.SkillUsageLogMapper;
import com.xiafan.ai.persistence.mapper.ToolDefinitionMapper;
import com.xiafan.ai.persistence.mapper.ToolUsageLogMapper;
import com.xiafan.ai.skill.SkillDefinition;
import com.xiafan.ai.tool.ToolDefinition;
import com.xiafan.ai.tool.ToolParameter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Repository;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Repository
public class RecordRepository {

    private static final Logger log = LoggerFactory.getLogger(RecordRepository.class);
    private static final int MAX_PAGE_SIZE = 100;

    private final SkillDefinitionMapper skillMapper;
    private final McpServerMapper mcpServerMapper;
    private final McpToolMapper mcpToolMapper;
    private final ToolDefinitionMapper toolMapper;
    private final SkillUsageLogMapper skillUsageMapper;
    private final McpCallLogMapper mcpCallMapper;
    private final ToolUsageLogMapper toolUsageMapper;
    private final ObjectMapper om;

    public RecordRepository(SkillDefinitionMapper skillMapper,
                            McpServerMapper mcpServerMapper,
                            McpToolMapper mcpToolMapper,
                            ToolDefinitionMapper toolMapper,
                            SkillUsageLogMapper skillUsageMapper,
                            McpCallLogMapper mcpCallMapper,
                            ToolUsageLogMapper toolUsageMapper,
                            ObjectMapper om) {
        this.skillMapper = skillMapper;
        this.mcpServerMapper = mcpServerMapper;
        this.mcpToolMapper = mcpToolMapper;
        this.toolMapper = toolMapper;
        this.skillUsageMapper = skillUsageMapper;
        this.mcpCallMapper = mcpCallMapper;
        this.toolUsageMapper = toolUsageMapper;
        this.om = om;
    }

    public Optional<Boolean> findSkillEnabled(String name) {
        try {
            SkillDefinitionEntity existing = skillMapper.selectOne(
                    Wrappers.<SkillDefinitionEntity>lambdaQuery().eq(SkillDefinitionEntity::getName, name));
            return existing == null ? Optional.empty() : Optional.of(existing.getEnabled());
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    public void upsertSkill(SkillDefinition skill) {
        SkillDefinitionEntity existing = skillMapper.selectOne(
                Wrappers.<SkillDefinitionEntity>lambdaQuery().eq(SkillDefinitionEntity::getName, skill.name()));
        SkillDefinitionEntity e = new SkillDefinitionEntity();
        e.setName(skill.name());
        e.setDescription(skill.description());
        e.setInstructions(skill.instructions());
        e.setSourcePath(skill.sourcePath());
        e.setEnabled(true);
        if (existing == null) {
            skillMapper.insert(e);
        } else {
            e.setId(existing.getId());
            e.setCreatedAt(existing.getCreatedAt());
            e.setUpdatedAt(LocalDateTime.now());
            skillMapper.updateById(e);
        }
    }

    public void upsertMcpServer(String name, String transport, String endpoint, String version) {
        McpServerEntity existing = mcpServerMapper.selectOne(
                Wrappers.<McpServerEntity>lambdaQuery().eq(McpServerEntity::getName, name));
        McpServerEntity e = new McpServerEntity();
        e.setName(name);
        e.setTransport(transport);
        e.setEndpoint(endpoint);
        e.setVersion(version);
        e.setEnabled(true);
        if (existing == null) {
            mcpServerMapper.insert(e);
        } else {
            e.setId(existing.getId());
            e.setCreatedAt(existing.getCreatedAt());
            e.setUpdatedAt(LocalDateTime.now());
            mcpServerMapper.updateById(e);
        }
    }

    public void upsertMcpTool(McpToolDefinition tool) {
        McpToolEntity existing = mcpToolMapper.selectOne(
                Wrappers.<McpToolEntity>lambdaQuery().eq(McpToolEntity::getName, tool.name()));
        McpToolEntity e = new McpToolEntity();
        e.setName(tool.name());
        e.setDescription(tool.description());
        e.setInputSchema(toJson(tool.inputSchema()));
        e.setEnabled(true);
        if (existing == null) {
            mcpToolMapper.insert(e);
        } else {
            e.setId(existing.getId());
            e.setCreatedAt(existing.getCreatedAt());
            e.setUpdatedAt(LocalDateTime.now());
            mcpToolMapper.updateById(e);
        }
    }

    public void upsertTool(ToolDefinition tool) {
        ToolDefinitionEntity existing = toolMapper.selectOne(
                Wrappers.<ToolDefinitionEntity>lambdaQuery().eq(ToolDefinitionEntity::getName, tool.getName()));
        ToolDefinitionEntity e = fromTool(tool);
        if (existing == null) {
            toolMapper.insert(e);
        } else {
            e.setId(existing.getId());
            e.setCreatedAt(existing.getCreatedAt());
            e.setUpdatedAt(LocalDateTime.now());
            toolMapper.updateById(e);
        }
    }

    public void deleteTool(String name) {
        toolMapper.delete(Wrappers.<ToolDefinitionEntity>lambdaQuery()
                .eq(ToolDefinitionEntity::getName, name)
                .eq(ToolDefinitionEntity::getBuiltIn, false));
        mcpToolMapper.delete(Wrappers.<McpToolEntity>lambdaQuery().eq(McpToolEntity::getName, name));
    }

    public List<ToolDefinition> listToolDefinitions(boolean builtIn) {
        return toolMapper.selectList(Wrappers.<ToolDefinitionEntity>lambdaQuery()
                        .eq(ToolDefinitionEntity::getBuiltIn, builtIn)
                        .orderByAsc(ToolDefinitionEntity::getId))
                .stream()
                .map(this::toTool)
                .toList();
    }

    public void recordSkillUsage(String skillName, String channel, String operation, String userMessage,
                                 boolean success, String error, long durationMs) {
        try {
            SkillUsageLogEntity e = new SkillUsageLogEntity();
            e.setSkillName(skillName);
            e.setChannel(channel);
            e.setOperation(operation);
            e.setUserMessage(userMessage);
            e.setSuccess(success);
            e.setErrorMessage(error);
            e.setDurationMs(durationMs);
            skillUsageMapper.insert(e);
        } catch (Exception e) {
            log.warn("Unable to persist skill usage audit: {}", e.getMessage());
        }
    }

    public void recordMcpCall(String serverName, String toolName, Map<String, Object> arguments,
                              String resultSummary, boolean success, String error, long durationMs) {
        try {
            McpCallLogEntity e = new McpCallLogEntity();
            e.setServerName(serverName);
            e.setToolName(toolName);
            e.setArguments(toJson(arguments));
            e.setResultSummary(resultSummary);
            e.setSuccess(success);
            e.setErrorMessage(error);
            e.setDurationMs(durationMs);
            mcpCallMapper.insert(e);
        } catch (Exception e) {
            log.warn("Unable to persist MCP call audit: {}", e.getMessage());
        }
    }

    public void recordToolUsage(String toolName, String channel, String operation, Map<String, Object> arguments,
                                String resultSummary, boolean success, String error, long durationMs) {
        try {
            ToolUsageLogEntity e = new ToolUsageLogEntity();
            e.setToolName(toolName);
            e.setChannel(channel);
            e.setOperation(operation);
            e.setArguments(toJson(arguments));
            e.setResultSummary(resultSummary);
            e.setSuccess(success);
            e.setErrorMessage(error);
            e.setDurationMs(durationMs);
            toolUsageMapper.insert(e);
        } catch (Exception e) {
            log.warn("Unable to persist tool usage audit: {}", e.getMessage());
        }
    }

    public Map<String, Object> queryToolUsage(String toolName, String channel, Boolean success,
                                              int page, int size) {
        int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        int safePage = Math.max(page, 1);
        int offset = (safePage - 1) * safeSize;
        Long total = toolUsageMapper.selectCount(toolUsageConditions(toolName, channel, success));
        List<Map<String, Object>> items = toolUsageMapper.selectList(
                        toolUsageConditions(toolName, channel, success)
                                .orderByDesc(ToolUsageLogEntity::getId)
                                .last("LIMIT " + safeSize + " OFFSET " + offset))
                .stream()
                .map(this::toToolUsageRow)
                .toList();
        return pageBody(items, safePage, safeSize, total == null ? 0 : total.intValue());
    }

    public Map<String, Object> querySkillUsage(String skillName, String channel, Boolean success,
                                               int page, int size) {
        int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        int safePage = Math.max(page, 1);
        int offset = (safePage - 1) * safeSize;
        Long total = skillUsageMapper.selectCount(skillUsageConditions(skillName, channel, success));
        List<Map<String, Object>> items = skillUsageMapper.selectList(
                        skillUsageConditions(skillName, channel, success)
                                .orderByDesc(SkillUsageLogEntity::getId)
                                .last("LIMIT " + safeSize + " OFFSET " + offset))
                .stream()
                .map(this::toSkillUsageRow)
                .toList();
        return pageBody(items, safePage, safeSize, total == null ? 0 : total.intValue());
    }

    public Map<String, Object> queryMcpCalls(String toolName, String serverName, Boolean success,
                                             int page, int size) {
        int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        int safePage = Math.max(page, 1);
        int offset = (safePage - 1) * safeSize;
        Long total = mcpCallMapper.selectCount(mcpCallConditions(toolName, serverName, success));
        List<Map<String, Object>> items = mcpCallMapper.selectList(
                        mcpCallConditions(toolName, serverName, success)
                                .orderByDesc(McpCallLogEntity::getId)
                                .last("LIMIT " + safeSize + " OFFSET " + offset))
                .stream()
                .map(this::toMcpCallRow)
                .toList();
        return pageBody(items, safePage, safeSize, total == null ? 0 : total.intValue());
    }

    private LambdaQueryWrapper<ToolUsageLogEntity> toolUsageConditions(String toolName, String channel, Boolean success) {
        return Wrappers.<ToolUsageLogEntity>lambdaQuery()
                .eq(notBlank(toolName), ToolUsageLogEntity::getToolName, toolName)
                .eq(notBlank(channel), ToolUsageLogEntity::getChannel, channel)
                .eq(success != null, ToolUsageLogEntity::getSuccess, success);
    }

    private LambdaQueryWrapper<SkillUsageLogEntity> skillUsageConditions(String skillName, String channel, Boolean success) {
        return Wrappers.<SkillUsageLogEntity>lambdaQuery()
                .eq(notBlank(skillName), SkillUsageLogEntity::getSkillName, skillName)
                .eq(notBlank(channel), SkillUsageLogEntity::getChannel, channel)
                .eq(success != null, SkillUsageLogEntity::getSuccess, success);
    }

    private LambdaQueryWrapper<McpCallLogEntity> mcpCallConditions(String toolName, String serverName, Boolean success) {
        return Wrappers.<McpCallLogEntity>lambdaQuery()
                .eq(notBlank(toolName), McpCallLogEntity::getToolName, toolName)
                .eq(notBlank(serverName), McpCallLogEntity::getServerName, serverName)
                .eq(success != null, McpCallLogEntity::getSuccess, success);
    }

    private ToolDefinition toTool(ToolDefinitionEntity e) {
        ToolDefinition tool = new ToolDefinition();
        tool.setId(e.getId());
        tool.setName(e.getName());
        tool.setDisplayName(e.getDisplayName());
        tool.setDescription(e.getDescription());
        tool.setParameters(parseParameters(e.getParameters()));
        tool.setCategory(e.getCategory());
        tool.setEnabled(Boolean.TRUE.equals(e.getEnabled()));
        tool.setRequiresAuth(Boolean.TRUE.equals(e.getRequiresAuth()));
        tool.setTimeout(e.getTimeoutSeconds() == null ? 60 : e.getTimeoutSeconds());
        tool.setApiUrl(e.getApiUrl());
        tool.setApiMethod(e.getApiMethod());
        tool.setApiHeaders(readStringMap(e.getApiHeaders()));
        tool.setAuthType(e.getAuthType());
        tool.setAuthConfig(readMap(e.getAuthConfig()));
        tool.setResponsePath(e.getResponsePath());
        tool.setBuiltIn(Boolean.TRUE.equals(e.getBuiltIn()));
        tool.setCreatedAt(toIso(e.getCreatedAt()));
        tool.setUpdatedAt(toIso(e.getUpdatedAt()));
        return tool;
    }

    private ToolDefinitionEntity fromTool(ToolDefinition tool) {
        ToolDefinitionEntity e = new ToolDefinitionEntity();
        e.setName(tool.getName());
        e.setDisplayName(tool.getDisplayName());
        e.setDescription(tool.getDescription());
        e.setParameters(toJson(tool.getParameters()));
        e.setCategory(tool.getCategory());
        e.setEnabled(tool.isEnabled());
        e.setRequiresAuth(tool.isRequiresAuth());
        e.setTimeoutSeconds(tool.getTimeout());
        e.setApiUrl(tool.getApiUrl());
        e.setApiMethod(tool.getApiMethod());
        e.setApiHeaders(toJson(tool.getApiHeaders()));
        e.setAuthType(tool.getAuthType());
        e.setAuthConfig(toJson(tool.getAuthConfig()));
        e.setResponsePath(tool.getResponsePath());
        e.setBuiltIn(tool.isBuiltIn());
        return e;
    }

    private Map<String, Object> toToolUsageRow(ToolUsageLogEntity e) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", e.getId());
        row.put("tool_name", e.getToolName());
        row.put("channel", e.getChannel());
        row.put("operation", e.getOperation());
        row.put("arguments", e.getArguments());
        row.put("result_summary", e.getResultSummary());
        row.put("success", e.getSuccess());
        row.put("error_message", e.getErrorMessage());
        row.put("duration_ms", e.getDurationMs());
        row.put("created_at", toIso(e.getCreatedAt()));
        return row;
    }

    private Map<String, Object> toSkillUsageRow(SkillUsageLogEntity e) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", e.getId());
        row.put("skill_name", e.getSkillName());
        row.put("channel", e.getChannel());
        row.put("operation", e.getOperation());
        row.put("user_message", e.getUserMessage());
        row.put("success", e.getSuccess());
        row.put("error_message", e.getErrorMessage());
        row.put("duration_ms", e.getDurationMs());
        row.put("created_at", toIso(e.getCreatedAt()));
        return row;
    }

    private Map<String, Object> toMcpCallRow(McpCallLogEntity e) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", e.getId());
        row.put("server_name", e.getServerName());
        row.put("tool_name", e.getToolName());
        row.put("arguments", e.getArguments());
        row.put("result_summary", e.getResultSummary());
        row.put("success", e.getSuccess());
        row.put("error_message", e.getErrorMessage());
        row.put("duration_ms", e.getDurationMs());
        row.put("created_at", toIso(e.getCreatedAt()));
        return row;
    }

    private static Map<String, Object> pageBody(List<Map<String, Object>> items, int page, int size, int total) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("items", items);
        body.put("page", page);
        body.put("size", size);
        body.put("total", total);
        body.put("total_pages", size == 0 ? 0 : (int) Math.ceil(total / (double) size));
        return body;
    }

    private String toJson(Object value) {
        try {
            return om.writeValueAsString(value == null ? Map.of() : value);
        } catch (JacksonException e) {
            return String.valueOf(value);
        }
    }

    private List<ToolParameter> parseParameters(String json) {
        if (json == null || json.isBlank()) {
            return new ArrayList<>();
        }
        try {
            return om.readerForListOf(ToolParameter.class).readValue(json);
        } catch (JacksonException e) {
            log.warn("Unable to parse persisted tool parameters: {}", e.getMessage());
            return new ArrayList<>();
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> readMap(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            Object value = om.readValue(json, Map.class);
            if (value instanceof Map<?, ?> map) {
                return (Map<String, Object>) map;
            }
            return null;
        } catch (JacksonException e) {
            log.warn("Unable to parse persisted tool map: {}", e.getMessage());
            return null;
        }
    }

    private Map<String, String> readStringMap(String json) {
        Map<String, Object> value = readMap(json);
        if (value == null) {
            return null;
        }
        Map<String, String> headers = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : value.entrySet()) {
            headers.put(entry.getKey(), entry.getValue() == null ? null : String.valueOf(entry.getValue()));
        }
        return headers;
    }

    private static String toIso(LocalDateTime value) {
        return value == null ? null : value.toInstant(ZoneOffset.UTC).toString();
    }

    private static boolean notBlank(String value) {
        return value != null && !value.isBlank();
    }
}