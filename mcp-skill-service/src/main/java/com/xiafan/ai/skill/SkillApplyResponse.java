package com.xiafan.ai.skill;

import java.util.List;
import java.util.Map;

public record SkillApplyResponse(
        SkillDefinition skill,
        String systemPrompt,
        List<Map<String, String>> preparedMessages) {
}
