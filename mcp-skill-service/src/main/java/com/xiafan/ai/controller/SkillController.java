package com.xiafan.ai.controller;

import com.xiafan.ai.persistence.RecordRepository;
import com.xiafan.ai.skill.SkillApplyRequest;
import com.xiafan.ai.skill.SkillApplyResponse;
import com.xiafan.ai.skill.SkillDefinition;
import com.xiafan.ai.skill.SkillRegistry;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Tag(name = "Skills", description = "Skill registry browsing and application")
@RestController
@RequestMapping("/api/v1/skills")
public class SkillController {

    private final SkillRegistry registry;
    private final RecordRepository records;

    public SkillController(SkillRegistry registry, RecordRepository records) {
        this.registry = registry;
        this.records = records;
    }

    @Operation(summary = "List skills")
    @GetMapping
    public Map<String, Object> list(@RequestParam(defaultValue = "false") boolean enabledOnly) {
        List<Map<String, Object>> items = new ArrayList<>();
        for (SkillDefinition def : registry.list(enabledOnly)) {
            items.add(def.summary());
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("skills", items);
        body.put("count", items.size());
        return body;
    }

    @Operation(summary = "Get a skill by name")
    @GetMapping("/{name}")
    public Map<String, Object> get(@PathVariable String name) {
        long start = System.nanoTime();
        try {
            SkillDefinition def = registry.getRequired(name);
            records.recordSkillUsage(name, "REST", "GET", null, true, null, elapsedMs(start));
            return def.toMap();
        } catch (Exception e) {
            records.recordSkillUsage(name, "REST", "GET", null, false, message(e), elapsedMs(start));
            throw e;
        }
    }

    @Operation(summary = "Apply a skill to a user message")
    @PostMapping("/{name}/apply")
    public Map<String, Object> apply(@PathVariable String name,
                                     @Valid @RequestBody SkillApplyRequest request) {
        long start = System.nanoTime();
        try {
            SkillApplyResponse result = registry.apply(name, request.userMessage(), request.conversationHistory());
            records.recordSkillUsage(name, "REST", "APPLY", request.userMessage(), true, null, elapsedMs(start));
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("skill", result.skill().toMap());
            body.put("system_prompt", result.systemPrompt());
            body.put("prepared_messages", result.preparedMessages());
            return body;
        } catch (Exception e) {
            records.recordSkillUsage(name, "REST", "APPLY", request == null ? null : request.userMessage(),
                    false, message(e), elapsedMs(start));
            if (e instanceof ResponseStatusException) {
                throw e;
            }
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    e.getMessage() == null ? e.toString() : e.getMessage(), e);
        }
    }

    private static String message(Exception e) {
        return e.getMessage() == null ? e.toString() : e.getMessage();
    }

    private static long elapsedMs(long startNanos) {
        return (System.nanoTime() - startNanos) / 1_000_000;
    }
}