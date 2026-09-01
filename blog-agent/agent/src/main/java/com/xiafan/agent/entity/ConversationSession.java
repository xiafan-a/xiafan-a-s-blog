package com.xiafan.agent.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** conversation_session */
@TableName("conversation_session")
@Data
public class ConversationSession {
    @TableId(value = "id", type = IdType.AUTO)
    private Integer id;
    @TableField("knowledge_base_id")
    private Integer knowledgeBaseId;
    @TableField("title")
    private String title;
    @TableField("created_at")
    private LocalDateTime createdAt;
    @TableField("updated_at")
    private LocalDateTime updatedAt;
    @TableField("is_deleted")
    private Integer isDeleted = 0;
}
