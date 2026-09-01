package com.xiafan.agent.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiafan.agent.config.AppProperties;
import com.xiafan.agent.entity.KnowledgeBase;
import io.agentscope.core.message.AssistantMessage;
import io.agentscope.core.message.ContentBlock;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.SystemMessage;
import io.agentscope.core.message.TextBlock;
import io.agentscope.core.message.UserMessage;
import io.agentscope.core.model.ChatResponse;
import io.agentscope.core.model.GenerateOptions;
import io.agentscope.core.model.Model;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.SynchronousSink;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Mirrors chatService.py (real_stream_response + rag_stream_response, OpenAI-compatible SSE chunk schema). */
@Service
public class ChatService {

    private final AppProperties props;
    private final Model model;
    private final ObjectMapper om;
    private final RagOptimizationService ragOptimization;
    private final KnowledgeChunkService chunkService;
    private final KnowledgeBaseService knowledgeBaseService;
    private final ConversationMessageService messageService;
    private final EmbeddingService embeddingService;

    public ChatService(AppProperties props, Model model, ObjectMapper om,
                       RagOptimizationService ragOptimization, KnowledgeChunkService chunkService,
                       KnowledgeBaseService knowledgeBaseService, ConversationMessageService messageService,
                       EmbeddingService embeddingService) {
        this.props = props;
        this.model = model;
        this.om = om;
        this.ragOptimization = ragOptimization;
        this.chunkService = chunkService;
        this.knowledgeBaseService = knowledgeBaseService;
        this.messageService = messageService;
        this.embeddingService = embeddingService;
    }

    /** POST /chat/stream — mirrors real_stream_response(messages). */
    public Flux<Map<String, Object>> realStreamResponse(String messages) {
        if (props.getApi().getKey().isEmpty()) {
            return Flux.just(chatChunk("error", "请设置有效的API_KEY环境变量", "assistant", null, true));
        }
        List<Map<String, Object>> messagesList = parseHistory(messages);
        messagesList.add(0, Map.of("role", "system", "content", "You are a helpful assistant."));
        return streamWithAssistant(messagesList, props.getApi().getModel(), props.getApi().getTemperature());
    }

    /**
     * POST /chat/rag/stream — mirrors rag_stream_response: optional query optimization, similarity search,
     * semantic rerank, optional context compression, then either pre-model optimization + char stream
     * or direct streaming, always persisting the user/assistant messages when a session is provided.
     */
    public Flux<Map<String, Object>> ragStreamResponse(String query, int knowledgeBaseId, String messages,
                                                       String model, Double temperature, Integer sessionId) {
        return Flux.defer(() -> {
            List<Map<String, Object>> history = parseHistory(messages);
            String finalQuery = query;
            if (props.getRag().isEnableQueryOptimization()) {
                try {
                    Map<String, Object> optimized = ragOptimization.optimizeQuery(query, history);
                    @SuppressWarnings("unchecked")
                    List<String> expanded = (List<String>) optimized.get("expanded_queries");
                    if (expanded != null && !expanded.isEmpty()) {
                        finalQuery = expanded.get(0);
                    }
                } catch (Exception ignored) {
                    // keep original query
                }
            }
            List<Map<String, Object>> allContexts = searchSimilar(finalQuery, knowledgeBaseId);
            if (props.getRag().isEnableSemanticRerank() && !allContexts.isEmpty()) {
                try {
                    allContexts = ragOptimization.semanticRerank(finalQuery, allContexts,
                            props.getRag().getRerankTopK(), 0.6);
                } catch (Exception ignored) {
                    // keep embedding-similarity order
                }
            }
            int totalContextLength = 0;
            for (Map<String, Object> c : allContexts) {
                Object content = c.get("content");
                totalContextLength += content == null ? 0 : content.toString().length();
            }
            String contextText;
            if (props.getRag().isEnableContextCompression()
                    && totalContextLength > props.getRag().getCompressionThreshold()) {
                try {
                    contextText = ragOptimization.compressContext(query, allContexts,
                            props.getRag().getMaxContextLength());
                } catch (Exception e) {
                    contextText = buildContextText(allContexts, props.getRag().getMaxContextLength());
                }
            } else {
                contextText = buildContextText(allContexts, props.getRag().getMaxContextLength());
            }
            String systemPrompt = knowledgeBaseService.getKnowledgeBaseById(knowledgeBaseId)
                    .map(KnowledgeBase::getSystemPrompt).orElse(null);
            String ragPrompt = buildRagPromptFromContext(query, contextText, systemPrompt);
            List<Map<String, Object>> msgList = new ArrayList<>(history);
            msgList.add(0, Map.of("role", "system", "content", ragPrompt));
            double temp = temperature != null ? temperature : props.getApi().getTemperature();
            String usedModel = (model == null || model.isEmpty()) ? props.getApi().getModel() : model;
            if (props.getRag().isEnableResponseOptimization()) {
                return optimizedResponseStream(query, knowledgeBaseId, sessionId, msgList, allContexts, temp);
            }
            return directRagStream(usedModel, temp, query, knowledgeBaseId, sessionId, msgList, allContexts);
        }).onErrorResume(e -> Flux.just(chatChunk("error", "错误: " + e.getMessage(), "assistant", null, true)));
    }

