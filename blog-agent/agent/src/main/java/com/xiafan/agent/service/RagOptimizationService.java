package com.xiafan.agent.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.xiafan.agent.config.AppProperties;
import com.xiafan.agent.service.llm.OpenAiClient;
import io.agentscope.core.message.ContentBlock;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.TextBlock;
import io.agentscope.core.message.UserMessage;
import io.agentscope.core.model.ChatResponse;
import io.agentscope.core.model.GenerateOptions;
import io.agentscope.core.model.Model;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Mirrors ragOptimizationService.py (query optimization, rerank, context compression, response optimization). */
@Service
public class RagOptimizationService {

    private static final Logger log = LoggerFactory.getLogger(RagOptimizationService.class);

    private final OpenAiClient openAi;
    private final AppProperties props;
    private final Model model;

    public RagOptimizationService(OpenAiClient openAi, Model model, AppProperties props) {
        this.openAi = openAi;
        this.model = model;
        this.props = props;
    }

    public Map<String, Object> optimizeQuery(String query, List<Map<String, Object>> history) {
        StringBuilder historyText = new StringBuilder();
        if (history != null && !history.isEmpty()) {
            List<Map<String, Object>> recent = history.size() > 5 ? history.subList(history.size() - 5, history.size()) : history;
            for (Map<String, Object> msg : recent) {
                if (!historyText.isEmpty()) {
                    historyText.append("\n");
                }
                historyText.append(msg.getOrDefault("role", "user")).append(": ").append(msg.getOrDefault("content", ""));
            }
        }
        String prompt = """
                你是一个查询优化专家。请将用户的问题从不同角度重新表述，生成5个语义相近但表述不同的问题版本，以便在知识库中检索到更相关的信息。

                对话历史：
                %s

                用户当前问题：%s

                请生成扩写版本，要求：
                1. 根据上下文信息总结用户问题的核心语义
                2. 可以适当补充上下文信息（结合对话历史）

                请以JSON格式返回结果，格式如下：
                {
                    "expanded_queries": "扩写版本"
                }

                注意：只返回JSON，不要有其他内容""".formatted(
                historyText.isEmpty() ? "（无历史消息）" : historyText, query);

        Map<String, Object> result = new HashMap<>();
        result.put("original_query", query);
        result.put("expanded_queries", List.of());
        try {
            String content = completeText(props.getApi().getExtendModel(), prompt, 0.1);
            if (content != null) {
                String parsed = stripMarkdownFence(content.strip());
                JsonNode json = openAi.parseTree(parsed);
                List<String> expanded = readStrings(json.get("expanded_queries"));
                result.put("expanded_queries", expanded.isEmpty() ? List.of() : List.of(expanded.get(0)));
            }
        } catch (Exception e) {
            log.warn("optimize_query failed, falling back to original query: {}", e.getMessage());
        }
        return result;
    }

    /** Re-ranks dict contexts; on failure returns the original contexts (top_k truncated). */
    public List<Map<String, Object>> semanticRerank(String query, List<Map<String, Object>> contexts, int topK, double threshold) {
        if (contexts == null || contexts.isEmpty()) {
            return List.of();
        }
        try {
            int n = Math.min(topK, contexts.size());
            Map<String, Object> body = buildRerankBody(query, contexts, n);
            JsonNode resp = openAi.parseTree(openAi.postAndGet(props.getRerank().getApiUrl(), body, 5).body());
            JsonNode results = resp.path("output").path("results");
            if (!results.isArray()) {
                return contexts.subList(0, n);
            }
            List<Map<String, Object>> ranked = new ArrayList<>();
            for (JsonNode item : results) {
                int idx = item.path("index").asInt(0);
                double score = item.path("relevance_score").asDouble(0.0);
                if (score < threshold) {
                    continue;
                }
                if (idx >= 0 && idx < contexts.size()) {
                    Map<String, Object> ctx = new LinkedHashMap<>(contexts.get(idx));
                    ctx.put("rerank_score", score);
                    ctx.put("similarity", score);
                    ranked.add(ctx);
                }
            }
            return ranked;
        } catch (Exception e) {
            log.warn("semantic_rerank failed, using original order: {}", e.getMessage());
            return contexts.subList(0, Math.min(topK, contexts.size()));
        }
    }

    /** Returns context text, compressing via LLM only when over maxLength. */
    public String compressContext(String query, List<Map<String, Object>> contexts, int maxLength) {
        if (contexts == null || contexts.isEmpty()) {
            return "";
        }
        String joined = joinContexts(contexts);
        int totalLength = 0;
        for (Map<String, Object> c : contexts) {
            Object content = c.get("content");
            if (content != null) {
                totalLength += content.toString().length();
            }
        }
        if (totalLength <= maxLength) {
            return joined;
        }
        String prompt = """
                你是一个信息提取专家。请从以下参考资料中提取与用户问题最相关的关键信息。

                用户问题：%s

                参考资料：
                %s

                请执行以下操作：
                1. 提取与问题直接相关的关键信息
                2. 去除无关的描述和冗余内容
                3. 保留重要的细节和数据
                4. 保持信息的准确性，不要添加原文没有的信息

                请输出压缩后的内容，保留资料编号以便追溯。压缩后的内容总长度不要超过%d个字符。"""
                .formatted(query, joined, maxLength);
        try {
            String compressed = completeText(props.getApi().getExtendModel(), prompt, 0.1);
            if (compressed == null || compressed.isBlank()) {
                throw new RuntimeException("empty compressed result");
            }
            return compressed.strip();
        } catch (Exception e) {
            log.warn("compress_context failed, truncating: {}", e.getMessage());
            StringBuilder result = new StringBuilder();
            for (int i = 0; i < contexts.size(); i++) {
                Object content = contexts.get(i).get("content");
                String text = content == null ? "" : content.toString();
                if (result.length() + text.length() > maxLength) {
                    break;
                }
                result.append("【参考资料").append(i + 1).append("】\n").append(text).append("\n\n");
            }
            return result.toString();
        }
    }

