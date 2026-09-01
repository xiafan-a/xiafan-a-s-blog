package com.xiafan.agent.service.agent;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiafan.agent.common.BusinessException;
import com.xiafan.agent.config.AppProperties;
import com.xiafan.agent.entity.agent.AgentResponseType;
import io.agentscope.core.ReActAgent;
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.event.AgentEvent;
import io.agentscope.core.event.AgentResultEvent;
import io.agentscope.core.event.ExceedMaxItersEvent;
import io.agentscope.core.event.TextBlockDeltaEvent;
import io.agentscope.core.event.TextBlockEndEvent;
import io.agentscope.core.event.TextBlockStartEvent;
import io.agentscope.core.event.ThinkingBlockDeltaEvent;
import io.agentscope.core.event.ThinkingBlockEndEvent;
import io.agentscope.core.event.ThinkingBlockStartEvent;
import io.agentscope.core.event.ToolCallDeltaEvent;
import io.agentscope.core.event.ToolCallEndEvent;
import io.agentscope.core.event.ToolCallStartEvent;
import io.agentscope.core.event.ToolResultDataDeltaEvent;
import io.agentscope.core.event.ToolResultEndEvent;
import io.agentscope.core.event.ToolResultStartEvent;
import io.agentscope.core.event.ToolResultTextDeltaEvent;
import io.agentscope.core.message.AssistantMessage;
import io.agentscope.core.message.ContentBlock;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.TextBlock;
import io.agentscope.core.message.ToolResultState;
import io.agentscope.core.message.UserMessage;
import io.agentscope.core.model.Model;
import io.agentscope.core.tool.Toolkit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Mirrors service/agentService.py using agent-scope: a fresh {@link ReActAgent} per request over a
 * shared {@link OpenAiCompatibleModel} and a Toolkit built from the allowed tools. Translates the
 * agentscope event stream into the Python chunk schema (thought/action/observation/text/summary/done).
 */
@Service
public class AgentService {

    private static final Logger log = LoggerFactory.getLogger(AgentService.class);

    private static final String REACT_PROMPT_TEMPLATE = """
            请注意，你是一个有能力调用外部工具的智能助手。

            可用工具如下:
            %s

            请严格按照以下步骤工作：
            1. Thought: 首先分析问题、拆解任务并规划下一步行动。
            2. 需要使用工具时，调用下方提供的函数工具完成调用，等待工具执行结果后继续分析。
            3. 当你已经收集到足够的信息，能够回答用户的最终问题时，直接给出最终答案。

            现在，请开始解决以下问题:
            Question: %s
            History: %s
            """;

    private final AppProperties props;
    private final Model model;
    private final ToolRegistryService registry;
    private final ObjectMapper om;

    public AgentService(AppProperties props, Model model, ToolRegistryService registry,
                        ObjectMapper om) {
        this.props = props;
        this.model = model;
        this.registry = registry;
        this.om = om;
    }

    /**
     * Streams agent chunks mirroring process_message: thought / action / observation / text,
     * then summary and done. Errors yield a single error chunk (done=true).
     */
    public Flux<Map<String, Object>> processMessage(String userMessage,
                                                    List<Map<String, String>> conversationHistory,
                                                    List<String> availableTools,
                                                    String sessionId,
                                                    Integer maxIterations,
                                                    Boolean enableThought) {
        String sid = (sessionId == null || sessionId.isBlank()) ? UUID.randomUUID().toString() : sessionId;
        int maxIters = maxIterations == null ? props.getAgent().getMaxReactIterations() : maxIterations;
        boolean thinking = enableThought == null ? props.getAgent().isReactThinkingEnabled() : enableThought;

        List<String> allowed = availableTools;
        if (allowed == null) {
            allowed = List.of(registry.defaultTools());
        }
        String toolsDesc = registry.formatToolsForPrompt(allowed);
        String historyText = formatHistory(conversationHistory);
        String sysPrompt = String.format(REACT_PROMPT_TEMPLATE, toolsDesc, userMessage,
                historyText.isEmpty() ? "无" : historyText);

        Toolkit toolkit = registry.buildToolkit(allowed);
        ReActAgent agent = ReActAgent.builder()
                .name("Assistant")
                .sysPrompt(sysPrompt)
                .model(model)
                .toolkit(toolkit)
                .maxIters(maxIters)
                .build();
        List<Msg> messages = buildMessages(userMessage, conversationHistory);
        RuntimeContext ctx = RuntimeContext.builder().sessionId(sid).build();
        StreamState state = new StreamState(sid, thinking, om);

        return agent.streamEvents(messages, ctx)
                .concatMap(ev -> {
                    List<Map<String, Object>> chunks = state.handle(ev);
                    return chunks.isEmpty()
                            ? Flux.empty()
                            : Flux.fromIterable(chunks.stream().filter(Objects::nonNull).toList());
                })
                .onErrorResume(err -> {
                    log.error("agent stream error", err);
                    return Flux.fromIterable(state.error(err));
                })
                .concatWith(Flux.defer(() -> Flux.fromIterable(state.finishChunks())))
                .doFinally(sig -> closeAgent(agent));
    }

