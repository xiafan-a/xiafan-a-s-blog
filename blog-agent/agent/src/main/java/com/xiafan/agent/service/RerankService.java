package com.xiafan.agent.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.xiafan.agent.config.AppProperties;
import com.xiafan.agent.service.llm.OpenAiClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Mirrors rerankService.py (Alibaba rerank API + embedding-fallback similarity). */
@Service
public class RerankService {

    private static final Logger log = LoggerFactory.getLogger(RerankService.class);

    private final EmbeddingService embeddingService;
    private final OpenAiClient openAi;
    private final AppProperties props;

    public RerankService(EmbeddingService embeddingService, OpenAiClient openAi, AppProperties props) {
        this.embeddingService = embeddingService;
        this.openAi = openAi;
        this.props = props;
    }

    /** gte-rerank string-format similarity for a single candidate. */
    public double calculateSimilarity(String query, String candidate) {
        try {
            HttpResult r = post(query, List.of(candidate), 1, false);
            if (r.status() != 200) {
                return 0.0;
            }
            JsonNode results = r.json().path("output").path("results");
            if (results.isArray() && !results.isEmpty()) {
                return results.get(0).path("relevance_score").asDouble(0.0);
            }
            return 0.0;
        } catch (Exception e) {
            log.warn("Rerank API failed: {}", e.getMessage());
            return 0.0;
        }
    }

    public double calculateSimilarityByEmbedding(String query, String candidate) {
        try {
            List<Double> queryVec = embeddingService.encodeSingle(query);
            List<Double> candidateVec = embeddingService.encodeSingle(candidate);
            return embeddingService.cosineSimilarity(queryVec, candidateVec);
        } catch (Exception e) {
            log.warn("Embedding similarity failed: {}", e.getMessage());
            return 0.0;
        }
    }

    public double calculateSimilarityFromEmbedding(List<Double> targetEmbedding, String candidate) {
        try {
            List<Double> candidateVec = embeddingService.encodeSingle(candidate);
            return embeddingService.cosineSimilarity(targetEmbedding, candidateVec);
        } catch (Exception e) {
            log.warn("Embedding similarity failed: {}", e.getMessage());
            return 0.0;
        }
    }

    public List<Map<String, Object>> rerank(String query, List<String> candidates, int topN, double threshold) {
        if (candidates.isEmpty()) {
            return List.of();
        }
        try {
            HttpResult r = post(query, candidates, Math.min(topN, candidates.size()), false);
            if (r.status() != 200) {
                return fallback(candidates, topN);
            }
            JsonNode results = r.json().path("output").path("results");
            List<Map<String, Object>> ranked = new ArrayList<>();
            for (JsonNode item : results) {
                int idx = item.path("index").asInt(0);
                double score = item.path("relevance_score").asDouble(0.0);
                if (score < threshold) {
                    continue;
                }
                if (idx >= 0 && idx < candidates.size()) {
                    ranked.add(Map.of("index", idx, "similarity", score, "text", candidates.get(idx)));
                }
            }
            return ranked;
        } catch (Exception e) {
            log.warn("Rerank API failed: {}", e.getMessage());
            return fallback(candidates, topN);
        }
    }

    public List<Double> batchCalculateSimilarityByEmbedding(String query, List<String> candidates) {
        try {
            List<Double> queryVec = embeddingService.encodeSingle(query);
            List<List<Double>> candidateVecs = embeddingService.encode(candidates);
            return embeddingService.batchCosineSimilarity(queryVec, candidateVecs);
        } catch (Exception e) {
            log.warn("Embedding batch similarity failed: {}", e.getMessage());
            List<Double> zeros = new ArrayList<>();
            for (int i = 0; i < candidates.size(); i++) {
                zeros.add(0.0);
            }
            return zeros;
        }
    }

    private List<Map<String, Object>> fallback(List<String> candidates, int topN) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (int i = 0; i < candidates.size() && i < topN; i++) {
            out.add(Map.of("index", i, "similarity", 0.0, "text", candidates.get(i)));
        }
        return out;
    }

    private HttpResult post(String query, List<String> documents, int topN, boolean objectFormat) {
        Map<String, Object> body = new HashMap<>();
        body.put("model", props.getRerank().getModel());
        Map<String, Object> input = new HashMap<>();
        if (objectFormat) {
            Map<String, Object> q = new HashMap<>();
            q.put("text", query);
            input.put("query", q);
            List<Map<String, Object>> docs = new ArrayList<>();
            for (String d : documents) {
                docs.add(Map.of("text", d));
            }
            input.put("documents", docs);
        } else {
            input.put("query", query);
            input.put("documents", documents);
        }
        body.put("input", input);
        Map<String, Object> params = new HashMap<>();
        params.put("return_documents", true);
        params.put("top_n", topN);
        body.put("parameters", params);
        HttpResponse<String> resp = openAi.postAndGet(props.getRerank().getApiUrl(), body, 5);
        return new HttpResult(resp.statusCode(), openAi.parseTree(resp.body()));
    }

    public record HttpResult(int status, JsonNode json) {
    }
}