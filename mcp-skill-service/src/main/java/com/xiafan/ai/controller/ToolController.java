package com.xiafan.ai.controller;

import com.xiafan.ai.tool.ToolCall;
import com.xiafan.ai.tool.ToolCallRequest;
import com.xiafan.ai.tool.ToolCreateRequest;
import com.xiafan.ai.tool.ToolDefinition;
import com.xiafan.ai.tool.ToolRegistryService;
import com.xiafan.ai.tool.ToolResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Tag(name = "Tools", description = "Tool registry and execution (REST + SSE)")
@RestController
@RequestMapping({"/api/v1/tools", "/api/v1/agent/tools"})
public class ToolController {

    private static final Logger log = LoggerFactory.getLogger(ToolController.class);
    private static final ExecutorService SSE_EXECUTOR =
            Executors.newCachedThreadPool(runnable -> {
                Thread thread = new Thread(runnable, "tool-sse");
                thread.setDaemon(true);
                return thread;
            });

    private final ToolRegistryService registry;

    public ToolController(ToolRegistryService registry) {
        this.registry = registry;
    }

    @Operation(summary = "List registered tools")
    @GetMapping
    public Map<String, Object> listTools(@RequestParam(required = false) String category) {
        List<ToolDefinition> tools = registry.listTools(category, false);
        List<Map<String, Object>> payload = new ArrayList<>();
        for (ToolDefinition tool : tools) {
            payload.add(tool.toMap());
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("tools", payload);
        body.put("count", payload.size());
        return body;
    }

    @Operation(summary = "Get a tool by name")
    @GetMapping("/{toolName}")
    public Map<String, Object> getTool(@PathVariable String toolName) {
        ToolDefinition tool = getRequired(toolName);
        return tool.toMap();
    }

    @Operation(summary = "Register a custom API-backed tool")
    @PostMapping
    public Map<String, Object> registerCustomTool(@RequestBody ToolCreateRequest request) {
        ToolDefinition tool;
        try {
            tool = registry.registerCustom(request);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    e.getMessage() == null ? e.toString() : e.getMessage(), e);
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("success", true);
        body.put("tool", tool.toMap());
        body.put("message", "Tool registered and persisted");
        return body;
    }

    @Operation(summary = "Execute a tool synchronously")
    @PostMapping("/{toolName}/execute")
    public Map<String, Object> executeTool(@PathVariable String toolName,
                                           @RequestBody(required = false) Map<String, Object> parameters) {
        getRequired(toolName);
        ToolCall call = new ToolCall();
        call.setToolName(toolName);
        call.setParameters(parameters);
        ToolResult result = registry.execute(call, "REST");
        return result.toMap();
    }

    @Operation(summary = "Delete a custom tool")
    @DeleteMapping("/{toolName}")
    public Map<String, Object> deleteCustomTool(@PathVariable String toolName) {
        if (registry.isBuiltIn(toolName)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cannot delete built-in tools");
        }
        if (!registry.unregister(toolName)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Tool '" + toolName + "' not found");
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("success", true);
        body.put("message", "Tool '" + toolName + "' deleted");
        return body;
    }

    @Operation(summary = "List tool categories")
    @GetMapping("/categories/list")
    public Map<String, Object> listToolCategories() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("categories", registry.listCategories());
        return body;
    }

    @Operation(summary = "Execute a tool and stream the result over SSE")
    @PostMapping("/stream")
    public SseEmitter streamExecute(@RequestBody ToolCallRequest request) {
        String toolName = request.tool_name();
        getRequired(toolName);
        ToolCall call = new ToolCall();
        call.setToolName(toolName);
        call.setParameters(request.parameters());
        call.setCallId(request.call_id());
        return stream(call);
    }

    @Operation(summary = "Execute a tool by path and stream the result over SSE")
    @PostMapping("/{toolName}/execute/stream")
    public SseEmitter streamExecuteTool(@PathVariable String toolName,
                                        @RequestBody(required = false) Map<String, Object> parameters) {
        getRequired(toolName);
        ToolCall call = new ToolCall();
        call.setToolName(toolName);
        call.setParameters(parameters);
        return stream(call);
    }

    private SseEmitter stream(ToolCall call) {
        SseEmitter emitter = new SseEmitter(0L);
        CompletableFuture.runAsync(() -> {
            Map<String, Object> started = new LinkedHashMap<>();
            started.put("event", "started");
            started.put("tool_name", call.getToolName());
            started.put("call_id", call.getCallId());
            send(emitter, started);
            ToolResult result = registry.execute(call, "SSE");
            Map<String, Object> done = new LinkedHashMap<>();
            done.put("event", "result");
            done.putAll(result.toMap());
            send(emitter, done);
            emitter.complete();
        }, SSE_EXECUTOR).exceptionally(error -> {
            log.error("tool SSE execution failed", error);
            emitter.completeWithError(error);
            return null;
        });
        return emitter;
    }

    private ToolDefinition getRequired(String toolName) {
        ToolDefinition tool = registry.getTool(toolName);
        if (tool == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Tool '" + toolName + "' not found");
        }
        return tool;
    }

    private static void send(SseEmitter emitter, Map<String, Object> payload) {
        try {
            emitter.send(payload, MediaType.APPLICATION_JSON);
        } catch (IOException e) {
            log.warn("tool SSE send failed: {}", e.getMessage());
            emitter.completeWithError(e);
        }
    }
}