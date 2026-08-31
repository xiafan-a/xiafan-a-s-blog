package com.xiafan.agent.entity;

import lombok.Data;

import java.time.LocalDateTime;

/** images: image metadata of files stored in MinIO under their content hash. */
@Data
public class Image {
    private Integer id;
    /** Hash-based object name in MinIO (e.g. <md5>.png); unique identifier. */
    private String imageName;
    /** Filename as provided by the uploader. */
    private String originalName;
    private Integer fileSize;
    private String contentType;
    private Integer width;
    private Integer height;
    /** Not persisted; populated with a fresh presigned URL when the row is read. */
    private String url;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Integer isDeleted = 0;
}