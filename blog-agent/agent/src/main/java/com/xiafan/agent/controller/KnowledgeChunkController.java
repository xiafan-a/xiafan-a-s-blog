package com.xiafan.agent.controller;

import com.xiafan.agent.common.ApiResponse;
import com.xiafan.agent.common.BusinessException;
import com.xiafan.agent.entity.KnowledgeChunk;
import com.xiafan.agent.service.KnowledgeChunkService;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * Mirrors fastApiProject/api/knowledgeChunk.py.
 */
@RestController
@RequestMapping("/api/v1")
public class KnowledgeChunkController {

    private final KnowledgeChunkService chunkService;

    public KnowledgeChunkController(KnowledgeChunkService chunkService) {
        this.chunkService = chunkService;
    }

    public record KnowledgeChunkCreate(int knowledgeBaseId, int chunkIndex, String content,
                                       Integer knowledgeFileId, List<Double> embedding,
                                       Map<String, Object> metadata, String indexingMethod) {
    }

    public record SearchRequest(int knowledgeBaseId, List<Double> queryEmbedding,
                                Integer topK, Double threshold) {
    }

    @PostMapping("/chunks")
    public KnowledgeChunk createChunk(@RequestBody KnowledgeChunkCreate chunk) {
        return chunkService.createChunk(chunk.knowledgeBaseId(), chunk.chunkIndex(), chunk.content(),
                chunk.knowledgeFileId(), chunk.embedding(), chunk.metadata(), chunk.indexingMethod());
    }

    @GetMapping("/chunks/{chunkId}")
    public KnowledgeChunk getChunk(@PathVariable int chunkId) {
        return chunkService.getChunkById(chunkId)
                .orElseThrow(() -> new BusinessException(404, "知识分块不存在"));
    }

    @GetMapping("/knowledge-bases/{kbId}/chunks")
    public ApiResponse<List<KnowledgeChunk>> getChunksByKnowledgeBase(@PathVariable int kbId,
                                                                      @RequestParam(defaultValue = "0") int skip,
                                                                      @RequestParam(defaultValue = "100") int limit) {
        return ApiResponse.ok(chunkService.getChunksByKnowledgeBase(kbId, skip, limit));
    }

    @GetMapping("/files/{fileId}/chunks")
    public List<KnowledgeChunk> getChunksByFile(@PathVariable int fileId) {
        return chunkService.getChunksByFile(fileId, 0, Integer.MAX_VALUE);
    }

    @PostMapping("/chunks/search")
    public List<Map<String, Object>> searchSimilarChunks(@RequestBody SearchRequest searchRequest) {
        int topK = searchRequest.topK() == null ? 5 : searchRequest.topK();
        double threshold = searchRequest.threshold() == null ? 0.5 : searchRequest.threshold();
        return chunkService.searchSimilarChunks(searchRequest.knowledgeBaseId(),
                searchRequest.queryEmbedding(), topK, threshold);
    }

    @DeleteMapping("/chunks/{chunkId}")
    public ApiResponse<Map<String, String>> deleteChunk(@PathVariable int chunkId) {
        if (!chunkService.deleteChunk(chunkId)) {
            throw new BusinessException(404, "知识分块不存在");
        }
        return ApiResponse.ok(Map.of("message", "知识分块删除成功"));
    }
}