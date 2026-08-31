package com.xiafan.agent.service.llm;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiafan.agent.config.AppProperties;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * OpenAI-compatible HTTP client mirroring chatService/ragOptimizationService/embeddingService
 * in fastApiProject (same base URL, Bearer auth, /chat/completions and /embeddings endpoints).
 */
@Component
public class OpenAiClient {

    private final AppProperties props;
    private final ObjectMapper om;
    private final HttpClient http;

    public OpenAiClient(AppProperties props, ObjectMapper om) {
        this.props = props;
        this.om = om;
        this.http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    public AppProperties props() {
        return props;
    }

    public String baseUrl() {
        String url = props.getApi().getUrl();
        while (url.endsWith("/")) {
            url = url.substring(0, url.length() - 1);
        }
        return url;
    }

    public String chatCompletionsUrl() {
        return baseUrl() + "/chat/completions";
    }

    /** Non-streaming chat completion. Throws on HTTP error / unparseable body. */
    public JsonNode chatCompletion(String model, List<Map<String, Object>> messages, Double temperature,
                                   Map<String, Object> extraBody, int timeoutSeconds) {
        Map<String, Object> body = new HashMap<>();
        body.put("model", model);
        body.put("messages", messages);
        if (temperature != null) {
            body.put("temperature", temperature);
        }
        if (extraBody != null) {
            body.putAll(extraBody);
        }
        HttpResponse<String> resp = postAndGet(chatCompletionsUrl(), body, timeoutSeconds);
        if (resp.statusCode() < 200 || resp.statusCode() >= 300) {
            throw new RuntimeException("LLM API error " + resp.statusCode() + ": " + resp.body());
        }
        return parseTree(resp.body());
    }

    /** Streaming chat completion; emits each SSE {@code data:} payload (excluding "[DONE]"). */
    public Flux<JsonNode> streamChat(String model, List<Map<String, Object>> messages, Double temperature,
                                     Map<String, Object> extraBody) {
        Map<String, Object> body = new HashMap<>();
        body.put("model", model);
        body.put("messages", messages);
        body.put("stream", true);
        if (temperature != null) {
            body.put("temperature", temperature);
        }
        if (extraBody != null) {
            body.putAll(extraBody);
        }
        HttpRequest request = postJsonRequest(chatCompletionsUrl(), body, Duration.ofSeconds(300));
        return streamSse(request);
    }

    /** Embeddings endpoint mirroring EmbeddingService.encode. */
    public JsonNode embeddings(List<String> texts, String model, Integer dimension) {
        Map<String, Object> body = new HashMap<>();
        body.put("model", model);
        body.put("input", texts);
        if (dimension != null) {
            body.put("dimensions", dimension);
        }
        HttpResponse<String> resp = postAndGet(baseUrl() + "/embeddings", body, 120);
        if (resp.statusCode() < 200 || resp.statusCode() >= 300) {
            throw new RuntimeException("Embedding API error " + resp.statusCode() + ": " + resp.body());
        }
        return parseTree(resp.body());
    }

    /** Generic POST returning the raw response (any status). Authorization header always attached. */
    public HttpResponse<String> postAndGet(String url, Map<String, Object> body, int timeoutSeconds) {
        HttpRequest request = postJsonRequest(url, body, Duration.ofSeconds(timeoutSeconds));
        try {
            return http.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (IOException | InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    public <T> T fromJson(String json, JavaType type) {
        try {
            return om.readValue(json, type);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("JSON parse failed: " + json, e);
        }
    }

    public JsonNode parseTree(String json) {
        try {
            return om.readTree(json);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("JSON parse failed: " + json, e);
        }
    }

    public ObjectMapper objectMapper() {
        return om;
    }

    private HttpRequest postJsonRequest(String url, Map<String, Object> body, Duration timeout) {
        String json;
        try {
            json = om.writeValueAsString(body);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
        return HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(timeout)
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + props.getApi().getKey())
                .POST(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8))
                .build();
    }

    private Flux<JsonNode> streamSse(HttpRequest request) {
        return Flux.create(sink -> {
            try {
                HttpResponse<InputStream> resp = http.send(request, HttpResponse.BodyHandlers.ofInputStream());
                if (resp.statusCode() < 200 || resp.statusCode() >= 300) {
                    String text = new String(resp.body().readAllBytes(), StandardCharsets.UTF_8);
                    sink.error(new RuntimeException("LLM API error " + resp.statusCode() + ": " + text));
                    return;
                }
                Charset charset = charsetFromContentType(resp.headers().firstValue("content-type").orElse(""));
                BufferedReader reader = new BufferedReader(new InputStreamReader(resp.body(), charset));
                String line;
                String pending = "";
                while ((line = reader.readLine()) != null) {
                    String data = null;
                    if (line.startsWith("data:")) {
                        data = line.substring(5).trim();
                    } else if (line.isEmpty() && !pending.isEmpty()) {
                        data = pending;
                        pending = "";
                    } else if (line.startsWith("{")) {
                        // Some servers emit raw JSON lines without the data: prefix
                        data = line;
                    }
                    if (data == null || data.isEmpty()) {
                        continue;
                    }
                    if ("[DONE]".equals(data)) {
                        break;
                    }
                    sink.next(parseTree(data));
                }
                sink.complete();
            } catch (IOException e) {
                sink.error(e);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                sink.error(e);
            }
        });
    }

    private static Charset charsetFromContentType(String contentType) {
        int idx = contentType.indexOf("charset=");
        if (idx >= 0) {
            try {
                return Charset.forName(contentType.substring(idx + 8).trim());
            } catch (Exception ignored) {
                // fall through
            }
        }
        return StandardCharsets.UTF_8;
    }

    /** Extracts the assistant text content from a chat completion response, or null if absent. */
    public static String contentFrom(JsonNode completion) {
        JsonNode choices = completion.path("choices");
        if (!choices.isArray() || choices.isEmpty()) {
            return null;
        }
        JsonNode message = choices.get(0).path("message");
        if (message.hasNonNull("content") && message.get("content").isTextual()) {
            return message.get("content").asText();
        }
        // streamed deltas
        return message.path("content").asText(null);
    }

    /** Extracts tool_calls from a chat completion response (OpenAI format), or empty list. */
    public static List<JsonNode> toolCallsFrom(JsonNode completion) {
        List<JsonNode> calls = new ArrayList<>();
        JsonNode choices = completion.path("choices");
        if (!choices.isArray() || choices.isEmpty()) {
            return calls;
        }
        JsonNode toolCalls = choices.get(0).path("message").path("tool_calls");
        if (toolCalls.isArray()) {
            toolCalls.forEach(calls::add);
        }
        return calls;
    }
}