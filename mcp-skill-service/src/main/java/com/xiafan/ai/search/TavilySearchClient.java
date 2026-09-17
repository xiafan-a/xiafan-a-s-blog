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
 * Tavily Search 客户端：{@code POST {app.tavily.api-url}/search}，Bearer 认证。
 *
 * <p>与 {@link McpWebSearchClient}（bing-cn-mcp）并列，作为 {@code web_search} 之外的另一个联网检索来源。
 * 参数名兼容 registry 里常见的 {@code num_results} 与 Tavily 原生的 {@code max_results}，缺省时回落到
 * {@code app.tavily.*} 配置；返回结果按 {@code content-max-length} 截断，避免整页正文进入模型上下文。</p>
 */
@Component
public class TavilySearchClient {

    private static final Logger log = LoggerFactory.getLogger(TavilySearchClient.class);

    private static final Set<String> SEARCH_DEPTHS = Set.of("advanced", "basic", "fast", "ultra-fast");
    private static final Set<String> TOPICS = Set.of("general", "news", "finance");
    private static final Set<String> TIME_RANGES = Set.of("day", "week", "month", "year", "d", "w", "m", "y");
    private static final List<String> QUERY_KEYS = List.of("query", "q", "search_query", "searchQuery", "keyword");
    private static final List<String> COUNT_KEYS = List.of("num_results", "numResults", "max_results", "maxResults",
            "count", "limit", "top_k", "topK");
    private static final int MAX_RESULTS = 20;

    private final TavilySearchProperties props;
    private final ObjectMapper om;
    private final HttpClient http;

    public TavilySearchClient(TavilySearchProperties props, ObjectMapper om) {
        this.props = props;
        this.om = om;
        this.http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    }

    /** 开关打开且配置了 API Key 才算可用；未配置时注册表不暴露 tavily_search。 */
    public boolean isConfigured() {
        return props.isEnabled() && props.getApiKey() != null && !props.getApiKey().isBlank();
    }

    /** app.tavily.enabled 原值，用于启动日志区分「被开关关掉」和「没填 Key」。 */
    public boolean isEnabled() {
        return props.isEnabled();
    }

    /** 打日志用的脱敏 Key：只留头尾，便于确认容器里拿到的是不是同一个 Key。 */
    public String maskedApiKey() {
        String key = props.getApiKey() == null ? "" : props.getApiKey().trim();
        if (key.isEmpty()) {
            return "(empty)";
        }
        if (key.length() <= 12) {
            return key.charAt(0) + "***";
        }
        return key.substring(0, 8) + "***" + key.substring(key.length() - 4);
    }

    public String apiUrl() {
        return searchUrl();
    }

    public int timeoutSeconds() {
        return Math.max(1, props.getTimeoutSeconds());
    }

    public int defaultNumResults() {
        return normalizeCount(props.getMaxResults());
    }

    public String defaultTopic() {
        String value = normalize(props.getTopic());
        return TOPICS.contains(value) ? value : "general";
    }

    public String defaultSearchDepth() {
        String value = normalize(props.getSearchDepth());
        return SEARCH_DEPTHS.contains(value) ? value : "basic";
    }

    public Map<String, Object> search(String query) throws Exception {
        return search(query, defaultNumResults());
    }

    public Map<String, Object> search(String query, int numResults) throws Exception {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("query", query);
        params.put("num_results", numResults);
        return search(params);
    }