    /**
     * Non-streaming aggregation mirroring AgentService.chat(): {content, tool_calls, steps, session_id}.
     */
    public Map<String, Object> chat(String userMessage,
                                    List<Map<String, String>> conversationHistory,
                                    List<String> availableTools) {
        String sessionId = UUID.randomUUID().toString();
        Map<String, Object> full = new LinkedHashMap<>();
        full.put("content", "");
        full.put("tool_calls", new ArrayList<>());
        full.put("steps", new ArrayList<>());
        full.put("session_id", sessionId);
        processMessage(userMessage, conversationHistory, availableTools, sessionId, null, null)
                .toStream()
                .forEach(chunk -> {
                    String type = String.valueOf(chunk.get("type"));
                    String typeOf = type == null ? "" : type;
                    switch (typeOf) {
                        case "text" -> {
                            String prev = String.valueOf(full.get("content"));
                            full.put("content", prev + String.valueOf(chunk.get("content")));
                        }
                        case AgentResponseType.ACTION -> {
                            @SuppressWarnings("unchecked")
                            List<Map<String, Object>> calls = (List<Map<String, Object>>) full.get("tool_calls");
                            Map<String, Object> call = new LinkedHashMap<>();
                            call.put("tool_name", chunk.get("tool_name"));
                            call.put("parameters", chunk.get("parameters"));
                            calls.add(call);
                        }
                        case AgentResponseType.THOUGHT -> {
                            @SuppressWarnings("unchecked")
                            List<Map<String, Object>> steps = (List<Map<String, Object>>) full.get("steps");
                            Map<String, Object> step = new LinkedHashMap<>();
                            step.put("type", "thought");
                            step.put("content", chunk.get("content"));
                            steps.add(step);
                        }
                        case AgentResponseType.OBSERVATION -> {
                            @SuppressWarnings("unchecked")
                            List<Map<String, Object>> steps = (List<Map<String, Object>>) full.get("steps");
                            if (!steps.isEmpty()) {
                                steps.get(steps.size() - 1).put("observation", chunk.get("result"));
                            }
                        }
                        case "error" -> {
                            throw new BusinessException(500, String.valueOf(chunk.get("error")));
                        }
                        default -> {
                            // ignore summary/done
                        }
                    }
                });
        return full;
    }

    // ============================================ helpers ============================================

    private List<Msg> buildMessages(String userMessage, List<Map<String, String>> history) {
        List<Msg> messages = new ArrayList<>();
        if (history != null) {
            for (Map<String, String> h : history) {
                if (h == null) {
                    continue;
                }
                String role = h.get("role");
                String content = h.get("content");
                if (content == null) {
                    continue;
                }
                if ("assistant".equalsIgnoreCase(role)) {
                    messages.add(new AssistantMessage(content));
                } else {
                    messages.add(new UserMessage(content));
                }
            }
        }
        messages.add(new UserMessage(userMessage));
        return messages;
    }

