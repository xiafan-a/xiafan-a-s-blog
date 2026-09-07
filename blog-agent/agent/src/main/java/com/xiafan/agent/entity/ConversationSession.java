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
    /** 旧单选 skill 名（保留只读兼容；新数据写 skills 列） */
    @TableField("skill")
    private String skill;
    /** 会话绑定的 skill 名称列表（逗号分隔存储；可空；为空表示默认问答行为） */
    @TableField("skills")
    private String skills;
    @TableField("created_at")
    private LocalDateTime createdAt;
    @TableField("updated_at")
    private LocalDateTime updatedAt;
    @TableField("is_deleted")
    private Integer isDeleted = 0;
}