    public Map<String, Object> optimizeResponse(String query, List<Map<String, Object>> contexts, String response) {
        StringBuilder contextSummary = new StringBuilder();
        int limit = Math.min(5, contexts == null ? 0 : contexts.size());
        for (int i = 0; i < limit; i++) {
            Object content = contexts.get(i).get("content");
            String text = content == null ? "" : content.toString();
            String truncated = text.length() > 150 ? text.substring(0, 150) : text;
            contextSummary.append("[资料").append(i + 1).append("] ").append(truncated).append("...\n");
        }
        String prompt = """
                你是一个回答质量审核专家。请审核以下回答的质量并进行优化。

                用户问题：%s

                参考的知识库内容：
                %s

                AI的回答：
                %s

                请执行以下任务：
                1. 判断回答是否合理（是否回答了问题、是否有依据、是否有幻觉）
                2. 找出回答中的问题（如：与参考资料不符的内容、过度推断、无关信息等）
                3. 优化回答，去除不合理部分

                请以JSON格式返回结果：
                {
                    "is_reasonable": true/false,
                    "issues": ["问题1", "问题2"],
                    "optimized_response": "优化后的回答",
                    "confidence": 0.85
                }

                注意：
                - 如果回答整体合理，is_reasonable为true，issues为空列表，optimized_response可以与原回答相同
                - confidence表示对回答质量的信心程度，0-1之间
                - 只返回JSON，不要有其他内容""".formatted(query, contextSummary, response);

        Map<String, Object> result = new HashMap<>();
        result.put("is_reasonable", true);
        result.put("optimized_response", response);
        result.put("issues", List.of());
        result.put("confidence", 0.5);
        try {
            String content = completeText(props.getApi().getResultModel(), prompt, 0.1);
            if (content != null) {
                JsonNode json = openAi.parseTree(stripMarkdownFence(content.strip()));
                result.put("is_reasonable", json.path("is_reasonable").asBoolean(true));
                result.put("optimized_response", json.path("optimized_response").asText(response));
                List<String> issues = new ArrayList<>();
                JsonNode issueArr = json.path("issues");
                if (issueArr.isArray()) {
                    issueArr.forEach(i -> issues.add(i.asText()));
                }
                result.put("issues", issues);
                result.put("confidence", json.path("confidence").asDouble(0.5));
            }
        } catch (Exception e) {
            log.warn("optimize_response failed, keeping original response: {}", e.getMessage());
        }
        return result;
    }

    private String completeText(String modelName, String prompt, double temperature) {
        StringBuilder result = new StringBuilder();
        GenerateOptions options = GenerateOptions.builder()
                .modelName(modelName)
                .temperature(temperature)
                .stream(false)
                .build();
        model.stream(List.<Msg>of(new UserMessage(prompt)), List.of(), options)
                .toStream()
                .forEach(response -> result.append(responseText(response)));
        return result.toString();
    }

    private static String responseText(ChatResponse response) {
        if (response == null) {
            return "";
        }
        StringBuilder content = new StringBuilder();
        for (ContentBlock block : response.getContent()) {
            if (block instanceof TextBlock textBlock) {
                content.append(textBlock.getText());
            }
        }
        return content.toString();
    }

    private String joinContexts(List<Map<String, Object>> contexts) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < contexts.size(); i++) {
            if (i > 0) {
                sb.append("\n\n");
            }
            Object content = contexts.get(i).get("content");
            sb.append("【参考资料").append(i + 1).append("】\n").append(content == null ? "" : content);
        }
        return sb.toString();
    }

    private Map<String, Object> buildRerankBody(String query, List<Map<String, Object>> contexts, int topK) {
        Map<String, Object> body = new HashMap<>();
        body.put("model", props.getRerank().getModel());
        Map<String, Object> input = new HashMap<>();
        input.put("query", Map.of("text", query));
        List<Map<String, Object>> docs = new ArrayList<>();
        for (Map<String, Object> ctx : contexts) {
            docs.add(Map.of("text", String.valueOf(ctx.get("content"))));
        }
        input.put("documents", docs);
        body.put("input", input);
        body.put("parameters", Map.of("return_documents", true, "top_n", topK));
        return body;
    }

    private List<String> readStrings(JsonNode node) {
        List<String> out = new ArrayList<>();
        if (node == null || node.isMissingNode() || node.isNull()) {
            return out;
        }
        if (node.isArray()) {
            node.forEach(n -> out.add(n.asText()));
        } else if (node.isTextual()) {
            out.add(node.asText());
        }
        return out;
    }

    private static String stripMarkdownFence(String text) {
        if (text.contains("```json")) {
            return text.split("```json")[1].split("```")[0].trim();
        } else if (text.contains("```")) {
            return text.split("```")[1].split("```")[0].trim();
        }
        return text;
    }
}