    private List<Map<String, Object>> searchSimilar(String query, int knowledgeBaseId) {
        try {
            List<Double> vec = embeddingService.encodeSingle(query);
            return chunkService.searchSimilarChunks(knowledgeBaseId, vec,
                    props.getRag().getTopK(), props.getRag().getThreshold());
        } catch (Exception e) {
            return List.of();
        }
    }

    private Flux<Map<String, Object>> streamWithAssistant(List<Map<String, Object>> messages, String modelName,
                                                          double temperature) {
        return model.stream(toAgentScopeMessages(messages), List.of(), chatOptions(modelName, temperature))
                .handle((ChatResponse response, SynchronousSink<Map<String, Object>> sink) ->
                        emitFromResponse(response, sink))
                .takeUntil(chunk -> Boolean.TRUE.equals(chunk.get("done")))
                .onErrorResume(e -> Flux.just(chatChunk("error", "错误: " + e.getMessage(), "assistant", null, true)));
    }

    private Flux<Map<String, Object>> directRagStream(String modelName, double temperature, String query,
                                                      int knowledgeBaseId, Integer sessionId,
                                                      List<Map<String, Object>> msgList,
                                                      List<Map<String, Object>> allContexts) {
        StringBuilder finalResponse = new StringBuilder();
        return model.stream(toAgentScopeMessages(msgList), List.of(), chatOptions(modelName, temperature))
                .handle((ChatResponse response, SynchronousSink<Map<String, Object>> sink) -> {
                    finalResponse.append(responseText(response));
                    emitFromResponse(response, sink);
                })
                .takeUntil(chunk -> Boolean.TRUE.equals(chunk.get("done")))
                .doOnComplete(() -> saveRagMessages(knowledgeBaseId, sessionId, query, finalResponse.toString(), allContexts))
                .onErrorResume(e -> Flux.just(chatChunk("error", "错误: " + e.getMessage(), "assistant", null, true)));
    }

    private Flux<Map<String, Object>> optimizedResponseStream(String query, int knowledgeBaseId, Integer sessionId,
                                                              List<Map<String, Object>> msgList,
                                                              List<Map<String, Object>> allContexts,
                                                              double temperature) {
        try {
            String initialResponse = completeText(msgList, props.getApi().getPreModel(), temperature);
            String finalResponse = initialResponse;
            try {
                Map<String, Object> optimization = ragOptimization.optimizeResponse(query, allContexts, initialResponse);
                Object optimized = optimization.get("optimized_response");
                if (optimized != null) {
                    finalResponse = optimized.toString();
                }
            } catch (Exception ignored) {
                // keep initial response
            }
            saveRagMessages(knowledgeBaseId, sessionId, query, finalResponse, allContexts);
            List<Map<String, Object>> chunks = new ArrayList<>();
            String responseId = "rag_response";
            for (int i = 0; i < finalResponse.length(); i++) {
                chunks.add(chatChunk(responseId, String.valueOf(finalResponse.charAt(i)), "assistant", null, false));
            }
            chunks.add(chatChunk(responseId, "", "assistant", "stop", true));
            return Flux.fromIterable(chunks);
        } catch (Exception e) {
            return Flux.just(chatChunk("error", "错误: " + e.getMessage(), "assistant", null, true));
        }
    }

    private void emitFromResponse(ChatResponse response, SynchronousSink<Map<String, Object>> sink) {
        String content = responseText(response);
        if (!content.isEmpty()) {
            sink.next(chatChunk(response.getId(), content, "assistant", null, false));
        }
        String finish = response.getFinishReason();
        if (finish != null && !finish.isEmpty()) {
            sink.next(chatChunk(response.getId(), "", "assistant", finish, true));
        }
    }

