package com.xiafan.agent.controller;

import com.xiafan.agent.common.ApiResponse;
import com.xiafan.agent.common.BusinessException;
import com.xiafan.agent.entity.ConversationSession;
import com.xiafan.agent.entity.agent.ToolDefinition;
import com.xiafan.agent.entity.agent.ToolResult;
import com.xiafan.agent.service.ConversationSessionService;
import com.xiafan.agent.service.agent.AgentService;
import com.xiafan.agent.service.agent.ToolRegistryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import reactor.core.scheduler.Schedulers;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Mirrors fastApiProject/api/agent.py. The ResponseMiddleware wraps every non-SSE response in
 * ApiResponse, so only /chat/stream returns a bare SSE stream.
 */
@RestController
@RequestMapping("/api/v1/agent")
public class AgentController {

    private static final Logger log = LoggerFactory.getLogger(AgentController.class);

    private static final List<String> BUILT_IN_TOOLS = List.of("file_read", "file_write", "web_search");

    private final AgentService agentService;
    private final ToolRegistryService registry;
    private final ConversationSessionService conversationSessionService;

    public AgentController(AgentService agentService, ToolRegistryService registry,
                           ConversationSessionService conversationSessionService) {
        this.agentService = agentService;
        this.registry = registry;
        this.conversationSessionService = conversationSessionService;
    }

    public record AgentChatRequest(String message, Integer sessionId,
                                   List<Map<String, String>> conversationHistory,
                                   List<String> availableTools,
                                   Integer maxIterations, Boolean enableThought) {
    }

    // ============================================ chat ============================================

    @PostMapping("/chat/stream")
    public SseEmitter agentChatStream(@RequestBody AgentChatRequest request) {
        List<String> availableTools = request.availableTools();
        if (availableTools == null) {
            availableTools = List.of(registry.defaultTools());
        }
        SseEmitter emitter = new SseEmitter(0L);
        agentService.processMessage(request.message(), request.conversationHistory(), availableTools,
                        request.sessionId() == null ? null : String.valueOf(request.sessionId()),
                        request.maxIterations(), request.enableThought())
                .subscribeOn(Schedulers.boundedElastic())
                .subscribe(
                        chunk -> {
                            try {
                                emitter.send(chunk, MediaType.APPLICATION_JSON);
                            } catch (IOException e) {
                                log.warn("agent stream send failed (client may be gone): {}", e.getMessage());
                            }
                        },
                        err -> {
                            log.error("agent stream error", err);
                            emitter.completeWithError(err);
                        },
                        emitter::complete);
        return emitter;
    }

    @PostMapping("/chat")
    public Map<String, Object> agentChat(@RequestBody AgentChatRequest request) {
        try {
            return agentService.chat(request.message(), request.conversationHistory(), request.availableTools());
        } catch (Exception e) {
            throw new BusinessException(500, e.getMessage() == null ? e.toString() : e.getMessage());
        }
    }

    // ============================================ tool management ============================================

    @GetMapping("/tools")
    public Map<String, Object> listTools(@RequestParam(required = false) String category) {
        List<ToolDefinition> tools = registry.listTools(category, false);
        List<Map<String, Object>> payload = new ArrayList<>();
        for (ToolDefinition t : tools) {
            payload.add(toolPayload(t));
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("tools", payload);
        return body;
    }

    @GetMapping("/tools/{toolName}")
    public Map<String, Object> getTool(@PathVariable String toolName) {
        ToolDefinition tool = registry.getTool(toolName);
        if (tool == null) {
            throw new BusinessException(404, "Tool '" + toolName + "' not found");
        }
        return toolPayload(tool);
    }

    @PostMapping("/tools")
    public Map<String, Object> addCustomTool(@RequestBody com.xiafan.agent.entity.agent.ToolCreate request) {
        ToolDefinition tool;
        try {
            tool = registry.registerCustom(request);
        } catch (IllegalArgumentException e) {
            throw new BusinessException(400, e.getMessage() == null ? e.toString() : e.getMessage());
        }
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("name", tool.getName());
        summary.put("display_name", tool.getDisplayName());
        summary.put("description", tool.getDescription());
        summary.put("category", tool.getCategory());
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("success", true);
        body.put("tool", summary);
        body.put("message", "Tool registered successfully (in-memory only, not persisted)");
        return body;
    }

    @PostMapping("/tools/{toolName}/execute")
    public Map<String, Object> executeTool(@PathVariable String toolName,
                                           @RequestBody Map<String, Object> parameters) {
        ToolDefinition tool = registry.getTool(toolName);
        if (tool == null) {
            throw new BusinessException(404, "Tool '" + toolName + "' not found");
        }
        com.xiafan.agent.entity.agent.ToolCall call = new com.xiafan.agent.entity.agent.ToolCall();
        call.setToolName(toolName);
        call.setParameters(parameters);
        call.setCallId(UUID.randomUUID().toString());
        ToolResult result = registry.execute(call);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("call_id", result.getCallId());
        body.put("tool_name", result.getToolName());
        body.put("success", result.isSuccess());
        body.put("result", result.getResult());
        body.put("error", result.getError());
        body.put("execution_time", result.getExecutionTime());
        return body;
    }

    @DeleteMapping("/tools/{toolName}")
    public Map<String, Object> deleteCustomTool(@PathVariable String toolName) {
        if (BUILT_IN_TOOLS.contains(toolName)) {
            throw new BusinessException(400, "Cannot delete built-in tools");
        }
        if (!registry.unregister(toolName)) {
            throw new BusinessException(404, "Tool '" + toolName + "' not found");
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("success", true);
        body.put("message", "Tool '" + toolName + "' deleted");
        return body;
    }

    @GetMapping("/tools/categories/list")
    public Map<String, Object> listToolCategories() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("categories", registry.listCategories());
        return body;
    }

    // ============================================ sessions ============================================

    @GetMapping("/sessions")
    public ApiResponse<List<ConversationSession>> getSessions(@RequestParam(defaultValue = "-1") int kbId) {
        List<ConversationSession> sessions = kbId <= 0
                ? conversationSessionService.listAllSessions()
                : conversationSessionService.getSessionsByKnowledgeBase(kbId);
        return ApiResponse.ok(sessions);
    }

    private static Map<String, Object> toolPayload(ToolDefinition t) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("name", t.getName());
        payload.put("display_name", t.getDisplayName());
        payload.put("description", t.getDescription());
        payload.put("category", t.getCategory());
        payload.put("parameters", t.getParameters());
        payload.put("enabled", t.isEnabled());
        payload.put("timeout", t.getTimeout());
        return payload;
    }
}