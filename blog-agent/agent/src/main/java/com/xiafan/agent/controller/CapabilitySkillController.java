package com.xiafan.agent.controller;

import com.xiafan.agent.common.BusinessException;
import com.xiafan.agent.service.agent.CapabilityServiceClient;
import com.xiafan.agent.service.agent.CapabilityServiceException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Keeps the blog-agent-facing skill API while delegating skill storage and prompt
 * preparation to mcp-skill-service over HTTP.
 */
@RestController
@RequestMapping("/api/v1/agent/skills")
public class CapabilitySkillController {

    private final CapabilityServiceClient capability;

    public CapabilitySkillController(CapabilityServiceClient capability) {
        this.capability = capability;
    }

    @GetMapping
    public Map<String, Object> listSkills() {
        try {
            return capability.listSkills();
        } catch (CapabilityServiceException e) {
            throw new BusinessException(e.getStatusCode(),
                    e.getMessage() == null ? e.toString() : e.getMessage());
        }
    }

    @GetMapping("/{skillName}")
    public Map<String, Object> getSkill(@PathVariable String skillName) {
        try {
            return capability.getSkill(skillName);
        } catch (CapabilityServiceException e) {
            throw new BusinessException(e.getStatusCode(),
                    e.getMessage() == null ? e.toString() : e.getMessage());
        }
    }

    @PostMapping("/{skillName}/apply")
    public Map<String, Object> applySkill(@PathVariable String skillName,
                                          @RequestBody Map<String, Object> request) {
        try {
            return capability.applySkill(skillName, request);
        } catch (CapabilityServiceException e) {
            throw new BusinessException(e.getStatusCode(),
                    e.getMessage() == null ? e.toString() : e.getMessage());
        }
    }
}
