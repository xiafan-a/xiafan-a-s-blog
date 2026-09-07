package com.xiafan.ai.persistence.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** tool_usage_logs */
@TableName("tool_usage_logs")
@Data
public class ToolUsageLogEntity {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @TableField("tool_name")
    private String toolName;

    @TableField("channel")
    private String channel;

    @TableField("operation")
    private String operation;

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