package com.xiafan.agent.controller;

import com.xiafan.agent.common.ApiResponse;
import com.xiafan.agent.common.BusinessException;
import com.xiafan.agent.entity.ConversationSession;
import com.xiafan.agent.service.ConversationSessionService;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
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

    public record SessionSkillUpdate(String skill, java.util.List<String> skills) {
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

    // ============================================ session skill ============================================

    /** 当前会话绑定的 skill 列表；空数组表示未绑定（默认问答行为）。附带 legacy 单值 skill 字段兼容旧前端。 */
    @GetMapping("/sessions/{sessionId}/skill")
    public ApiResponse<Map<String, Object>> getSessionSkill(@PathVariable int sessionId) {
        List<String> skills = sessionService.getSessionSkills(sessionId);
        Map<String, Object> body = new java.util.LinkedHashMap<>();
        body.put("session_id", sessionId);
        body.put("skills", skills);
        body.put("skill", skills.isEmpty() ? "" : skills.get(0));
        return ApiResponse.ok(body);
    }

    /**
     * 替换当前会话的 skill 绑定（多选）。接受 {@code {"skills": ["a","b"]}}，也兼容旧的
     * {@code {"skill": "a"}}（包装成单元素）；空数组或 null 表示清除绑定。返回更新后的会话。
     */
    @PutMapping("/sessions/{sessionId}/skill")
    public ConversationSession setSessionSkill(@PathVariable int sessionId,
                                               @RequestBody SessionSkillUpdate update) {
        List<String> skills;
        if (update == null) {
            skills = List.of();
        } else if (update.skills() != null) {
            skills = update.skills();
        } else {
            skills = update.skill() == null || update.skill().isBlank()
                    ? List.of() : List.of(update.skill());
        }
        return sessionService.setSessionSkills(sessionId, skills);
    }
}