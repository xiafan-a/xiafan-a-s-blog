package com.xiafan.agent.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.xiafan.agent.repository.PostgresJsonTypeHandler;
import lombok.Data;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** conversation_messages */
@TableName(value = "conversation_messages", autoResultMap = true)
@Data
public class ConversationMessage {
    @TableId(value = "id", type = IdType.AUTO)
    private Integer id;
    @TableField("knowledge_base_id")
    private Integer knowledgeBaseId;
    @TableField("role")
    private String role;
    @TableField("content")
    private String content;
    @TableField("session_id")
    private Integer sessionId;
    @TableField("parent_message_id")
    private Integer parentMessageId;
    @TableField("context_window")
    private Integer contextWindow = 10;
    @TableField("context_summary")
    private String contextSummary;
    @TableField(value = "sources", typeHandler = PostgresJsonTypeHandler.class)
    private List<Object> sources = new ArrayList<>();
    @TableField(value = "token_usage", typeHandler = PostgresJsonTypeHandler.class)
    private Map<String, Object> tokenUsage = new HashMap<>();
    @TableField("feedback")
    private Integer feedback;
    @TableField(value = "message_metadata", typeHandler = PostgresJsonTypeHandler.class)
    private Map<String, Object> messageMetadata = new HashMap<>();
    @TableField("created_at")
    private LocalDateTime createdAt;
    @TableField("updated_at")
    private LocalDateTime updatedAt;
    @TableField("is_deleted")
    private Integer isDeleted = 0;
}
