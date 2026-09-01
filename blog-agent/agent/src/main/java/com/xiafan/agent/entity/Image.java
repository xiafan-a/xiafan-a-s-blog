package com.xiafan.agent.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** images: image metadata of files stored in MinIO under their content hash. */
@TableName("images")
@Data
public class Image {
    @TableId(value = "id", type = IdType.AUTO)
    private Integer id;
    /** Hash-based object name in MinIO (e.g. <md5>.png); unique identifier. */
    @TableField("image_name")
    private String imageName;
    /** Filename as provided by the uploader. */
    @TableField("original_name")
    private String originalName;
    @TableField("file_size")
    private Integer fileSize;
    @TableField("content_type")
    private String contentType;
    @TableField("width")
    private Integer width;
    @TableField("height")
    private Integer height;
    /** Not persisted; populated with a fresh presigned URL when the row is read. */
    @TableField(exist = false)
    private String url;
    @TableField("created_at")
    private LocalDateTime createdAt;
    @TableField("updated_at")
    private LocalDateTime updatedAt;
    @TableField("is_deleted")
    private Integer isDeleted = 0;
}
