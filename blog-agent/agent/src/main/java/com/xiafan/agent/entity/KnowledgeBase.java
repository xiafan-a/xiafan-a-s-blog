package com.xiafan.agent.entity;

import lombok.Data;

import java.time.LocalDateTime;

/** knowledge_bases */
@Data
public class KnowledgeBase {
    private Integer id;
    private String name;
    private String description;
    private String systemPrompt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Integer isDeleted = 0;
}