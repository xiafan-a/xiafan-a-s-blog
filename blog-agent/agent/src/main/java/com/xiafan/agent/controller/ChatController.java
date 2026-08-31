package com.xiafan.agent.controller;

import com.xiafan.agent.common.BusinessException;
import com.xiafan.agent.config.AppProperties;
import com.xiafan.agent.service.ChatService;
import com.xiafan.agent.service.RedisService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import reactor.core.scheduler.Schedulers;

import java.io.IOException;
import java.util.Map;

/**
 * Mirrors fastApiProject/api/chat.py (SSE chat + RAG chat) with the identification header check.
 */
@RestController
@RequestMapping("/api/v1/chat")
public class ChatController {

    private static final Logger log = LoggerFactory.getLogger(ChatController.class);

    private final ChatService chatService;
    private final RedisService redisService;
    private final AppProperties props;

    public ChatController(ChatService chatService, RedisService redisService, AppProperties props) {
        this.chatService = chatService;
        this.redisService = redisService;
        this.props = props;
    }

    public record ChatRequest(String message, String conversationHistory, String model, Double temperature) {
    }

    public record RagChatRequest(String message, int knowledgeBaseId, Integer sessionId,
                                 String conversationHistory, String model, Double temperature) {
    }

    @PostMapping("/stream")
    public SseEmitter streamChat(@RequestBody ChatRequest request, HttpServletRequest servletReq,
                                 HttpServletResponse response) {
        if (request.message() == null || request.message().isBlank()) {
            throw new BusinessException(400, "消息内容不能为空");
        }
        if (!authorized(servletReq)) {
            throw new BusinessException(403, "权限不足");
        }
        SseEmitter emitter = new SseEmitter(0L);
        setSseHeaders(response);
        chatService.realStreamResponse(request.conversationHistory())
                .subscribeOn(Schedulers.boundedElastic())
                .subscribe(chunk -> send(emitter, chunk),
                        err -> onError(emitter, err),
                        emitter::complete);
        return emitter;
    }

    @PostMapping("/rag/stream")
    public SseEmitter ragStreamChat(@RequestBody RagChatRequest request, HttpServletRequest servletReq,
                                    HttpServletResponse response) {
        if (request.message() == null || request.message().isBlank()) {
            throw new BusinessException(400, "消息内容不能为空");
        }
        if (!authorized(servletReq)) {
            throw new BusinessException(403, "权限不足");
        }
        SseEmitter emitter = new SseEmitter(0L);
        setSseHeaders(response);
        chatService.ragStreamResponse(request.message(), request.knowledgeBaseId(),
                        request.conversationHistory(), request.model(), request.temperature(), request.sessionId())
                .subscribeOn(Schedulers.boundedElastic())
                .subscribe(chunk -> send(emitter, chunk),
                        err -> onError(emitter, err),
                        emitter::complete);
        return emitter;
    }

    private boolean authorized(HttpServletRequest request) {
        String identification = request.getHeader(props.getChat().getHeadKey());
        if (identification == null || identification.isEmpty()) {
            return false;
        }
        return redisService.checkSetMember(props.getChat().getIdentificationSet(), identification);
    }

    private static void setSseHeaders(HttpServletResponse response) {
        response.setHeader("Cache-Control", "no-cache");
        response.setHeader("Connection", "keep-alive");
        response.setHeader("X-Accel-Buffering", "no");
    }

    private static void send(SseEmitter emitter, Map<String, Object> chunk) {
        try {
            emitter.send(chunk, MediaType.APPLICATION_JSON);
        } catch (IOException e) {
            log.warn("chat stream send failed (client may be gone): {}", e.getMessage());
            emitter.completeWithError(e);
        }
    }

    private static void onError(SseEmitter emitter, Throwable err) {
        log.error("chat stream error", err);
        emitter.completeWithError(err);
    }
}