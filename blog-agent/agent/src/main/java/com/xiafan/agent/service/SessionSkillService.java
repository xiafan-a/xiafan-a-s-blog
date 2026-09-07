package com.xiafan.agent.service;

import com.xiafan.agent.service.agent.CapabilityServiceClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Shared helper for chat and agent flows: resolves the skills bound to a session and loads their
 * metadata or full instructions from mcp-skill-service on demand (progressive disclosure — the
 * full text never enters the model context unless it is actually needed).
 *
 * <p>All lookups fail soft: when no skill is bound or the capability service is unavailable, the
 * QA/agent flows fall back to default behavior while the skill names still travel with the
 * persisted messages.</p>
 */
@Service
public class SessionSkillService {

    private static final Logger log = LoggerFactory.getLogger(SessionSkillService.class);

    private final ConversationSessionService sessionService;
    private final CapabilityServiceClient capability;

    public SessionSkillService(ConversationSessionService sessionService, CapabilityServiceClient capability) {
        this.sessionService = sessionService;
        this.capability = capability;
    }

    /**
     * Resolves the names of the skills bound to the session. Returns an empty list when none is
     * bound or the session cannot be read.
     */
    public List<String> resolveSkillNames(Integer sessionId) {
        if (sessionId == null) {
            return List.of();
        }
        try {
            return sessionService.getSessionSkills(sessionId);
        } catch (Exception e) {
            log.warn("Unable to resolve session skills for session {}: {}", sessionId,
                    e.getMessage() == null ? e.toString() : e.getMessage());
            return List.of();
        }
    }

    /**
     * Lightweight metadata (name + one-line description) of the bound skills, straight from
     * {@code mcp-skill-service}'s skill list. Returns an empty list when the capability service is
     * unavailable — callers use this to decide between metadata mode and full-injection fallback.
     */
    public List<Map<String, String>> fetchSkillMetadata(List<String> skillNames) {
        if (skillNames == null || skillNames.isEmpty() || !capability.isEnabled()) {
            return List.of();
        }
        try {
            Map<String, Object> body = capability.listSkills();
            Object value = body == null ? null : body.get("skills");
            List<Map<String, String>> metadata = new ArrayList<>();
            if (value instanceof List<?> skills) {
                for (Object item : skills) {
                    if (!(item instanceof Map<?, ?> map)) {
                        continue;
                    }
                    Object name = map.get("name");
                    if (name instanceof String skillName && skillNames.contains(skillName)) {
                        Object description = map.get("description");
                        Map<String, String> entry = new LinkedHashMap<>();
                        entry.put("name", skillName);
                        entry.put("description", description == null ? "" : String.valueOf(description));
                        metadata.add(entry);
                    }
                }
            }
            return metadata;
        } catch (Exception e) {
            log.warn("Unable to fetch skill metadata for {}: {}", skillNames,
                    e.getMessage() == null ? e.toString() : e.getMessage());
            return List.of();
        }
    }

    /**
     * Fetches the full instructions of one skill ({@code GET /api/v1/skills/{name}}). The text
     * stays out of the model context until a caller explicitly injects it or the model asks for
     * it via the {@code load_skill} tool.
     */
    public String fetchSkillInstructions(String skillName) {
        if (skillName == null || skillName.isBlank() || !capability.isEnabled()) {
            return null;
        }
        try {
            Map<String, Object> skill = capability.getSkill(skillName);
            Object instructions = skill == null ? null : skill.get("instructions");
            if (instructions == null || instructions.toString().isBlank()) {
                log.warn("Skill '{}' returned empty instructions", skillName);
                return null;
            }
            return instructions.toString();
        } catch (Exception e) {
            log.warn("Unable to fetch instructions of skill '{}': {}", skillName,
                    e.getMessage() == null ? e.toString() : e.getMessage());
            return null;
        }
    }

    /**
     * Legacy helper kept for the RAG inject mode: fetches the full wrapped system prompt of one
     * skill via the apply endpoint.
     */
    public String fetchSkillPrompt(String skill, String userMessage) {
        if (skill == null || skill.isBlank() || !capability.isEnabled()) {
            return null;
        }
        try {
            Map<String, Object> request = new LinkedHashMap<>();
            request.put("userMessage", userMessage == null ? "" : userMessage);
            Map<String, Object> response = capability.applySkill(skill, request);
            Object prompt = response == null ? null : response.get("system_prompt");
            if (prompt == null || prompt.toString().isBlank()) {
                log.warn("Session skill '{}' returned an empty system prompt", skill);
                return null;
            }
            log.debug("Applied session skill '{}'", skill);
            return prompt.toString();
        } catch (Exception e) {
            log.warn("Unable to apply session skill '{}': {}", skill,
                    e.getMessage() == null ? e.toString() : e.getMessage());
            return null;
        }
    }
}
