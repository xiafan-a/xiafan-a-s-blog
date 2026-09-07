package com.xiafan.ai.skill;

import java.util.LinkedHashMap;
import java.util.Map;

public record SkillDefinition(
        String name,
        String description,
        String instructions,
        String sourcePath,
        boolean enabled) {

    public SkillDefinition withEnabled(boolean value) {
        return new SkillDefinition(name, description, instructions, sourcePath, value);
    }

    /** Lightweight list payload: name and description only, without instructions. */
    public Map<String, Object> summary() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("name", name);
        map.put("description", description == null ? "" : description);
        return map;
    }

    public Map<String, Object> toMap() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("name", name);
        map.put("description", description == null ? "" : description);
        map.put("instructions", instructions);
        map.put("source_path", sourcePath);
        map.put("enabled", enabled);
        return map;
    }
}