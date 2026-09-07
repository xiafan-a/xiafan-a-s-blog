package com.xiafan.ai.tool;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ToolParameter {

    private String name = "";
    private String type = "string";
    private String description = "";
    private boolean required = true;
    private Object defaultValue;
    private List<String> enumValues;

    public Map<String, Object> toMap() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("name", name);
        map.put("type", type);
        map.put("description", description == null ? "" : description);
        map.put("required", required);
        if (defaultValue != null) {
            map.put("default", defaultValue);
        }
        if (enumValues != null && !enumValues.isEmpty()) {
            map.put("enum", enumValues);
        }
        return map;
    }

    public static ToolParameter fromMap(Map<String, Object> raw) {
        ToolParameter parameter = new ToolParameter();
        parameter.setName(String.valueOf(raw.getOrDefault("name", "")));
        parameter.setType(String.valueOf(raw.getOrDefault("type", "string")));
        parameter.setDescription(String.valueOf(raw.getOrDefault("description", "")));
        parameter.setRequired(!Boolean.FALSE.equals(raw.get("required")));
        if (raw.containsKey("default")) {
            parameter.setDefaultValue(raw.get("default"));
        } else if (raw.containsKey("default_value")) {
            parameter.setDefaultValue(raw.get("default_value"));
        }
        Object enums = raw.get("enum");
        if (enums instanceof List<?> values) {
            List<String> enumValues = new ArrayList<>();
            for (Object value : values) {
                enumValues.add(String.valueOf(value));
            }
            parameter.setEnumValues(enumValues);
        }
        return parameter;
    }
}
