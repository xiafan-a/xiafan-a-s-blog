package com.xiafan.agent.controller;

import com.xiafan.agent.common.ApiResponse;
import com.xiafan.agent.common.BusinessException;
import com.xiafan.agent.entity.KnowledgeFile;
import com.xiafan.agent.service.KnowledgeChunkService;
import com.xiafan.agent.service.KnowledgeFileService;
import com.xiafan.agent.service.MinioService;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Mirrors fastApiProject/api/knowledgeFile.py: multipart upload to MinIO, DB record + async
 * background indexing (process_file_task in a thread pool).
 */
@RestController
@RequestMapping("/api/v1")
public class KnowledgeFileController {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeFileController.class);
    private static final Set<String> SUPPORTED_FILE_TYPES = Set.of(".txt", ".md", ".pdf", ".docx");

    private static final ExecutorService PROCESS_POOL = Executors.newFixedThreadPool(4);

    private final KnowledgeFileService fileService;
    private final KnowledgeChunkService chunkService;
    private final MinioService minioService;

    public KnowledgeFileController(KnowledgeFileService fileService, KnowledgeChunkService chunkService,
                                   MinioService minioService) {
        this.fileService = fileService;
        this.chunkService = chunkService;
        this.minioService = minioService;
    }

    @PreDestroy
    public void shutdown() {
        PROCESS_POOL.shutdown();
    }

    @PostMapping("/knowledge-files")
    public KnowledgeFile createKnowledgeFile(@RequestParam("knowledge_base_id") int knowledgeBaseId,
                                             @RequestParam("file") MultipartFile file) throws Exception {
        String originalFilename = file.getOriginalFilename();
        String ext = MinioService.extOf(originalFilename == null ? "" : originalFilename);
        if (!SUPPORTED_FILE_TYPES.contains(ext)) {
            throw new BusinessException(400, "不支持的文件类型: " + ext);
        }
        byte[] content = file.getBytes();
        String fileHash = sha256(content);
        if (fileService.getFileByHash(knowledgeBaseId, fileHash).isPresent()) {
            throw new BusinessException(400, "该文件已存在于知识库中");
        }
        MinioService.UploadResult upload = minioService.uploadFileWithOriginalByte(
                content, originalFilename, file.getContentType());
        if (!upload.success()) {
            throw new BusinessException(500, "MinIO上传失败");
        }
        KnowledgeFile created = fileService.createKnowledgeFile(knowledgeBaseId, originalFilename, content.length,
                file.getContentType(), fileHash, Map.of("path", upload.objectName()));
        PROCESS_POOL.submit(() -> {
            try {
                fileService.processFile(created.getId(), upload.objectName(), knowledgeBaseId);
            } catch (Exception e) {
                log.error("background file processing failed", e);
            }
        });
        return created;
    }

    @GetMapping("/knowledge-files/{fileId}")
    public KnowledgeFile getKnowledgeFile(@PathVariable int fileId) {
        return fileService.getKnowledgeFileById(fileId)
                .orElseThrow(() -> new BusinessException(404, "文件不存在"));
    }

    @GetMapping("/knowledge-bases/{kbId}/files")
    public ApiResponse<List<KnowledgeFile>> getFilesByKnowledgeBase(@PathVariable int kbId,
                                                                    @RequestParam(defaultValue = "0") int skip,
                                                                    @RequestParam(defaultValue = "100") int limit) {
        return ApiResponse.ok(fileService.getFilesByKnowledgeBase(kbId, skip, limit));
    }

    @PutMapping("/knowledge-files/{fileId}/status")
    public KnowledgeFile updateFileStatus(@PathVariable int fileId,
                                          @RequestParam String status,
                                          @RequestParam(required = false) String errorMessage) {
        return fileService.updateFileStatus(fileId, status, errorMessage)
                .orElseThrow(() -> new BusinessException(404, "文件不存在"));
    }

    @DeleteMapping("/knowledge-files/{fileId}")
    public ApiResponse<Map<String, String>> deleteKnowledgeFile(@PathVariable int fileId) {
        if (fileService.getKnowledgeFileById(fileId).isEmpty()) {
            throw new BusinessException(404, "文件不存在");
        }
        chunkService.deleteChunksByFile(fileId);
        if (!fileService.softDeleteFile(fileId)) {
            throw new BusinessException(404, "文件不存在");
        }
        return ApiResponse.ok(Map.of("message", "文件删除成功"));
    }

    private static String sha256(byte[] data) throws Exception {
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        byte[] digest = md.digest(data);
        StringBuilder sb = new StringBuilder(digest.length * 2);
        for (byte b : digest) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
}