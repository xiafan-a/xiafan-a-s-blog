package com.xiafan.agent.entity.agent;

import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Mirrors entity/Tool.py ToolResult.
 */
@Data
@NoArgsConstructor
public class ToolResult {
    private String callId;
    private String toolName;
    private boolean success;
    private Object result;
    private String error;
    private double executionTime;
}