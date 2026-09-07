package com.xiafan.ai.persistence.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** skill_usage_logs */
@TableName("skill_usage_logs")
@Data
public class SkillUsageLogEntity {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @TableField("skill_name")
    private String skillName;

    @TableField("channel")
    private String channel;

    @TableField("operation")
    private String operation;

    @TableField("user_message")
    private String userMessage;

    @TableField("success")
    private Boolean success;

    @TableField("error_message")
    private String errorMessage;

    @TableField("duration_ms")
    private Long durationMs;

    @TableField("created_at")
    private LocalDateTime createdAt;
}