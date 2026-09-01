package com.xiafan.agent.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.xiafan.agent.repository.PostgresJsonTypeHandler;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/** knowledge_files */
@TableName(value = "knowledge_files", autoResultMap = true)
@Data
public class KnowledgeFile {
    @TableId(value = "id", type = IdType.AUTO)
    private Integer id;
    @TableField("knowledge_base_id")
    private Integer knowledgeBaseId;
    @TableField("file_name")
    private String fileName;
    @TableField("file_size")
    private Integer fileSize;
    @TableField("file_type")
    private String fileType;
    @TableField("file_hash")
    private String fileHash;
    @TableField("indexing_method")
    private String indexingMethod = "semantic";
    @TableField("status")
    private String status = "pending";
    @TableField("error_message")
    private String errorMessage;
    @TableField(value = "file_metadata", typeHandler = PostgresJsonTypeHandler.class)
    private Map<String, Object> fileMetadata = new HashMap<>();
    @TableField("created_at")
    private LocalDateTime createdAt;
    @TableField("updated_at")
    private LocalDateTime updatedAt;
    @TableField("is_deleted")
    private Integer isDeleted = 0;
}