    private String completeText(List<Map<String, Object>> messages, String modelName, double temperature) {
        StringBuilder result = new StringBuilder();
        GenerateOptions options = GenerateOptions.builder()
                .modelName(modelName)
                .temperature(temperature)
                .stream(false)
                .build();
        model.stream(toAgentScopeMessages(messages), List.of(), options)
                .toStream()
                .forEach(response -> result.append(responseText(response)));
        return result.toString();
    }

    private GenerateOptions chatOptions(String modelName, double temperature) {
        return GenerateOptions.builder()
                .modelName(modelName)
                .temperature(temperature)
                .build();
    }

    private List<Msg> toAgentScopeMessages(List<Map<String, Object>> messages) {
        List<Msg> out = new ArrayList<>();
        if (messages != null) {
            for (Map<String, Object> message : messages) {
                if (message == null) {
                    continue;
                }
                String role = message.get("role") == null ? "user" : message.get("role").toString();
                Object value = message.get("content");
                String content = value == null ? "" : String.valueOf(value);
                switch (role.toLowerCase()) {
                    case "system" -> out.add(new SystemMessage(content));
                    case "assistant" -> out.add(new AssistantMessage(content));
                    default -> out.add(new UserMessage(content));
                }
            }
        }
        return out;
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

    private void saveRagMessages(int knowledgeBaseId, Integer sessionId, String query, String response,
                                 List<Map<String, Object>> allContexts) {
        if (sessionId == null) {
            return;
        }
        messageService.createMessage(knowledgeBaseId, "user", query, sessionId, null, null, null, null, null, null, null);
        List<Map<String, Object>> sources = new ArrayList<>();
        for (Map<String, Object> c : allContexts) {
            sources.add(Map.of("chunk_id", c.get("chunk_id"), "similarity", c.get("similarity")));
        }
        messageService.createMessage(knowledgeBaseId, "assistant", response, sessionId, null, null, null, null, null,
                null, Map.of("sources", sources));
    }

    private String buildContextText(List<Map<String, Object>> contexts, int maxLength) {
        StringBuilder contextText = new StringBuilder();
        int totalLength = 0;
        for (int i = 0; i < contexts.size(); i++) {
            Object sim = contexts.get(i).get("similarity");
            double similarity = sim instanceof Number ? ((Number) sim).doubleValue() : 0.0;
            String content = String.valueOf(contexts.get(i).get("content"));
            String piece = String.format("【参考资料%d】(相似度: %.2f)\n%s\n\n", i + 1, similarity, content);
            if (totalLength + piece.length() > maxLength) {
                break;
            }
            contextText.append(piece);
            totalLength += piece.length();
        }
        return contextText.toString();
    }

    private String buildRagPromptFromContext(String query, String contextText, String systemPrompt) {
        String basePrompt = systemPrompt != null ? systemPrompt : """
                你是一个智能助手，请根据提供的知识回答用户问题。
                任务：基于知识库中的信息进行总结并回答用户的问题。
                要求与限制：
                - 不要编造内容，尤其是数字。
                - 如果知识库中的信息与用户问题无关，只需回复：抱歉，没有提供相关信息。
                - 使用 Markdown 格式的文本回答。
                - 使用用户提问的语言回答。
                - 不要编造内容，尤其是数字。
                """;
        if (contextText != null && !contextText.isEmpty()) {
            return basePrompt + """

                    以下是与用户问题相关的参考资料：

                    """ + contextText + """
                    请根据以上参考资料回答用户问题。如果参考资料中没有相关信息，请明确告知用户。
                    不要在问题的回复中提及所参考的资料信息，只对问题进行回复。
                    当对用户所提的问题回答没有充足把握时，回复：“相关信息不足无法回复”。

                    用户问题：""" + query + "\n";
        }
        return basePrompt + """

                用户问题：""" + query + """

                提示：知识库中未找到与用户问题相关的资料。
                当对用户所提的问题回答没有充足把握时，回复：“相关信息不足无法回复”。
                """;
    }

    private List<Map<String, Object>> parseHistory(String messages) {
        if (messages == null || messages.isBlank()) {
            return new ArrayList<>();
        }
        try {
            return om.readValue(messages, new TypeReference<List<Map<String, Object>>>() {
            });
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    private static Map<String, Object> chatChunk(String id, String message, String role,
                                                 String finishReason, boolean done) {
        Map<String, Object> chunk = new LinkedHashMap<>();
        chunk.put("id", id);
        chunk.put("message", message);
        chunk.put("role", role);
        chunk.put("finish_reason", finishReason);
        chunk.put("done", done);
        return chunk;
    }
}
