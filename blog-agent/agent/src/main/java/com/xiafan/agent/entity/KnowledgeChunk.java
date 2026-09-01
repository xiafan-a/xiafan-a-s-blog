package com.xiafan.agent.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.xiafan.agent.repository.PostgresJsonTypeHandler;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** knowledge_chunks */
@TableName(value = "knowledge_chunks", autoResultMap = true)
@Data
public class KnowledgeChunk {
    @TableId(value = "id", type = IdType.AUTO)
    private Integer id;
    @TableField("knowledge_base_id")
    private Integer knowledgeBaseId;
    @TableField("knowledge_file_id")
    private Integer knowledgeFileId;
    @TableField("chunk_index")
    private Integer chunkIndex;
    @TableField("content")
    private String content;
    @TableField(value = "embedding", typeHandler = PostgresJsonTypeHandler.class)
    private List<Double> embedding;
    @TableField(value = "chunk_metadata", typeHandler = PostgresJsonTypeHandler.class)
    private Map<String, Object> chunkMetadata = new HashMap<>();
    @TableField("indexing_method")
    private String indexingMethod = "semantic";
    @TableField("created_at")
    private LocalDateTime createdAt;
    @TableField("updated_at")
    private LocalDateTime updatedAt;
    @TableField("is_deleted")
    private Integer isDeleted = 0;
}
