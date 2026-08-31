package com.xiafan.agent.service;

import com.xiafan.agent.entity.ConversationMessage;
import com.xiafan.agent.repository.ConversationMessageRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Mirrors conversationMessageService.py. */
@Service
public class ConversationMessageService {

    private final ConversationMessageRepository repository;

    public ConversationMessageService(ConversationMessageRepository repository) {
        this.repository = repository;
    }

    public ConversationMessage createMessage(int knowledgeBaseId, String role, String content, Integer sessionId,
                                             Integer parentMessageId, Integer contextWindow, String contextSummary,
                                             List<Object> sources, Map<String, Object> tokenUsage, Integer feedback,
                                             Map<String, Object> messageMetadata) {
        return repository.insert(knowledgeBaseId, role, content, sessionId, parentMessageId, contextWindow,
                contextSummary, sources, tokenUsage, feedback, messageMetadata);
    }

    public Optional<ConversationMessage> getMessageById(int messageId) {
        return repository.findById(messageId);
    }

    public List<ConversationMessage> getMessagesByKnowledgeBase(int kbId, int skip, int limit) {
        return repository.findByKnowledgeBase(kbId, skip, limit);
    }

    public List<ConversationMessage> getMessagesBySession(int sessionId, int skip, int limit) {
        return repository.findBySession(sessionId, skip, limit);
    }

    public Optional<ConversationMessage> updateMessageFeedback(int messageId, int feedback) {
        if (repository.updateFeedback(messageId, feedback) == 0) {
            return Optional.empty();
        }
        return repository.findById(messageId);
    }

    public boolean softDeleteMessage(int messageId) {
        return repository.softDelete(messageId) > 0;
    }
}