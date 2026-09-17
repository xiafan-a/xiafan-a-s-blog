package com.xiafan.ai.search;

import com.xiafan.ai.config.TavilySearchProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Tavily Extract 客户端：{@code POST {app.tavily.api-url}/extract}，Bearer 认证。
 *
 * <p>与 {@link TavilySearchClient} 共用 {@link TavilySearchProperties} 里的地址、Key 与超时配置，一次可抽取
 * 1–20 个 URL（批量抽取的选项对所有 URL 生效）。HTTP 200 也可能只成功一部分，因此
 * {@code results} 与 {@code failed_results} 两个数组都要看：成功项返回 {@code raw_content}
 * （按 {@code extract-max-content-length} 截断），失败项返回 {@code url}/{@code error}。</p>
 */
@Component
public class TavilyExtractClient {

    private static final Logger log = LoggerFactory.getLogger(TavilyExtractClient.class);

    private static final Set<String> DEPTHS = Set.of("basic", "advanced");
    private static final Set<String> FORMATS = Set.of("markdown", "text");
    private static final List<String> URL_KEYS = List.of("urls", "url", "links", "targets");
    private static final List<String> QUERY_KEYS = List.of("query", "q", "intent");
    private static final int MAX_URLS = 20;

    private final TavilySearchProperties props;
    private final ObjectMapper om;
    private final HttpClient http;

    public TavilyExtractClient(TavilySearchProperties props, ObjectMapper om) {
        this.props = props;
        this.om = om;
        this.http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    }

    /** 开关打开且配置了 API Key 才算可用；未配置时注册表不暴露 tavily_extract。 */
    public boolean isConfigured() {
        return props.isEnabled() && props.getApiKey() != null && !props.getApiKey().isBlank();
    }

    public String defaultExtractDepth() {
        String value = normalize(props.getExtractDepth());
        return DEPTHS.contains(value) ? value : "basic";
    }

    public String defaultFormat() {
        String value = normalize(props.getExtractFormat());
        return FORMATS.contains(value) ? value : "markdown";
    }

    public Map<String, Object> extract(String url) throws Exception {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("urls", url);
        return extract(params);
    }

