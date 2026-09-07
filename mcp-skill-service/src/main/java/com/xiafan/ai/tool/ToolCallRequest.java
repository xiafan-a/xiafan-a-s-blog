package com.xiafan.ai.tool;

import java.util.Map;

public record ToolCallRequest(String tool_name, String call_id, Map<String, Object> parameters) {
}
