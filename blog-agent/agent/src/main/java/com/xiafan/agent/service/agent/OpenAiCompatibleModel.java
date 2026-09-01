package com.xiafan.agent.service.agent;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiafan.agent.config.AppProperties;
import com.xiafan.agent.service.llm.OpenAiClient;
import io.agentscope.core.message.ContentBlock;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.MsgRole;
import io.agentscope.core.message.TextBlock;
import io.agentscope.core.message.ToolResultBlock;
import io.agentscope.core.message.ToolUseBlock;
import io.agentscope.core.model.ChatResponse;
import io.agentscope.core.model.ChatUsage;
import io.agentscope.core.model.GenerateOptions;
import io.agentscope.core.model.Model;
import io.agentscope.core.model.ToolSchema;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * agentscope {@link Model} backed by the OpenAI-compatible chat completions endpoint used
 * elsewhere in the app (mirrors agentService.py's {@code client.chat.completions.create}).
 * Non-streaming: one {@link ChatResponse} per call, with tool calls emitted as
 * {@link ToolUseBlock}s whose {@code content} carries the OpenAI arguments JSON so the agent
 * stream can recover the {@code action} chunk parameters.
 */
@Component
public class OpenAiCompatibleModel implements Model {

    private static final Logger log = LoggerFactory.getLogger(OpenAiCompatibleModel.class);

    private final AppProperties props;
    private final OpenAiClient openAi;
    private final ObjectMapper om;

    public OpenAiCompatibleModel(AppProperties props, OpenAiClient openAi, ObjectMapper om) {
        this.props = props;
        this.openAi = openAi;
        this.om = om;
    }

    @Override
    public Flux<ChatResponse> stream(List<Msg> messages, List<ToolSchema> tools, GenerateOptions options) {
        return Flux.defer(() -> {
            try {
                if (props.getApi().getKey().isEmpty()) {
                    return Flux.error(new IllegalStateException("请设置有效的API_KEY环境变量"));
                }
                String modelName = (options != null && options.getModelName() != null)
                        ? options.getModelName() : props.getApi().getModel();
                Double temperature = options == null ? null : options.getTemperature();
                List<Map<String, Object>> openAiMessages = new ArrayList<>();
                if (messages != null) {
                    for (Msg msg : messages) {
                        openAiMessages.addAll(toOpenAiMessages(msg));
                    }
                }
                Map<String, Object> extraBody = new HashMap<>();
                if (tools != null && !tools.isEmpty()) {
                    List<Map<String, Object>> toolList = new ArrayList<>();
                    for (ToolSchema schema : tools) {
                        toolList.add(toOpenAiTool(schema));
                    }
                    extraBody.put("tools", toolList);
                }
                int maxTokens = options != null && options.getMaxTokens() != null
                        ? options.getMaxTokens() : props.getApi().getMaxOutputTokens();
                extraBody.put("max_tokens", maxTokens);
                if (options != null && options.getAdditionalBodyParams() != null) {
                    extraBody.putAll(options.getAdditionalBodyParams());
                }
                if (shouldStream(options, tools)) {
                    return openAi.streamChat(modelName, openAiMessages, temperature, extraBody)
                            .map(this::buildStreamResponse);
                }
                JsonNode completion = openAi.chatCompletion(modelName, openAiMessages, temperature, extraBody, 120);
                return Flux.just(buildResponse(completion));
            } catch (Exception e) {
                log.error("agent model call failed", e);
                return Flux.error(e);
            }
        });
    }

    @Override
    public String getModelName() {
        return props.getApi().getModel();
    }

    // ============================================ conversion ============================================

    private List<Map<String, Object>> toOpenAiMessages(Msg msg) {
        List<Map<String, Object>> out = new ArrayList<>();
        if (msg.getRole() == MsgRole.TOOL) {
            List<ToolResultBlock> blocks = msg.getContentBlocks(ToolResultBlock.class);
            if (!blocks.isEmpty()) {
                for (ToolResultBlock block : blocks) {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("role", "tool");
                    m.put("tool_call_id", block.getId());
                    m.put("content", blockText(block));
                    out.add(m);
                }
                return out;
            }
        }
        Map<String, Object> m = new LinkedHashMap<>();
        switch (msg.getRole()) {
            case SYSTEM -> {
                m.put("role", "system");
                m.put("content", msg.getTextContent());
            }
            case ASSISTANT -> {
                m.put("role", "assistant");
                List<ToolUseBlock> toolCalls = msg.getContentBlocks(ToolUseBlock.class);
                if (!toolCalls.isEmpty()) {
                    m.put("content", msg.getTextContent());
                    List<Map<String, Object>> calls = new ArrayList<>();
                    for (ToolUseBlock tub : toolCalls) {
                        String name = tub.getName() == null ? "" : tub.getName();
                        String args = toolCallArgs(tub);
                        Map<String, Object> function = new LinkedHashMap<>();
                        function.put("name", name);
                        function.put("arguments", args);
                        Map<String, Object> call = new LinkedHashMap<>();
                        call.put("id", tub.getId());
                        call.put("type", "function");
                        call.put("function", function);
                        calls.add(call);
                    }
                    m.put("tool_calls", calls);
                } else {
                    m.put("content", msg.getTextContent());
                }
            }
            case USER -> {
                m.put("role", "user");
                m.put("content", msg.getTextContent());
            }
            default -> {
                m.put("role", "user");
                m.put("content", msg.getTextContent());
            }
        }
        out.add(m);
        return out;
    }

    private static String blockText(ToolResultBlock block) {
        StringBuilder sb = new StringBuilder();
        for (ContentBlock cb : block.getOutput()) {
            if (cb instanceof TextBlock tb) {
                sb.append(tb.getText());
            } else {
                sb.append(cb);
            }
        }
        return sb.toString();
    }

    private String toolCallArgs(ToolUseBlock tub) {
        String content = tub.getContent();
        if (content != null && !content.isBlank()) {
            return content;
        }
        try {
            return om.writeValueAsString(tub.getInput() == null ? Map.of() : tub.getInput());
        } catch (Exception e) {
            return "{}";
        }
    }

    private static Map<String, Object> toOpenAiTool(ToolSchema schema) {
        Map<String, Object> function = new LinkedHashMap<>();
        function.put("name", schema.getName());
        function.put("description", schema.getDescription() == null ? "" : schema.getDescription());
        Map<String, Object> parameters = schema.getParameters();
        function.put("parameters", parameters == null ? Map.of("type", "object") : parameters);
        Map<String, Object> tool = new LinkedHashMap<>();
        tool.put("type", "function");
        tool.put("function", function);
        return tool;
    }

    private ChatResponse buildResponse(JsonNode completion) throws Exception {
        String id = completion.path("id").asText(UUID.randomUUID().toString());
        List<ContentBlock> blocks = new ArrayList<>();
        String content = OpenAiClient.contentFrom(completion);
        if (content != null && !content.isEmpty()) {
            blocks.add(TextBlock.builder().text(content).build());
        }
        List<JsonNode> calls = OpenAiClient.toolCallsFrom(completion);
        for (JsonNode call : calls) {
            String callId = call.path("id").asText(UUID.randomUUID().toString());
            JsonNode fn = call.path("function");
            String name = fn.path("name").asText("");
            String args = fn.path("arguments").asText("{}");
            Map<String, Object> input = parseArgs(args);
            blocks.add(new ToolUseBlock(callId, name, input, args, null));
        }
        int promptTokens = completion.path("usage").path("prompt_tokens").asInt(0);
        int completionTokens = completion.path("usage").path("completion_tokens").asInt(0);
        ChatUsage usage = new ChatUsage(promptTokens, completionTokens, 0.0);
        String finishReason = completion.path("choices").path(0).path("finish_reason").asText(null);
        return new ChatResponse(id, blocks, usage, Map.of(), finishReason);
    }

    private static boolean shouldStream(GenerateOptions options, List<ToolSchema> tools) {
        if (tools != null && !tools.isEmpty()) {
            return false;
        }
        if (options != null && options.getStream() != null) {
            return options.getStream();
        }
        return true;
    }

    private ChatResponse buildStreamResponse(JsonNode node) {
        String id = node.path("id").asText(UUID.randomUUID().toString());
        List<ContentBlock> blocks = new ArrayList<>();
        String finishReason = null;
        JsonNode choices = node.path("choices");
        if (choices.isArray() && !choices.isEmpty()) {
            JsonNode choice = choices.get(0);
            String content = choice.path("delta").path("content").asText(null);
            if (content != null && !content.isEmpty()) {
                blocks.add(TextBlock.builder().text(content).build());
            }
            finishReason = choice.path("finish_reason").asText(null);
        }
        ChatUsage usage = usageFrom(node);
        return new ChatResponse(id, blocks, usage, Map.of(), finishReason);
    }

    private static ChatUsage usageFrom(JsonNode node) {
        JsonNode usage = node.path("usage");
        if (!usage.isObject()) {
            return ChatUsage.builder().build();
        }
        return ChatUsage.builder()
                .inputTokens(usage.path("prompt_tokens").asInt(0))
                .outputTokens(usage.path("completion_tokens").asInt(0))
                .build();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseArgs(String args) {
        if (args == null || args.isBlank()) {
            return Map.of();
        }
        try {
            JsonNode tree = om.readTree(args);
            if (tree.isObject()) {
                return om.convertValue(tree, new TypeReference<Map<String, Object>>() {
                });
            }
        } catch (Exception e) {
            log.warn("failed to parse tool call arguments: {}", e.getMessage());
        }
        return Map.of();
    }
}