    /**
     * 抽取网页正文。参数名兼容常见别名：urls(url/links) / query / chunks_per_source /
     * extract_depth / format / include_images / timeout。
     *
     * @return 归一化结果：{@code results[{url,title?,raw_content,content_length,truncated?,images?,favicon?}],
     *         result_count, failed_results[{url,error}], failed_count, response_time}
     * @throws IllegalArgumentException urls 为空或超过 20 条
     * @throws IllegalStateException    未启用/未配置 Key/上游报错
     */
    public Map<String, Object> extract(Map<String, Object> params) throws Exception {
        if (!props.isEnabled()) {
            throw new IllegalStateException("Tavily extract is disabled (app.tavily.enabled=false)");
        }
        if (props.getApiKey() == null || props.getApiKey().isBlank()) {
            throw new IllegalStateException(
                    "Tavily API key is not configured; set app.tavily.api-key or TAVILY_API_KEY");
        }
        Map<String, Object> input = params == null ? Map.of() : params;
        List<String> urls = urls(firstRawValue(input, URL_KEYS));
        if (urls.isEmpty()) {
            throw new IllegalArgumentException("urls must not be blank");
        }
        if (urls.size() > MAX_URLS) {
            throw new IllegalArgumentException("at most " + MAX_URLS + " urls per request, got " + urls.size());
        }
        Map<String, Object> body = buildBody(input, urls);
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(endpoint()))
                .timeout(Duration.ofSeconds(Math.max(1, props.getTimeoutSeconds())))
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .header("Authorization", "Bearer " + props.getApiKey().trim())
                .POST(HttpRequest.BodyPublishers.ofString(om.writeValueAsString(body), StandardCharsets.UTF_8))
                .build();
        HttpResponse<String> response;
        try {
            response = http.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new IllegalStateException("Tavily extract request failed: " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.interrupted();
            throw new IllegalStateException("Tavily extract interrupted", e);
        }
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IllegalStateException(
                    "Tavily API returned HTTP " + response.statusCode() + ": " + truncate(response.body(), 500));
        }
        Map<String, Object> result = normalizeResponse(response.body());
        log.debug("tavily_extract extracted {} url(s), {} failed", result.get("result_count"),
                result.get("failed_count"));
        return result;
    }

    // ============================================ request ============================================

    private Map<String, Object> buildBody(Map<String, Object> params, List<String> urls) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("urls", urls);
        String depth = normalize(firstValue(params, List.of("extract_depth", "extractDepth", "depth")));
        body.put("extract_depth", DEPTHS.contains(depth) ? depth : defaultExtractDepth());
        String format = normalize(firstValue(params, List.of("format", "output_format", "outputFormat")));
        body.put("format", FORMATS.contains(format) ? format : defaultFormat());
        String query = firstValue(params, QUERY_KEYS);
        if (!query.isBlank()) {
            body.put("query", query);
            // chunks_per_source 只在给了 query 时生效
            int chunks = intParam(params, List.of("chunks_per_source", "chunksPerSource"), props.getChunksPerSource());
            body.put("chunks_per_source", Math.max(1, Math.min(5, chunks)));
        }
        Boolean includeImages = boolParam(params, List.of("include_images", "includeImages"));
        if (includeImages != null) {
            body.put("include_images", includeImages);
        }
        Double timeout = doubleParam(params, List.of("timeout", "timeout_seconds", "timeoutSeconds"));
        if (timeout != null) {
            body.put("timeout", Math.max(1.0, Math.min(60.0, timeout)));
        }
        return body;
    }

    private String endpoint() {
        String base = props.getApiUrl() == null || props.getApiUrl().isBlank()
                ? "https://api.tavily.com" : props.getApiUrl().trim();
        while (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        return base.endsWith("/extract") ? base : base + "/extract";
    }

    // ============================================ response ============================================

    private Map<String, Object> normalizeResponse(String rawBody) {
        JsonNode root;
        try {
            root = om.readTree(rawBody == null ? "" : rawBody);
        } catch (JacksonException e) {
            throw new IllegalStateException("Tavily returned an unparseable response: " + truncate(rawBody, 200), e);
        }
        if (root == null || root.isNull() || root.isMissingNode()) {
            throw new IllegalStateException("Tavily returned an empty response");
        }
        int maxLength = Math.max(1, props.getExtractMaxContentLength());
        List<Map<String, Object>> results = new ArrayList<>();
        JsonNode items = root.get("results");
        if (items != null && items.isArray()) {
            for (JsonNode item : items) {
                String content = text(item, "raw_content", "content");
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("url", text(item, "url"));
                String title = text(item, "title");
                if (!title.isBlank()) {
                    row.put("title", title);
                }
                row.put("content_length", content.length());
                row.put("raw_content", truncate(content, maxLength));
                if (content.length() > maxLength) {
                    row.put("truncated", true);
                }
                JsonNode images = item.get("images");
                if (images != null && images.isArray() && !images.isEmpty()) {
                    List<String> urls = new ArrayList<>();
                    for (JsonNode image : images) {
                        urls.add(image.asText());
                    }
                    row.put("images", urls);
                }
                String favicon = text(item, "favicon");
                if (!favicon.isBlank()) {
                    row.put("favicon", favicon);
                }
                results.add(row);
            }
        }
        List<Map<String, Object>> failed = new ArrayList<>();
        JsonNode failures = root.get("failed_results");
        if (failures != null && failures.isArray()) {
            for (JsonNode item : failures) {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("url", text(item, "url"));
                row.put("error", text(item, "error"));
                failed.add(row);
            }
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("results", results);
        result.put("result_count", results.size());
        result.put("failed_results", failed);
        result.put("failed_count", failed.size());
        if (root.hasNonNull("response_time")) {
            result.put("response_time", root.get("response_time").asDouble());
        }
        return result;
    }

    // ============================================ helpers ============================================

    /** URL 列表：接受数组，也接受单个字符串（按逗号/空白/换行切分），去重并保持顺序。 */
    private static List<String> urls(Object raw) {
        List<String> urls = new ArrayList<>();
        if (raw instanceof List<?> list) {
            for (Object item : list) {
                addUrl(urls, item == null ? "" : String.valueOf(item).trim());
            }
        } else if (raw != null) {
            for (String piece : String.valueOf(raw).split("[,\\s]+")) {
                addUrl(urls, piece.trim());
            }
        }
        return urls;
    }

    private static void addUrl(List<String> urls, String url) {
        if (!url.isEmpty() && !urls.contains(url)) {
            urls.add(url);
        }
    }

    /** 取第一个非空别名原值（不清洗，保留数组形态供 urls 解析）。 */
    private static Object firstRawValue(Map<String, Object> params, List<String> keys) {
        for (String key : keys) {
            Object value = params.get(key);
            if (value == null) {
                continue;
            }
            if (value instanceof String text && text.isBlank()) {
                continue;
            }
            if (value instanceof List<?> list && list.isEmpty()) {
                continue;
            }
            return value;
        }
        return null;
    }

    private static String firstValue(Map<String, Object> params, List<String> keys) {
        for (String key : keys) {
            Object value = params.get(key);
            if (value != null) {
                if (value instanceof List<?> list) {
                    return list.isEmpty() ? "" : String.valueOf(list.get(0));
                }
                String text = String.valueOf(value).trim();
                if (!text.isEmpty()) {
                    return text;
                }
            }
        }
        return "";
    }

    private static int intParam(Map<String, Object> params, List<String> keys, int fallback) {
        for (String key : keys) {
            Object value = params.get(key);
            if (value instanceof Number number) {
                return number.intValue();
            }
            if (value instanceof String text && !text.isBlank()) {
                try {
                    return (int) Double.parseDouble(text.trim());
                } catch (NumberFormatException ignored) {
                    // try next alias
                }
            }
        }
        return fallback;
    }

    private static Double doubleParam(Map<String, Object> params, List<String> keys) {
        for (String key : keys) {
            Object value = params.get(key);
            if (value instanceof Number number) {
                return number.doubleValue();
            }
            if (value instanceof String text && !text.isBlank()) {
                try {
                    return Double.parseDouble(text.trim());
                } catch (NumberFormatException ignored) {
                    // try next alias
                }
            }
        }
        return null;
    }

    private static Boolean boolParam(Map<String, Object> params, List<String> keys) {
        for (String key : keys) {
            Object value = params.get(key);
            if (value instanceof Boolean bool) {
                return bool;
            }
            if (value instanceof String text && !text.isBlank()) {
                return Boolean.parseBoolean(text.trim());
            }
        }
        return null;
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private static String text(JsonNode node, String... keys) {
        for (String key : keys) {
            JsonNode value = node.get(key);
            if (value != null && !value.isNull() && !value.isMissingNode()) {
                String text = value.asText();
                if (text != null && !text.isBlank()) {
                    return text;
                }
            }
        }
        return "";
    }

    private static String truncate(String value, int maxLength) {
        if (value == null) {
            return "";
        }
        int max = Math.max(1, maxLength);
        return value.length() <= max ? value : value.substring(0, max) + "...";
    }
}
