package com.xiafan.agent.entity;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** knowledge_chunks */
@Data
public class KnowledgeChunk {
    private Integer id;
    private Integer knowledgeBaseId;
    private Integer knowledgeFileId;
    private Integer chunkIndex;
    private String content;
    private List<Double> embedding;
    private Map<String, Object> chunkMetadata = new HashMap<>();
    private String indexingMethod = "semantic";
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Integer isDeleted = 0;
}