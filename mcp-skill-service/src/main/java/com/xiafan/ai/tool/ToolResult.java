package com.xiafan.ai.tool;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class ToolResult {

    private String callId;
    private String toolName;
    private boolean success;
    private Object result;
    private String error;
    private double executionTime;

    public java.util.Map<String, Object> toMap() {
        java.util.Map<String, Object> map = new java.util.LinkedHashMap<>();
        map.put("call_id", callId);
        map.put("tool_name", toolName);
        map.put("success", success);
        map.put("result", result);
        map.put("error", error);
        map.put("execution_time", executionTime);
        return map;
    }
}