    private static String formatHistory(List<Map<String, String>> history) {
        if (history == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (Map<String, String> h : history) {
            if (h == null) {
                continue;
            }
            String role = h.get("role");
            String content = h.get("content");
            if (content == null) {
                continue;
            }
            if ("user".equalsIgnoreCase(role)) {
                sb.append("User: ").append(content).append('\n');
            } else if ("assistant".equalsIgnoreCase(role)) {
                sb.append("Assistant: ").append(content).append('\n');
            }
        }
        return sb.toString();
    }

    private static void closeAgent(ReActAgent agent) {
        try {
            if (agent != null) {
                agent.close();
            }
        } catch (Exception ignored) {
            // best-effort resource cleanup
        }
    }

    /** Per-request event-to-chunk state machine. */
    private static final class StreamState {

        private final String sessionId;
        private final boolean enableThought;
        private final ObjectMapper om;
        private final long startNanos = System.nanoTime();

        private boolean textOpen;
        private final StringBuilder textBuffer = new StringBuilder();
        private String actionCallId;
        private String actionName;
        private final StringBuilder actionParams = new StringBuilder();
        private long resultStartNanos;
        private String resultName;
        private final StringBuilder resultBuffer = new StringBuilder();

        private final Map<String, Integer> toolsUsed = new LinkedHashMap<>();
        private final List<Map<String, Object>> reactSteps = new ArrayList<>();
        private int successfulCalls;
        private int failedCalls;
        private int step;

        private boolean errored;
        private boolean sawResult;

        StreamState(String sessionId, boolean enableThought, ObjectMapper om) {
            this.sessionId = sessionId;
            this.enableThought = enableThought;
            this.om = om;
        }

        List<Map<String, Object>> handle(AgentEvent ev) {
            List<Map<String, Object>> chunks = new ArrayList<>();
            if (ev instanceof TextBlockStartEvent || ev instanceof ThinkingBlockStartEvent) {
                textOpen = true;
                textBuffer.setLength(0);
            } else if (ev instanceof TextBlockDeltaEvent tbd) {
                if (textOpen) {
                    textBuffer.append(tbd.getDelta());
                }
            } else if (ev instanceof ThinkingBlockDeltaEvent tbd) {
                if (textOpen) {
                    textBuffer.append(tbd.getDelta());
                }
            } else if (ev instanceof TextBlockEndEvent || ev instanceof ThinkingBlockEndEvent) {
                textOpen = false;
            } else if (ev instanceof ToolCallStartEvent tcs) {
                addIfNotNull(chunks, flushThought());
                actionCallId = tcs.getToolCallId();
                actionName = tcs.getToolCallName();
                actionParams.setLength(0);
            } else if (ev instanceof ToolCallDeltaEvent tcd) {
                actionParams.append(tcd.getDelta());
            } else if (ev instanceof ToolCallEndEvent) {
                chunks.add(emitAction());
                actionCallId = null;
                actionName = null;
                actionParams.setLength(0);
            } else if (ev instanceof ToolResultStartEvent) {
                resultStartNanos = System.nanoTime();
                resultName = ((ToolResultStartEvent) ev).getToolCallName();
                resultBuffer.setLength(0);
            } else if (ev instanceof ToolResultTextDeltaEvent trt) {
                resultBuffer.append(trt.getDelta());
            } else if (ev instanceof ToolResultDataDeltaEvent trd) {
                ContentBlock data = trd.getData();
                if (data instanceof TextBlock tb) {
                    resultBuffer.append(tb.getText());
                } else {
                    resultBuffer.append(data);
                }
            } else if (ev instanceof ToolResultEndEvent tre) {
                chunks.add(emitObservation(tre));
            } else if (ev instanceof AgentResultEvent are) {
                sawResult = true;
                addIfNotNull(chunks, emitText(are));
            } else if (ev instanceof ExceedMaxItersEvent) {
                // max iterations reached; final result (if any) arrives via AgentResultEvent
            }
            return chunks;
        }

        private static void addIfNotNull(List<Map<String, Object>> chunks, Map<String, Object> chunk) {
            if (chunk != null) {
                chunks.add(chunk);
            }
        }

        private Map<String, Object> flushThought() {
            String thought = textBuffer.toString().trim();
            textBuffer.setLength(0);
            if (!enableThought || thought.isEmpty()) {
                return null;
            }
            Map<String, Object> chunk = new LinkedHashMap<>();
            chunk.put("type", AgentResponseType.THOUGHT);
            chunk.put("content", thought);
            chunk.put("step", step);
            chunk.put("session_id", sessionId);
            chunk.put("partial", false);
            return chunk;
        }

        private Map<String, Object> emitAction() {
            Map<String, Object> parameters;
            String raw = actionParams.toString();
            if (raw == null || raw.isBlank()) {
                parameters = Map.of();
            } else {
                Object parsed = parseResult(raw);
                parameters = parsed instanceof Map<?, ?> map ? toStringMap(map) : Map.of();
            }
            Map<String, Object> chunk = new LinkedHashMap<>();
            chunk.put("type", AgentResponseType.ACTION);
            chunk.put("tool_name", actionName);
            chunk.put("parameters", parameters);
            chunk.put("step", step);
            chunk.put("session_id", sessionId);
            return chunk;
        }

        @SuppressWarnings("unchecked")
        private Map<String, Object> toStringMap(Map<?, ?> map) {
            Map<String, Object> out = new LinkedHashMap<>();
            for (Map.Entry<?, ?> e : map.entrySet()) {
                out.put(String.valueOf(e.getKey()), e.getValue());
            }
            return out;
        }

        private Map<String, Object> emitObservation(ToolResultEndEvent tre) {
            boolean success = tre.getState() == ToolResultState.SUCCESS;
            String resultText = resultBuffer.toString();
            Object value = success ? parseToJsonValue(resultText) : null;
            String error = success ? null : (resultText.isEmpty() ? tre.getState().name() : resultText);
            double executionTime = (System.nanoTime() - resultStartNanos) / 1_000_000_000.0;

            toolCount(tre.getToolCallName());
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("tool_name", tre.getToolCallName());
            row.put("success", success);
            reactSteps.add(row);
            if (success) {
                successfulCalls++;
            } else {
                failedCalls++;
            }

            Map<String, Object> chunk = new LinkedHashMap<>();
            chunk.put("type", AgentResponseType.OBSERVATION);
            chunk.put("tool_name", tre.getToolCallName());
            chunk.put("success", success);
            chunk.put("result", value);
            chunk.put("error", error);
            chunk.put("execution_time", executionTime);
            chunk.put("step", step);
            chunk.put("session_id", sessionId);
            step++;
            return chunk;
        }

        private Map<String, Object> emitText(AgentResultEvent are) {
            Msg result = are.getResult();
            String text = result == null ? null : result.getTextContent();
            if (text == null || text.isEmpty()) {
                return null;
            }
            Map<String, Object> chunk = new LinkedHashMap<>();
            chunk.put("type", "text");
            chunk.put("content", text);
            chunk.put("step", step);
            chunk.put("session_id", sessionId);
            return chunk;
        }

        private void toolCount(String name) {
            if (name != null) {
                toolsUsed.merge(name, 1, Integer::sum);
            }
        }

        List<Map<String, Object>> error(Throwable e) {
            errored = true;
            Map<String, Object> chunk = new LinkedHashMap<>();
            chunk.put("type", "error");
            chunk.put("error", e.getMessage() == null ? e.toString() : e.getMessage());
            chunk.put("session_id", sessionId);
            chunk.put("done", true);
            List<Map<String, Object>> out = new ArrayList<>();
            out.add(chunk);
            return out;
        }

        List<Map<String, Object>> finishChunks() {
            List<Map<String, Object>> out = new ArrayList<>();
            if (errored) {
                return out;
            }
            if (!sawResult) {
                log.warn("Agent stream ended without a final result; reporting an error");
                return error(new IllegalStateException("AgentScope stream ended before the final result"));
            }
            double totalTime = (System.nanoTime() - startNanos) / 1_000_000_000.0;
            Map<String, Object> summary = new LinkedHashMap<>();
            summary.put("type", AgentResponseType.SUMMARY);
            summary.put("session_id", sessionId);
            summary.put("total_time", Math.round(totalTime * 100.0) / 100.0);
            summary.put("iterations", step);
            summary.put("steps_count", reactSteps.size());
            summary.put("successful_calls", successfulCalls);
            summary.put("failed_calls", failedCalls);
            summary.put("tools_used", toolsUsed);
            summary.put("summary", buildSummaryText());
            out.add(summary);

            Map<String, Object> done = new LinkedHashMap<>();
            done.put("type", "done");
            done.put("session_id", sessionId);
            done.put("done", true);
            done.put("iterations", step);
            done.put("total_steps", reactSteps.size());
            out.add(done);
            return out;
        }

        private String buildSummaryText() {
            List<String> parts = new ArrayList<>();
            parts.add("任务完成");
            parts.add("共执行 " + step + " 次迭代");
            if (!toolsUsed.isEmpty()) {
                parts.add("使用了 " + toolsUsed.size() + " 个工具");
                List<String> toolSummary = new ArrayList<>();
                for (Map.Entry<String, Integer> e : toolsUsed.entrySet()) {
                    toolSummary.add(e.getKey() + "(" + e.getValue() + "次)");
                }
                parts.add("(" + String.join(", ", toolSummary) + ")");
            }
            if (failedCalls > 0) {
                parts.add("其中 " + failedCalls + " 次调用失败");
            }
            return String.join("，", parts);
        }

        private Object parseToJsonValue(String text) {
            if (text == null || text.isBlank()) {
                return null;
            }
            try {
                JsonNode tree = om.readTree(text);
                if (tree.isContainerNode()) {
                    return om.convertValue(tree, new TypeReference<Object>() {
                    });
                }
                return tree.asText();
            } catch (Exception e) {
                return text;
            }
        }

        private Object parseResult(String text) {
            if (text == null || text.isBlank()) {
                return Map.of();
            }
            try {
                JsonNode tree = om.readTree(text);
                if (tree.isContainerNode()) {
                    return om.convertValue(tree, new TypeReference<Object>() {
                    });
                }
                return Map.of("value", tree.asText());
            } catch (Exception e) {
                return Map.of("value", text);
            }
        }
    }
}
