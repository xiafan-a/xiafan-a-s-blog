package com.xiafan.agent.service;

import com.xiafan.agent.entity.KnowledgeBase;
import com.xiafan.agent.repository.KnowledgeBaseRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

/** Mirrors knowledgeBaseService.py. */
@Service
public class KnowledgeBaseService {

    private final KnowledgeBaseRepository repository;

    public KnowledgeBaseService(KnowledgeBaseRepository repository) {
        this.repository = repository;
    }

    public KnowledgeBase createKnowledgeBase(String name, String description, String systemPrompt) {
        return repository.insert(name, description, systemPrompt);
    }

    public Optional<KnowledgeBase> getKnowledgeBaseById(int kbId) {
        return repository.findById(kbId);
    }

    public List<KnowledgeBase> getKnowledgeBases(int skip, int limit) {
        return repository.findAll(skip, limit);
    }

    public Optional<KnowledgeBase> updateKnowledgeBase(int kbId, String name, String description, String systemPrompt) {
        if (repository.update(kbId, name, description, systemPrompt) == 0) {
            return Optional.empty();
        }
        return repository.findById(kbId);
    }

    public boolean softDeleteKnowledgeBase(int kbId) {
        return repository.softDelete(kbId) > 0;
    }
}