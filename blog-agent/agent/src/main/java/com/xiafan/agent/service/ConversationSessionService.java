package com.xiafan.agent.service;

import com.xiafan.agent.common.BusinessException;
import com.xiafan.agent.entity.ConversationSession;
import com.xiafan.agent.repository.ConversationSessionRepository;
import com.xiafan.agent.service.agent.CapabilityServiceClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Mirrors conversationSessionService.py; skill binding is validated against mcp-skill-service. */
@Service
public class ConversationSessionService {

    private static final Logger log = LoggerFactory.getLogger(ConversationSessionService.class);

    private final ConversationSessionRepository repository;
    private final CapabilityServiceClient capability;

    public ConversationSessionService(ConversationSessionRepository repository, CapabilityServiceClient capability) {
        this.repository = repository;
        this.capability = capability;
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

    public List<ConversationSession> listStandaloneSessions() {
        return repository.findStandalone();
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

    // ============================================ session skill ============================================

    private static final int MAX_SKILLS_PER_SESSION = 10;

    /**
     * Returns the skill names bound to the session (empty list = default behavior). Prefers the
     * multi-select {@code skills} column and falls back to the legacy single-select {@code skill}
     * column for sessions saved before the migration.
     */
    public List<String> getSessionSkills(int sessionId) {
        ConversationSession session = repository.findById(sessionId)
                .orElseThrow(() -> new BusinessException(404, "会话不存在"));
        List<String> names = parseSkills(session.getSkills());
        if (names.isEmpty()) {
            names = parseSkills(session.getSkill());
        }
        return names;
    }

    /** Replaces the session's skill binding. An empty list clears it. Returns the updated session. */
    public ConversationSession setSessionSkills(int sessionId, List<String> skills) {
        List<String> normalized = normalizeSkills(skills);
        validateSkills(normalized);
        if (repository.updateSkills(sessionId, normalized.isEmpty() ? null : String.join(",", normalized)) == 0) {
            throw new BusinessException(404, "会话不存在");
        }
        return repository.findById(sessionId)
                .orElseThrow(() -> new BusinessException(404, "会话不存在"));
    }

    /** Splits the comma-separated storage value into trimmed, de-duplicated names. */
    public static List<String> parseSkills(String stored) {
        if (stored == null || stored.isBlank()) {
            return new ArrayList<>();
        }
        List<String> names = new ArrayList<>();
        for (String raw : stored.split(",")) {
            String name = raw.trim();
            if (!name.isEmpty() && !names.contains(name)) {
                names.add(name);
            }
        }
        return names;
    }

    /** Trims, drops blanks and duplicates, rejects commas, and enforces the per-session cap. */
    static List<String> normalizeSkills(List<String> skills) {
        List<String> names = new ArrayList<>();
        if (skills == null) {
            return names;
        }
        for (String raw : skills) {
            if (raw == null) {
                continue;
            }
            String name = raw.trim();
            if (name.isEmpty()) {
                continue;
            }
            if (name.contains(",")) {
                throw new BusinessException(400, "Skill 名称不能包含逗号: " + name);
            }
            if (!names.contains(name)) {
                names.add(name);
            }
        }
        if (names.size() > MAX_SKILLS_PER_SESSION) {
            throw new BusinessException(400, "每个会话最多绑定 " + MAX_SKILLS_PER_SESSION + " 个 Skill");
        }
        return names;
    }

    /**
     * Checks every skill name against mcp-skill-service when reachable. Unknown names are rejected;
     * a temporarily unavailable capability service does not block saving (chat applies fail-soft too).
     */
    private void validateSkills(List<String> skills) {
        if (skills.isEmpty() || !capability.isEnabled()) {
            return;
        }
        try {
            Map<String, Object> body = capability.listSkills();
            Object value = body == null ? null : body.get("skills");
            if (value instanceof List<?> remote) {
                var known = remote.stream()
                        .filter(Map.class::isInstance)
                        .map(item -> ((Map<?, ?>) item).get("name"))
                        .filter(String.class::isInstance)
                        .map(String.class::cast)
                        .collect(java.util.stream.Collectors.toSet());
                for (String skill : skills) {
                    if (!known.contains(skill)) {
                        throw new BusinessException(400, "Skill '" + skill + "' 不存在");
                    }
                }
            }
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.warn("Unable to validate skills {} against mcp-skill-service: {}", skills, e.getMessage());
        }
    }
}
