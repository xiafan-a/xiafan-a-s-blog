package com.xiafan.agent.controller;

import com.xiafan.agent.common.ApiResponse;
import com.xiafan.agent.common.BusinessException;
import com.xiafan.agent.entity.ConversationSession;
import com.xiafan.agent.entity.KnowledgeBase;
import com.xiafan.agent.service.ConversationSessionService;
import com.xiafan.agent.service.KnowledgeBaseService;
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
 * Mirrors fastApiProject/api/knowledgeBase.py.
 */
@RestController
@RequestMapping("/api/v1")
public class KnowledgeBaseController {

    private final KnowledgeBaseService knowledgeBaseService;
    private final ConversationSessionService conversationSessionService;

    public KnowledgeBaseController(KnowledgeBaseService knowledgeBaseService,
                                   ConversationSessionService conversationSessionService) {
        this.knowledgeBaseService = knowledgeBaseService;
        this.conversationSessionService = conversationSessionService;
    }

    public record KnowledgeBaseCreate(String name, String description, String systemPrompt) {
    }

    public record KnowledgeBaseUpdate(String name, String description, String systemPrompt) {
    }

    @PostMapping("/create/knowledge-base")
    public KnowledgeBase createKnowledgeBase(@RequestBody KnowledgeBaseCreate kb) {
        return knowledgeBaseService.createKnowledgeBase(kb.name(), kb.description(), kb.systemPrompt());
    }

    @GetMapping("/knowledge-bases/{kbId}")
    public ApiResponse<List<ConversationSession>> getSessionsOfKnowledgeBase(
            @PathVariable int kbId,
            @RequestParam(defaultValue = "0") int skip,
            @RequestParam(defaultValue = "100") int limit) {
        if (knowledgeBaseService.getKnowledgeBaseById(kbId).isEmpty()) {
            throw new BusinessException(404, "知识库不存在");
        }
        return ApiResponse.ok(conversationSessionService.getSessionsByKnowledgeBase(kbId, skip, limit));
    }

    @GetMapping("/knowledge-bases")
    public ApiResponse<List<KnowledgeBase>> getKnowledgeBases(
            @RequestParam(defaultValue = "0") int skip,
            @RequestParam(defaultValue = "100") int limit) {
        return ApiResponse.ok(knowledgeBaseService.getKnowledgeBases(skip, limit));
    }

    @PostMapping("/knowledge-bases/{kbId}")
    public ApiResponse<KnowledgeBase> updateKnowledgeBase(@PathVariable int kbId,
                                                          @RequestBody KnowledgeBaseUpdate update) {
        return knowledgeBaseService.updateKnowledgeBase(kbId, update.name(), update.description(), update.systemPrompt())
                .map(v -> ApiResponse.ok(v))
                .orElseThrow(() -> new BusinessException(404, "知识库不存在"));
    }

    @DeleteMapping("/knowledge-bases/{kbId}")
    public ApiResponse<Map<String, String>> deleteKnowledgeBase(@PathVariable int kbId) {
        if (!knowledgeBaseService.softDeleteKnowledgeBase(kbId)) {
            throw new BusinessException(404, "知识库不存在");
        }
        return ApiResponse.ok(Map.of("message", "知识库删除成功"));
    }
}