    /**
     * 执行搜索。参数名兼容别名的写法：query / num_results(max_results) / topic / search_depth /
     * time_range / include_answer / include_domains / exclude_domains。
     *
     * @return 归一化结果：{@code query, answer?, results[{title,url,content,score,published_date?}], result_count, response_time}
     * @throws IllegalArgumentException query 为空
     * @throws IllegalStateException    未启用/未配置 Key/上游报错
     */
    public Map<String, Object> search(Map<String, Object> params) throws Exception {
        if (!props.isEnabled()) {
            throw new IllegalStateException("Tavily search is disabled (app.tavily.enabled=false)");
        }
        if (props.getApiKey() == null || props.getApiKey().isBlank()) {
            throw new IllegalStateException(
                    "Tavily API key is not configured; set app.tavily.api-key or TAVILY_API_KEY");
        }
        Map<String, Object> input = params == null ? Map.of() : params;
        String query = firstText(input, QUERY_KEYS);
        if (query.isBlank()) {
            throw new IllegalArgumentException("query must not be blank");
        }
        Map<String, Object> body = buildBody(input, query);
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(searchUrl()))
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
            throw new IllegalStateException("Tavily search request failed: " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.interrupted();
            throw new IllegalStateException("Tavily search interrupted", e);
        }
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IllegalStateException(
                    "Tavily API returned HTTP " + response.statusCode() + ": " + truncate(response.body(), 500));
        }
        Map<String, Object> result = normalizeResponse(response.body(), query);
        log.debug("tavily_search '{}' returned {} result(s)", query, result.get("result_count"));
        return result;
    }

    // ============================================ request ============================================

    private Map<String, Object> buildBody(Map<String, Object> params, String query) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("query", query);
        String depth = firstText(params, List.of("search_depth", "searchDepth", "depth"));
        body.put("search_depth", SEARCH_DEPTHS.contains(normalize(depth)) ? normalize(depth) : defaultSearchDepth());
        String topic = firstText(params, List.of("topic", "category"));
        body.put("topic", TOPICS.contains(normalize(topic)) ? normalize(topic) : defaultTopic());
        body.put("max_results", normalizeCount(intParam(params, COUNT_KEYS, defaultNumResults())));
        Object includeAnswer = params.get("include_answer");
        body.put("include_answer", includeAnswer instanceof Boolean value ? value : props.isIncludeAnswer());
        String timeRange = firstText(params, List.of("time_range", "timeRange"));
        if (timeRange.isBlank()) {
            timeRange = props.getTimeRange() == null ? "" : props.getTimeRange().trim();
        }
        if (!timeRange.isBlank() && TIME_RANGES.contains(normalize(timeRange))) {
            body.put("time_range", normalize(timeRange));
        }
        List<String> includeDomains = domains(params.get("include_domains"));
        if (!includeDomains.isEmpty()) {
            body.put("include_domains", includeDomains);
        }
        List<String> excludeDomains = domains(params.get("exclude_domains"));
        if (!excludeDomains.isEmpty()) {
            body.put("exclude_domains", excludeDomains);
        }
        return body;
    }

    private String searchUrl() {
        String base = props.getApiUrl() == null || props.getApiUrl().isBlank()
                ? "https://api.tavily.com" : props.getApiUrl().trim();
        while (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        return base.endsWith("/search") ? base : base + "/search";
    }

    // ============================================ response ============================================

    private Map<String, Object> normalizeResponse(String rawBody, String query) {
        JsonNode root;
        try {
            root = om.readTree(rawBody == null ? "" : rawBody);
        } catch (JacksonException e) {
            throw new IllegalStateException("Tavily returned an unparseable response: " + truncate(rawBody, 200), e);
        }
        if (root == null || root.isNull() || root.isMissingNode()) {
            throw new IllegalStateException("Tavily returned an empty response");
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("query", text(root, "query").isBlank() ? query : text(root, "query"));
        String answer = text(root, "answer");
        if (!answer.isBlank()) {
            result.put("answer", truncate(answer, Math.max(props.getContentMaxLength() * 2, 600)));
        }
        List<Map<String, Object>> results = new ArrayList<>();
        JsonNode items = root.get("results");
        if (items != null && items.isArray()) {
            for (JsonNode item : items) {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("title", text(item, "title", "name"));
                row.put("url", text(item, "url", "link"));
                row.put("content", truncate(text(item, "content", "snippet", "description"),
                        props.getContentMaxLength()));
                if (item.hasNonNull("score")) {
                    row.put("score", item.get("score").asDouble());
                }
                String published = text(item, "published_date", "publishedDate");
                if (!published.isBlank()) {
                    row.put("published_date", published);
                }
                results.add(row);
            }
        }
        result.put("results", results);
        result.put("result_count", results.size());
        if (root.hasNonNull("response_time")) {
            result.put("response_time", root.get("response_time").asDouble());
        }
        return result;
    }

    // ============================================ helpers ============================================

    private static List<String> domains(Object raw) {
        List<String> domains = new ArrayList<>();
        if (raw instanceof List<?> list) {
            for (Object item : list) {
                addDomain(domains, item == null ? "" : String.valueOf(item).trim());
            }
        } else if (raw != null) {
            for (String piece : String.valueOf(raw).split(",")) {
                addDomain(domains, piece.trim());
            }
        }
        return domains;
    }

    private static void addDomain(List<String> domains, String domain) {
        if (!domain.isEmpty() && !domains.contains(domain)) {
            domains.add(domain);
        }
    }

    private static String firstText(Map<String, Object> params, List<String> keys) {
        for (String key : keys) {
            Object value = params.get(key);
            if (value != null) {
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

    private static int normalizeCount(int value) {
        return Math.max(0, Math.min(MAX_RESULTS, value <= 0 ? 5 : value));
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
