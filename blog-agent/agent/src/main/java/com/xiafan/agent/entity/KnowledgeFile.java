package com.xiafan.agent.entity;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/** knowledge_files */
@Data
public class KnowledgeFile {
    private Integer id;
    private Integer knowledgeBaseId;
    private String fileName;
    private Integer fileSize;
    private String fileType;
    private String fileHash;
    private String indexingMethod = "semantic";
    private String status = "pending";
    private String errorMessage;
    private Map<String, Object> fileMetadata = new HashMap<>();
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Integer isDeleted = 0;
}