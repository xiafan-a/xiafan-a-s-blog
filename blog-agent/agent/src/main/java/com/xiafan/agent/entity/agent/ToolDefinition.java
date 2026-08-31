package com.xiafan.agent.entity.agent;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * Mirrors entity/Tool.py ToolDefinition.
 */
@Data
@NoArgsConstructor
public class ToolDefinition {
    private Integer id;
    private String name;
    private String displayName;
    private String description;
    private List<ToolParameter> parameters = new ArrayList<>();
    private String category = "general";
    private boolean enabled = true;
    private boolean requiresAuth = false;
    private int timeout = 60;
    private String createdAt;
    private String updatedAt;
}