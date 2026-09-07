package com.xiafan.ai.persistence.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** mcp_call_logs */
@TableName("mcp_call_logs")
@Data
public class McpCallLogEntity {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @TableField("server_name")
    private String serverName;

    @TableField("tool_name")
    private String toolName;

    @TableField("arguments")
    private String arguments;

    @TableField("result_summary")
    private String resultSummary;

    @TableField("success")
    private Boolean success;

    @TableField("error_message")
    private String errorMessage;

    @TableField("duration_ms")
    private Long durationMs;

    @TableField("created_at")
    private LocalDateTime createdAt;
}