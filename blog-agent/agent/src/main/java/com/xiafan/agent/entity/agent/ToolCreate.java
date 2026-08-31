package com.xiafan.agent.entity.agent;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Mirrors entity/Tool.py ToolCreate (request model for registering a custom API tool).
 */
@Data
@NoArgsConstructor
public class ToolCreate {
    private String name;
    private String displayName;
    private String description;
    private List<Map<String, Object>> parameters = new ArrayList<>();
    private String category = "custom";
    private String executorType = "api";
    private String apiUrl;
    private String apiMethod = "POST";
    private Map<String, String> apiHeaders;
    private String authType = "none";
    private Map<String, Object> authConfig;
    private String responsePath;
    private int timeout = 30;
    private boolean enabled = true;
}