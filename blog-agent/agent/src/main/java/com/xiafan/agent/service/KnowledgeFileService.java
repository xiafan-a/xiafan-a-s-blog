package com.xiafan.agent.service;

import com.xiafan.agent.entity.KnowledgeFile;
import com.xiafan.agent.repository.KnowledgeChunkRepository;
import com.xiafan.agent.repository.KnowledgeFileRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Mirrors knowledgeFileService.py (+ the file indexing pipeline equivalent of process_file_task). */
@Service
public class KnowledgeFileService {

    private final KnowledgeFileRepository repository;
    private final KnowledgeChunkRepository chunkRepository;
    private final MinioService minioService;
    private final FileProcessingService fileProcessingService;
    private final KnowledgeChunkService chunkService;

    public KnowledgeFileService(KnowledgeFileRepository repository, KnowledgeChunkRepository chunkRepository,
                                MinioService minioService, FileProcessingService fileProcessingService,
                                KnowledgeChunkService chunkService) {
        this.repository = repository;
        this.chunkRepository = chunkRepository;
        this.minioService = minioService;
        this.fileProcessingService = fileProcessingService;
        this.chunkService = chunkService;
    }

    public KnowledgeFile createKnowledgeFile(int knowledgeBaseId, String fileName, int fileSize, String fileType,
                                             String fileHash, Map<String, Object> fileMetadata) {
        return repository.insert(knowledgeBaseId, fileName, fileSize, fileType, fileHash, fileMetadata);
    }

    public Optional<KnowledgeFile> getKnowledgeFileById(int fileId) {
        return repository.findById(fileId);
    }

    public List<KnowledgeFile> getFilesByKnowledgeBase(int kbId, int skip, int limit) {
        return repository.findByKnowledgeBase(kbId, skip, limit);
    }

    public Optional<KnowledgeFile> getFileByHash(int kbId, String fileHash) {
        return repository.findExistingByHash(kbId, fileHash);
    }

    public Optional<KnowledgeFile> updateFileStatus(int fileId, String status, String errorMessage) {
        if (repository.updateStatus(fileId, status, errorMessage) == 0) {
            return Optional.empty();
        }
        return repository.findById(fileId);
    }

    public Optional<KnowledgeFile> updateFileMetadata(int fileId, Map<String, Object> metadata) {
        if (repository.updateMetadata(fileId, metadata) == 0) {
            return Optional.empty();
        }
        return repository.findById(fileId);
    }

    public boolean softDeleteFile(int fileId) {
        return repository.softDelete(fileId) > 0;
    }

    /**
     * Background indexing task mirroring process_file_task: download from MinIO, extract text,
     * chunk, embed and persist chunks, then update the file status.
     */
    public void processFile(int fileId, String objectName, int knowledgeBaseId) {
        KnowledgeFile file = repository.findById(fileId).orElse(null);
        if (file == null) {
            return;
        }
        updateFileStatus(fileId, "processing", null);
        try {
            byte[] fileBytes = minioService.downloadFile(objectName);
            if (fileBytes == null) {
                throw new IllegalArgumentException("从MinIO下载文件失败: " + objectName);
            }
            String ext = MinioService.extOf(objectName);
            String content = fileProcessingService.extractText(fileBytes, ext);
            List<Map<String, Object>> chunksData = fileProcessingService.chunkText(
                    content, Map.of("file_id", fileId, "source", objectName));
            chunkService.batchCreateChunks(knowledgeBaseId, chunksData, fileId);
            updateFileStatus(fileId, "completed", null);
        } catch (Exception e) {
            updateFileStatus(fileId, "failed", e.getMessage());
        }
    }
}