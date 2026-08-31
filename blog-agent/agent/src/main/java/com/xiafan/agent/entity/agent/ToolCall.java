package com.xiafan.agent.entity.agent;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * Mirrors entity/Tool.py ToolCall.
 */
@Data
@NoArgsConstructor
public class ToolCall {
    private String toolName;
    private Map<String, Object> parameters;
    private String callId;
}