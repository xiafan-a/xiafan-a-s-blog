package com.xiafan.ai.persistence.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** tool_definitions */
@TableName("tool_definitions")
@Data
public class ToolDefinitionEntity {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @TableField("name")
    private String name;

    @TableField("display_name")
    private String displayName;

    @TableField("description")
    private String description;

    @TableField("parameters")
    private String parameters;

    @TableField("category")
    private String category;

    @TableField("enabled")
    private Boolean enabled;

    @TableField("requires_auth")
    private Boolean requiresAuth;

    @TableField("timeout_seconds")
    private Integer timeoutSeconds;

    @TableField("api_url")
    private String apiUrl;

    @TableField("api_method")
    private String apiMethod;

    @TableField("api_headers")
    private String apiHeaders;

    @TableField("auth_type")
    private String authType;

    @TableField("auth_config")
    private String authConfig;

    @TableField("response_path")
    private String responsePath;

    @TableField("built_in")
    private Boolean builtIn;

    @TableField("created_at")
    private LocalDateTime createdAt;

    @TableField("updated_at")
    private LocalDateTime updatedAt;
}