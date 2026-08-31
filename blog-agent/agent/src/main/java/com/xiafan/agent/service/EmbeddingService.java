package com.xiafan.agent.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.xiafan.agent.config.AppProperties;
import com.xiafan.agent.service.llm.OpenAiClient;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/** Mirrors embeddingService.py (OpenAI-compatible /embeddings + cosine similarity). */
@Service
public class EmbeddingService {

    private final OpenAiClient openAi;
    private final AppProperties props;

    public EmbeddingService(OpenAiClient openAi, AppProperties props) {
        this.openAi = openAi;
        this.props = props;
    }

    public List<List<Double>> encode(List<String> texts) {
        JsonNode resp = openAi.embeddings(texts, props.getEmbedding().getModel(), props.getEmbedding().getDimension());
        List<List<Double>> result = new ArrayList<>();
        for (JsonNode item : resp.path("data")) {
            List<Double> vec = new ArrayList<>();
            for (JsonNode v : item.path("embedding")) {
                vec.add(v.asDouble());
            }
            result.add(vec);
        }
        return result;
    }

    public List<Double> encodeSingle(String text) {
        return encode(List.of(text)).get(0);
    }

    public double cosineSimilarity(List<Double> vec1, List<Double> vec2) {
        if (vec1 == null || vec2 == null || vec1.size() != vec2.size() || vec1.isEmpty()) {
            return 0.0;
        }
        double dot = 0;
        double norm1 = 0;
        double norm2 = 0;
        for (int i = 0; i < vec1.size(); i++) {
            double a = vec1.get(i);
            double b = vec2.get(i);
            dot += a * b;
            norm1 += a * a;
            norm2 += b * b;
        }
        double n1 = Math.sqrt(norm1);
        double n2 = Math.sqrt(norm2);
        if (n1 == 0 || n2 == 0) {
            return 0.0;
        }
        return dot / (n1 * n2);
    }

    public List<Double> batchCosineSimilarity(List<Double> queryVec, List<List<Double>> vectors) {
        List<Double> result = new ArrayList<>();
        double qn = norm(queryVec) + 1e-8;
        for (List<Double> v : vectors) {
            double vn = norm(v) + 1e-8;
            double dot = 0;
            for (int i = 0; i < queryVec.size(); i++) {
                dot += v.get(i) * queryVec.get(i);
            }
            result.add(dot / (qn * vn));
        }
        return result;
    }

    private static double norm(List<Double> v) {
        double s = 0;
        for (double x : v) {
            s += x * x;
        }
        return Math.sqrt(s);
    }
}