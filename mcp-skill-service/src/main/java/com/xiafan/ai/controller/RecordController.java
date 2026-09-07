package com.xiafan.ai.controller;

import com.xiafan.ai.persistence.RecordRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@Tag(name = "Records", description = "Paginated audit records for skill / tool / MCP usage")
@RestController
@RequestMapping("/api/v1/records")
public class RecordController {

    private final RecordRepository records;

    public RecordController(RecordRepository records) {
        this.records = records;
    }

    @Operation(summary = "Query skill usage logs")
    @GetMapping("/skill-usage")
    public Map<String, Object> skillUsage(
            @RequestParam(required = false) String skillName,
            @RequestParam(required = false) String channel,
            @RequestParam(required = false) Boolean success,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        return records.querySkillUsage(skillName, channel, success, page, size);
    }

    @Operation(summary = "Query MCP tool call logs")
    @GetMapping("/mcp-calls")
    public Map<String, Object> mcpCalls(
            @RequestParam(required = false) String toolName,
            @RequestParam(required = false) String serverName,
            @RequestParam(required = false) Boolean success,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        return records.queryMcpCalls(toolName, serverName, success, page, size);
    }

    @Operation(summary = "Query tool usage logs")
    @GetMapping("/tool-usage")
    public Map<String, Object> toolUsage(
            @RequestParam(required = false) String toolName,
            @RequestParam(required = false) String channel,
            @RequestParam(required = false) Boolean success,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        return records.queryToolUsage(toolName, channel, success, page, size);
    }
}