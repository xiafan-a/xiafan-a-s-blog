package com.xiafan.agent.service;

import com.xiafan.agent.config.AppProperties;
import com.xiafan.agent.entity.KnowledgeChunk;
import com.xiafan.agent.repository.KnowledgeChunkRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Mirrors knowledgeChunkService.py (CRUD + cosine similarity search + index-range retrieval). */
@Service
public class KnowledgeChunkService {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeChunkService.class);
    private static final int BATCH_SIZE = 10;

    private final KnowledgeChunkRepository repository;
    private final EmbeddingService embeddingService;
    private final AppProperties props;

    public KnowledgeChunkService(KnowledgeChunkRepository repository, EmbeddingService embeddingService,
                                 AppProperties props) {
        this.repository = repository;
        this.embeddingService = embeddingService;
        this.props = props;
    }

    public KnowledgeChunk createChunk(int knowledgeBaseId, int chunkIndex, String content, Integer fileId,
                                      List<Double> embedding, Map<String, Object> metadata, String indexingMethod) {
        return repository.insert(knowledgeBaseId, fileId, chunkIndex, content, embedding, metadata,
                indexingMethod == null ? "semantic" : indexingMethod);
    }

    public KnowledgeChunk createChunkWithEmbedding(int knowledgeBaseId, int chunkIndex, String content,
                                                   Integer fileId, Map<String, Object> metadata) {
        List<Double> embedding;
        try {
            embedding = embeddingService.encodeSingle(content);
        } catch (Exception e) {
            embedding = null;
        }
        return createChunk(knowledgeBaseId, chunkIndex, content, fileId, embedding, metadata, "semantic");
    }

    /** Persists chunks produced by chunkText, embedding in batches of 10 (like batch_create_chunks). */
    public List<KnowledgeChunk> batchCreateChunks(int knowledgeBaseId, List<Map<String, Object>> chunksData,
                                                  Integer fileId) {
        List<String> contents = new ArrayList<>();
        for (Map<String, Object> c : chunksData) {
            contents.add(String.valueOf(c.get("content")));
        }
        List<List<Double>> embeddings = new ArrayList<>();
        try {
            for (int i = 0; i < contents.size(); i += BATCH_SIZE) {
                List<String> batch = contents.subList(i, Math.min(i + BATCH_SIZE, contents.size()));
                embeddings.addAll(embeddingService.encode(batch));
            }
        } catch (Exception e) {
            log.warn("batch embedding failed, inserting chunks without embeddings: {}", e.getMessage());
            for (int i = 0; i < contents.size(); i++) {
                embeddings.add(null);
            }
        }
        List<KnowledgeChunk> created = new ArrayList<>();
        for (int i = 0; i < chunksData.size(); i++) {
            Map<String, Object> c = chunksData.get(i);
            int chunkIndex = ((Number) c.get("chunk_index")).intValue();
            String content = String.valueOf(c.get("content"));
            Map<String, Object> metadata = c.get("metadata") instanceof Map ? castMap(c.get("metadata")) : Map.of();
            created.add(createChunk(knowledgeBaseId, chunkIndex, content, fileId, embeddings.get(i), metadata, "semantic"));
        }
        return created;
    }

    public Optional<KnowledgeChunk> getChunkById(int chunkId) {
        return repository.findById(chunkId);
    }

    public List<KnowledgeChunk> getChunksByKnowledgeBase(int kbId, int skip, int limit) {
        return repository.findByKnowledgeBase(kbId, skip, limit);
    }

    public List<KnowledgeChunk> getChunksByFile(int fileId, int skip, int limit) {
        return repository.findByFile(fileId, skip, limit);
    }

    public Optional<KnowledgeChunk> updateChunk(int chunkId, String content, List<Double> embedding,
                                                Map<String, Object> metadata) {
        if (repository.update(chunkId, content, embedding, metadata) == 0) {
            return Optional.empty();
        }
        return repository.findById(chunkId);
    }

    public boolean deleteChunk(int chunkId) {
        return repository.softDelete(chunkId) > 0;
    }

    public int deleteChunksByFile(int fileId) {
        return repository.deleteByFile(fileId);
    }

    /** Mirrors get_chunks_by_index_range; "before" returns ascending order, "both" merges before+after. */
    public List<KnowledgeChunk> getChunksByIndexRange(int kbId, int chunkIndex, String direction, int limit) {
        if ("before".equalsIgnoreCase(direction)) {
            List<KnowledgeChunk> before = repository.findBefore(kbId, chunkIndex, limit);
            java.util.Collections.reverse(before);
            return before;
        } else if ("after".equalsIgnoreCase(direction)) {
            return repository.findAfter(kbId, chunkIndex, limit);
        }
        List<KnowledgeChunk> before = repository.findBefore(kbId, chunkIndex, limit);
        java.util.Collections.reverse(before);
        List<KnowledgeChunk> after = repository.findAfter(kbId, chunkIndex, limit);
        List<KnowledgeChunk> merged = new ArrayList<>(before);
        merged.addAll(after);
        return merged;
    }

    /** Cosine similarity search; returns maps with chunk_id/content/similarity/metadata/knowledge_file_id/chunk_index. */
    public List<Map<String, Object>> searchSimilarChunks(int kbId, List<Double> queryEmbedding, int topK, double threshold) {
        List<KnowledgeChunk> all = repository.findAllWithEmbedding(kbId);
        List<Map<String, Object>> results = new ArrayList<>();
        for (KnowledgeChunk chunk : all) {
            if (chunk.getEmbedding() == null || chunk.getEmbedding().isEmpty()) {
                continue;
            }
            double similarity = embeddingService.cosineSimilarity(queryEmbedding, chunk.getEmbedding());
            if (similarity >= threshold) {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("chunk_id", chunk.getId());
                row.put("content", chunk.getContent());
                row.put("similarity", similarity);
                row.put("metadata", chunk.getChunkMetadata());
                row.put("knowledge_file_id", chunk.getKnowledgeFileId());
                row.put("chunk_index", chunk.getChunkIndex());
                results.add(row);
            }
        }
        results.sort((a, b) -> Double.compare(((Number) b.get("similarity")).doubleValue(),
                ((Number) a.get("similarity")).doubleValue()));
        return results.size() > topK ? results.subList(0, topK) : results;
    }

    public float defaultThreshold() {
        return (float) props.getRag().getThreshold();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> castMap(Object o) {
        return (Map<String, Object>) o;
    }
}