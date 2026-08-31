package com.xiafan.agent.entity;

import lombok.Data;

import java.time.LocalDateTime;

/** conversation_session */
@Data
public class ConversationSession {
    private Integer id;
    private Integer knowledgeBaseId;
    private String title;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Integer isDeleted = 0;
}