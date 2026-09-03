package com.xiafan.ai.tool;

import java.util.List;
import java.util.Map;

public record ToolCreateRequest(
        String name,
        String display_name,
        String description,
        List<Map<String, Object>> parameters,
        String category,
        String executor_type,
        String api_url,
        String api_method,
        Map<String, String> api_headers,
        String auth_type,
        Map<String, Object> auth_config,
        String response_path,
        Integer timeout,
        Boolean enabled) {
}
