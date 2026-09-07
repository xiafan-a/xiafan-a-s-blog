package com.xiafan.agent.entity.agent;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Mirrors entity/Tool.py ToolParameter. Serialized keys name/type/description/required/default/enum.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ToolParameter {
    private String name;
    private String type;
    private String description;
    private boolean required = true;
    @JsonProperty("default")
    private Object defaultValue;
    @JsonProperty("enum")
    private List<String> enumValues;

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
        Object enumValue = raw.get("enum");
        if (enumValue instanceof List<?> values) {
            List<String> enumValues = new ArrayList<>();
            for (Object value : values) {
                enumValues.add(String.valueOf(value));
            }
            parameter.setEnumValues(enumValues);
        }
        return parameter;
    }
}
