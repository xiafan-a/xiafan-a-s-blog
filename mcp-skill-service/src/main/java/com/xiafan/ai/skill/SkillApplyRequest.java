package com.xiafan.ai.skill;

import jakarta.validation.constraints.NotBlank;

import java.util.List;
import java.util.Map;

public record SkillApplyRequest(
        @NotBlank(message = "userMessage must not be blank")
        String userMessage,
        List<Map<String, String>> conversationHistory) {
}
