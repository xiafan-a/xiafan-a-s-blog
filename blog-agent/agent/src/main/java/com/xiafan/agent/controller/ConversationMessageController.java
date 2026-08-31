package com.xiafan.agent.controller;

import com.xiafan.agent.common.ApiResponse;
import com.xiafan.agent.common.BusinessException;
import com.xiafan.agent.entity.ConversationMessage;
import com.xiafan.agent.service.ConversationMessageService;
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
 * Mirrors fastApiProject/api/conversationMessage.py.
 */
@RestController
@RequestMapping("/api/v1")
public class ConversationMessageController {

    private final ConversationMessageService messageService;

    public ConversationMessageController(ConversationMessageService messageService) {
        this.messageService = messageService;
    }

    public record ConversationMessageCreate(int knowledgeBaseId, String role, String content,
                                            Integer sessionId, Integer parentMessageId, Integer contextWindow,
                                            String contextSummary, List<Object> sources,
                                            Map<String, Object> tokenUsage, Integer feedback,
                                            Map<String, Object> metadata) {
    }

    @PostMapping("/messages")
    public ConversationMessage createMessage(@RequestBody ConversationMessageCreate message) {
        return messageService.createMessage(message.knowledgeBaseId(), message.role(), message.content(),
                message.sessionId(), message.parentMessageId(), message.contextWindow(), message.contextSummary(),
                message.sources(), message.tokenUsage(), message.feedback(), message.metadata());
    }

    @GetMapping("/messages/{messageId}")
    public ConversationMessage getMessage(@PathVariable int messageId) {
        return messageService.getMessageById(messageId)
                .orElseThrow(() -> new BusinessException(404, "消息不存在"));
    }

    @GetMapping("/knowledge-bases/{kbId}/messages")
    public ApiResponse<List<ConversationMessage>> getMessagesByKnowledgeBase(@PathVariable int kbId,
                                                                             @RequestParam(defaultValue = "0") int skip,
                                                                             @RequestParam(defaultValue = "100") int limit) {
        return ApiResponse.ok(messageService.getMessagesByKnowledgeBase(kbId, skip, limit));
    }

    @GetMapping("/sessions/{sessionId}/messages")
    public ApiResponse<List<ConversationMessage>> getMessagesBySession(@PathVariable int sessionId,
                                                                       @RequestParam(defaultValue = "0") int skip,
                                                                       @RequestParam(defaultValue = "100") int limit) {
        return ApiResponse.ok(messageService.getMessagesBySession(sessionId, skip, limit));
    }

    @PutMapping("/messages/{messageId}/feedback")
    public ConversationMessage updateMessageFeedback(@PathVariable int messageId,
                                                     @RequestParam int feedback) {
        return messageService.updateMessageFeedback(messageId, feedback)
                .orElseThrow(() -> new BusinessException(404, "消息不存在"));
    }

    @DeleteMapping("/messages/{messageId}")
    public ApiResponse<Map<String, String>> deleteMessage(@PathVariable int messageId) {
        if (!messageService.softDeleteMessage(messageId)) {
            throw new BusinessException(404, "消息不存在");
        }
        return ApiResponse.ok(Map.of("message", "消息删除成功"));
    }
}