package com.xiafan.agent.controller;

import com.xiafan.agent.common.ApiResponse;
import com.xiafan.agent.common.BusinessException;
import com.xiafan.agent.entity.ConversationSession;
import com.xiafan.agent.service.ConversationSessionService;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * Mirrors fastApiProject/api/conversationSession.py. As in FastAPI, the later by-name route shadows
 * the earlier {@code GET /sessions/{session_id}} (identical path pattern), so GET /sessions/{x}
 * lists sessions whose title contains the given name.
 */
@RestController
@RequestMapping("/api/v1")
public class ConversationSessionController {

    private final ConversationSessionService sessionService;

    public ConversationSessionController(ConversationSessionService sessionService) {
        this.sessionService = sessionService;
    }

    public record ConversationSessionCreate(int knowledgeBaseId, String title) {
    }

    public record ConversationSessionUpdate(String title) {
    }

    @PostMapping("/sessions")
    public ConversationSession createSession(@RequestBody ConversationSessionCreate session) {
        return sessionService.createSession(session.knowledgeBaseId(), session.title());
    }

    @GetMapping("/sessions/{sessionName}")
    public ApiResponse<List<ConversationSession>> getSessionsByName(@PathVariable String sessionName) {
        return ApiResponse.ok(sessionService.getSessionsByName(sessionName));
    }

    @GetMapping("/knowledge-bases/{kbId}/sessions")
    public ApiResponse<List<ConversationSession>> getSessionsByKnowledgeBase(@PathVariable int kbId,
                                                                             @RequestParam(defaultValue = "0") int skip,
                                                                             @RequestParam(defaultValue = "100") int limit) {
        return ApiResponse.ok(sessionService.getSessionsByKnowledgeBase(kbId, skip, limit));
    }

    @PostMapping("/sessions/{sessionId}")
    public ConversationSession updateSession(@PathVariable int sessionId,
                                             @RequestBody ConversationSessionUpdate update) {
        return sessionService.updateSession(sessionId, update.title())
                .orElseThrow(() -> new BusinessException(404, "会话不存在"));
    }

    @DeleteMapping("/sessions/{sessionId}")
    public ApiResponse<Map<String, String>> deleteSession(@PathVariable int sessionId) {
        if (!sessionService.softDeleteSession(sessionId)) {
            throw new BusinessException(404, "会话不存在");
        }
        return ApiResponse.ok(Map.of("message", "会话删除成功"));
    }
}