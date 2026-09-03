package com.xiafan.ai.tool;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@NoArgsConstructor
public class ToolCall {

    private String toolName;
    private Map<String, Object> parameters;
    private String callId;
}
