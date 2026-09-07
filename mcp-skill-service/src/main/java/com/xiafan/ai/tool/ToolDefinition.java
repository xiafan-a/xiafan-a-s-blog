package com.xiafan.ai.tool;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Data
@NoArgsConstructor
public class ToolDefinition {

    private Long id;
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
    private boolean builtIn = false;
    private String apiUrl;
    private String apiMethod = "POST";
    private Map<String, String> apiHeaders;
    private String authType = "none";
    private Map<String, Object> authConfig;
    private String responsePath;

    public Map<String, Object> toMap() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("name", name);
        map.put("display_name", displayName == null ? name : displayName);
        map.put("description", description == null ? "" : description);
        map.put("category", category == null ? "general" : category);
        map.put("parameters", parameters == null ? List.of() : parameters.stream().map(ToolParameter::toMap).toList());
        map.put("enabled", enabled);
        map.put("requires_auth", requiresAuth);
        map.put("timeout", timeout);
        map.put("built_in", builtIn);
        map.put("created_at", createdAt);
        map.put("updated_at", updatedAt);
        if (apiUrl != null) {
            map.put("api_url", apiUrl);
            map.put("api_method", apiMethod);
            map.put("api_headers", apiHeaders);
            map.put("auth_type", authType);
            map.put("auth_config", authConfig);
            map.put("response_path", responsePath);
        }
        return map;
    }
}
