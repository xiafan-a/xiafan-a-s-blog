package com.xiafan.agent.service;

import com.xiafan.agent.entity.ConversationSession;
import com.xiafan.agent.repository.ConversationSessionRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

/** Mirrors conversationSessionService.py. */
@Service
public class ConversationSessionService {

    private final ConversationSessionRepository repository;

    public ConversationSessionService(ConversationSessionRepository repository) {
        this.repository = repository;
    }

    public ConversationSession createSession(int knowledgeBaseId, String title) {
        return repository.insert(knowledgeBaseId, title);
    }

    public Optional<ConversationSession> getSessionById(int sessionId) {
        return repository.findById(sessionId);
    }

    public List<ConversationSession> getSessionsByKnowledgeBase(int kbId, int skip, int limit) {
        return repository.findByKnowledgeBase(kbId, skip, limit);
    }

    public List<ConversationSession> getSessionsByKnowledgeBase(int kbId) {
        return repository.findByKnowledgeBase(kbId);
    }

    public List<ConversationSession> listAllSessions() {
        return repository.findAllNotDeleted();
    }

    public List<ConversationSession> getSessionsByName(String name) {
        return repository.findByNameContaining(name);
    }

    public Optional<ConversationSession> updateSession(int sessionId, String title) {
        if (repository.update(sessionId, title) == 0) {
            return Optional.empty();
        }
        return repository.findById(sessionId);
    }

    public boolean softDeleteSession(int sessionId) {
        return repository.softDelete(sessionId) > 0;
    }
}