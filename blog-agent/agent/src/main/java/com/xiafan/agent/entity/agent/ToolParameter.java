package com.xiafan.agent.entity.agent;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

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
}