package com.xiafan.agent.entity;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** conversation_messages */
@Data
public class ConversationMessage {
    private Integer id;
    private Integer knowledgeBaseId;
    private String role;
    private String content;
    private Integer sessionId;
    private Integer parentMessageId;
    private Integer contextWindow = 10;
    private String contextSummary;
    private List<Object> sources = new ArrayList<>();
    private Map<String, Object> tokenUsage = new HashMap<>();
    private Integer feedback;
    private Map<String, Object> messageMetadata = new HashMap<>();
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Integer isDeleted = 0;
}