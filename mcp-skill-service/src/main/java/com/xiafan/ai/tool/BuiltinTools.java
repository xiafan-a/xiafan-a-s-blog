package com.xiafan.ai.tool;

import com.xiafan.ai.config.SearchProperties;
import com.xiafan.ai.search.McpWebSearchClient;
import com.xiafan.ai.search.TavilyExtractClient;
import com.xiafan.ai.search.TavilySearchClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class BuiltinTools {

    private static final Logger log = LoggerFactory.getLogger(BuiltinTools.class);

    public record Spec(ToolDefinition definition, ToolExecutor executor) {
    }

    private static final List<String> READABLE_TEXT_EXTS =
            List.of(".txt", ".md", ".csv", ".json", ".xml", ".html");
    private static final List<String> WRITABLE_EXTS =
            List.of(".txt", ".md", ".json", ".csv", ".xml", ".html", ".js", ".css", ".py", ".java", ".c", ".cpp", ".h");

    /** Tavily（默认搜索源）在结果里的 provider 标记。 */
    private static final String PROVIDER_TAVILY = "tavily";
    /** 原 npx 启动的 bing-cn-mcp MCP 搜索，现在是备选源。 */
    private static final String PROVIDER_BING = "bing-mcp";

    private final McpWebSearchClient webSearch;
    private final TavilySearchClient tavilySearch;
    private final TavilyExtractClient tavilyExtract;
    private final SearchProperties searchProps;
    private final JdbcTemplate jdbc;
    private final ObjectMapper om;
    private final HttpClient http;

    public BuiltinTools(McpWebSearchClient webSearch, TavilySearchClient tavilySearch,
                        TavilyExtractClient tavilyExtract, SearchProperties searchProps,
                        JdbcTemplate jdbc, ObjectMapper om) {
        this.webSearch = webSearch;
        this.tavilySearch = tavilySearch;
        this.tavilyExtract = tavilyExtract;
        this.searchProps = searchProps;
        this.jdbc = jdbc;
        this.om = om;
        this.http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    }

    public List<Spec> all() {
        List<Spec> specs = new ArrayList<>();
        specs.add(new Spec(def("file_read", "Read local file",
                "Read a local text file and return its content.",
                "file", List.of(
                        param("file_path", "string", "Absolute path to the file to read"),
                        opt("max_chars", "integer", "Maximum number of characters to return", 10000))),
                this::executeFileRead));
        specs.add(new Spec(def("web_search", "Web search",
                "Search the web when the latest or external information is needed. Tavily is used by default; "
                        + "the Bing MCP source is only a fallback when Tavily is unavailable.",
                "web", List.of(
                        param("query", "string", "Search query or question"),
                        opt("num_results", "integer", "Number of results to return", 5),
                        param("provider", "string",
                                "Force a source: tavily (default), bing, or auto to use the configured order",
                                false, List.of("tavily", "bing", "auto")),
                        param("topic", "string", "Tavily search category: general, news or finance", false,
                                List.of("general", "news", "finance")),
                        param("search_depth", "string",
                                "advanced is more accurate but costs 2 credits; basic, fast and ultra-fast cost 1",
                                false, List.of("advanced", "basic", "fast", "ultra-fast")),
                        param("time_range", "string", "Only return results published within this window", false,
                                List.of("day", "week", "month", "year")),
                        opt("include_domains", "string",
                                "Comma separated allow-list of domains, for example gov.cn,reuters.com", null),
                        opt("exclude_domains", "string", "Comma separated deny-list of domains", null))),
                this::executeWebSearch));
        if (tavilySearch.isConfigured()) {
            specs.add(new Spec(def("tavily_search", "Tavily web search",
                    "Search the web with Tavily for the latest or external information; "
                            + "returns titles, links and snippets. web_search already defaults to Tavily, "
                            + "so prefer web_search unless you want to pin these Tavily options explicitly.",
                    "web", List.of(
                            param("query", "string", "Search query or question"),
                            opt("num_results", "integer", "Number of results to return",
                                    tavilySearch.defaultNumResults()),
                            param("topic", "string", "Search category: general, news or finance", false,
                                    List.of("general", "news", "finance")),
                            param("search_depth", "string",
                                    "advanced is more accurate but costs 2 credits; basic, fast and ultra-fast cost 1",
                                    false, List.of("advanced", "basic", "fast", "ultra-fast")),
                            param("time_range", "string", "Only return results published within this window", false,
                                    List.of("day", "week", "month", "year")),
                            opt("include_domains", "string",
                                    "Comma separated allow-list of domains, for example gov.cn,reuters.com", null),
                            opt("exclude_domains", "string", "Comma separated deny-list of domains", null))),
                    this::executeTavilySearch));
        }
        if (tavilyExtract.isConfigured()) {
            specs.add(new Spec(def("tavily_extract", "Tavily extract",
                    "Extract the readable content of one or more URLs with Tavily (up to 20 per call); "
                            + "use it after tavily_search when a page needs to be read in full.",
                    "web", List.of(
                            param("urls", "string",
                                    "One URL, or several URLs separated by commas/newlines (max 20)"),
                            opt("query", "string",
                                    "What you are looking for; when set, content chunks are reranked and "
                                            + "trimmed by relevance", null),
                            param("extract_depth", "string",
                                    "basic is faster; advanced also returns tables and embedded content",
                                    false, List.of("basic", "advanced")),
                            param("format", "string", "markdown (default) or plain text", false,
                                    List.of("markdown", "text")),
                            opt("chunks_per_source", "integer",
                                    "Chunks per source when query is provided, 1-5", 3),
                            opt("include_images", "boolean", "Also return image URLs found on the page", false))),
                    this::executeTavilyExtract));
        }
        specs.add(new Spec(def("file_write", "Write local file",
                "Write content to a local text file, creating parent directories if needed.",
                "file", List.of(
                        param("file_path", "string", "Absolute path to the file to write"),
                        param("content", "string", "File content"),
                        opt("encoding", "string", "File encoding", "utf-8"))),
                this::executeFileWrite));
        specs.add(new Spec(def("knowledge_retrieve", "Retrieve knowledge chunks",
                "Retrieve neighboring knowledge chunks from the knowledge base.",
                "knowledge", List.of(
                        param("knowledge_base_id", "integer", "Knowledge base id"),
                        param("chunk_index", "integer", "Reference chunk index"),
                        opt("direction", "string", "before, after or both", "after"),
                        opt("limit", "integer", "Maximum chunk count", 5))),
                this::executeKnowledgeRetrieve));
        specs.add(new Spec(def("web_open", "Open web page",
                "Open a URL and return the page title.",
                "web", List.of(
                        param("url", "string", "URL beginning with http:// or https://"))),
                this::executeWebOpen));
        specs.add(new Spec(def("web_scrape", "Fetch page content",
                "Fetch a web page and extract its readable text.",
                "web", List.of(
                        param("url", "string", "URL to fetch"),
                        opt("selector", "string", "Optional CSS selector", null))),
                this::executeWebScrape));
        specs.add(new Spec(def("web_click", "Click element",
                "Simulate clicking an element identified by CSS selector.",
                "web", List.of(
                        param("selector", "string", "CSS selector"))),
                this::executeWebClick));
        specs.add(new Spec(def("web_input", "Fill input",
                "Simulate typing into an element identified by CSS selector.",
                "web", List.of(
                        param("selector", "string", "CSS selector"),
                        param("value", "string", "Text value"))),
                this::executeWebInput));
        specs.add(new Spec(def("web_scroll", "Scroll page",
                "Simulate scrolling the active page.",
                "web", List.of(
                        param("direction", "string", "Scroll direction", true, List.of("up", "down")),
                        opt("pixels", "integer", "Scroll distance in pixels", 500))),
                this::executeWebScroll));
        specs.add(new Spec(def("get_Date", "Get current date",
                "Get the current date.", "date", List.of()), this::executeGetDate));
        return specs;
    }

    private Object executeFileRead(Map<String, Object> params) throws Exception {
        String filePath = strParam(params, "file_path", "");
        int maxChars = intParam(params, "max_chars", 10000);
        if (filePath.isEmpty()) {
            throw new IllegalArgumentException("file_path must not be blank");
        }
        Path path = Path.of(filePath).toAbsolutePath();
        if (!Files.exists(path)) {
            throw new IOException("File does not exist: " + path);
        }
        if (!Files.isRegularFile(path)) {
            throw new IllegalArgumentException("Path is not a file: " + path);
        }
        String ext = extOf(path.getFileName().toString());
        String content;
        if (READABLE_TEXT_EXTS.contains(ext)) {
            content = readText(path);
        } else if (".pdf".equals(ext) || ".docx".equals(ext)) {
            throw new UnsupportedOperationException("Parsing " + ext + " is not supported; save as text first");
        } else {
            throw new IllegalArgumentException("Unsupported file type: " + ext + ". Supported: " + READABLE_TEXT_EXTS);
        }
        if (content.length() > maxChars) {
            content = content.substring(0, maxChars) + "\n\n... (content truncated from " + content.length() + " chars)";
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("file_name", path.getFileName().toString());
        result.put("file_path", path.toString());
        result.put("file_size", Files.size(path));
        result.put("file_type", ext);
        result.put("content_length", content.length());
        result.put("content", content);
        return result;
    }

    /**
     * 统一搜索入口：默认用 Tavily，原本由 npx 启动的 bing-cn-mcp 只作备选。
     *
     * <p>顺序由 {@code app.search.provider} 与 {@code app.search.fallback-enabled} 决定，也可以被单次调用里的
     * {@code provider} 参数覆盖（{@code tavily} / {@code bing} / {@code auto}）。主源不可用或报错时自动切到备选，
     * 并在结果里用 {@code provider}、{@code fallback_from}、{@code attempts} 说明实际用了谁、为什么切换；
     * 所有源都失败时与旧行为一致，返回 {@code error} 字段而不是抛错。</p>
     */
    private Object executeWebSearch(Map<String, Object> params) {
        Map<String, Object> input = params == null ? Map.of() : params;
        String query = strParam(input, "query", "");
        int numResults = intParam(input, "num_results", 5);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("query", query);

        List<String> order = searchOrder(strParam(input, "provider", null));
        List<Map<String, Object>> attempts = new ArrayList<>();
        for (int i = 0; i < order.size(); i++) {
            String provider = order.get(i);
            boolean last = i == order.size() - 1;
            try {
                searchWith(provider, input, query, numResults, result);
                result.put("provider", provider);
                if (i > 0) {
                    result.put("fallback_from", order.get(0));
                    result.put("fallback_reason", attempts.isEmpty() ? "primary source unavailable"
                            : attempts.get(0).get("error"));
                }
                if (!attempts.isEmpty()) {
                    result.put("attempts", attempts);
                }
                return result;
            } catch (Exception e) {
                String message = e.getMessage() == null ? e.toString() : e.getMessage();
                log.warn("web_search via {} failed: {}", provider, message);
                Map<String, Object> attempt = new LinkedHashMap<>();
                attempt.put("provider", provider);
                attempt.put("error", message);
                attempts.add(attempt);
                if (last) {
                    result.put("provider", provider);
                    result.put("results", List.of());
                    result.put("result_count", 0);
                    result.put("error", message);
                    if (attempts.size() > 1) {
                        result.put("attempts", attempts);
                    }
                    return result;
                }
            }
        }
        // searchOrder 至少返回一个源，正常情况下上面已经在成功或最后一次失败时返回。
        result.put("provider", PROVIDER_BING);
        result.put("results", List.of());
        result.put("result_count", 0);
        result.put("error", "no search provider is available");
        return result;
    }

    /** 实际执行单个搜索源，成功时把归一化结果写进 {@code result}。 */
    private void searchWith(String provider, Map<String, Object> params, String query, int numResults,
                            Map<String, Object> result) throws Exception {
        if (PROVIDER_TAVILY.equals(provider)) {
            Map<String, Object> searched = tavilySearch.search(params);
            result.put("results", searched.get("results"));
            result.put("result_count", searched.get("result_count"));
            Object answer = searched.get("answer");
            if (answer != null) {
                result.put("answer", answer);
            }
            Object responseTime = searched.get("response_time");
            if (responseTime != null) {
                result.put("response_time", responseTime);
            }
            return;
        }
        List<Map<String, Object>> rows = bingRows(webSearch.search(query, numResults), numResults);
        result.put("results", rows);
        result.put("result_count", rows.size());
    }

    /**
     * 计算候选搜索源顺序。
     *
     * <p>主源优先；{@code app.search.fallback-enabled=true} 时补上另一个源兜底。若主源本来就没配好
     * （例如 Tavily 没有 Key），则直接把它从候选里去掉，剩下的源顶上，避免每次都白跑一次失败请求。</p>
     */
    private List<String> searchOrder(String override) {
        boolean tavilyReady = tavilySearch.isConfigured();
        String configured = searchProps == null ? null : searchProps.getProvider();
        boolean tavilyFirst = !"bing".equalsIgnoreCase(configured == null ? "" : configured.trim());
        String requested = override == null ? "" : override.trim().toLowerCase(Locale.ROOT);
        if ("tavily".equals(requested)) {
            tavilyFirst = true;
        } else if ("bing".equals(requested)) {
            tavilyFirst = false;
        }
        boolean fallback = searchProps == null || searchProps.isFallbackEnabled();

        List<String> order = new ArrayList<>();
        if (tavilyFirst) {
            if (tavilyReady) {
                order.add(PROVIDER_TAVILY);
            }
            if (fallback || order.isEmpty()) {
                order.add(PROVIDER_BING);
            }
        } else {
            order.add(PROVIDER_BING);
            if (tavilyReady && fallback) {
                order.add(PROVIDER_TAVILY);
            }
        }
        return order;
    }

    /** 把 bing-cn-mcp 的行（title/url/snippet）对齐成 Tavily 的字段名，调用方不必区分来源。 */
    private static List<Map<String, Object>> bingRows(List<Map<String, Object>> rows, int numResults) {
        int limit = numResults <= 0 ? rows.size() : Math.min(numResults, rows.size());
        List<Map<String, Object>> normalized = new ArrayList<>();
        for (int i = 0; i < limit; i++) {
            Map<String, Object> row = rows.get(i);
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("title", row.getOrDefault("title", ""));
            out.put("url", row.getOrDefault("url", ""));
            Object content = row.get("content");
            if (content == null) {
                content = row.getOrDefault("snippet", "");
            }
            out.put("content", content);
            normalized.add(out);
        }
        return normalized;
    }

    /** Tavily 搜索：与 web_search 一致，失败时返回 error 字段而不是让整轮调用抛错。 */
    private Object executeTavilySearch(Map<String, Object> params) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("query", strParam(params, "query", ""));
        try {
            Map<String, Object> searched = tavilySearch.search(params);
            result.put("results", searched.get("results"));
            result.put("result_count", searched.get("result_count"));
            Object answer = searched.get("answer");
            if (answer != null) {
                result.put("answer", answer);
            }
            Object responseTime = searched.get("response_time");
            if (responseTime != null) {
                result.put("response_time", responseTime);
            }
        } catch (Exception e) {
            String message = e.getMessage() == null ? e.toString() : e.getMessage();
            log.warn("tavily_search failed: {}", message);
            result.put("results", List.of());
            result.put("error", message);
        }
        return result;
    }

    /** Tavily extract: like web_search, failures come back as an error field; HTTP 200 may be partial. */
    private Object executeTavilyExtract(Map<String, Object> params) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("urls", params.get("urls"));
        try {
            Map<String, Object> extracted = tavilyExtract.extract(params);
            result.put("results", extracted.get("results"));
            result.put("result_count", extracted.get("result_count"));
            result.put("failed_results", extracted.get("failed_results"));
            result.put("failed_count", extracted.get("failed_count"));
            Object responseTime = extracted.get("response_time");
            if (responseTime != null) {
                result.put("response_time", responseTime);
            }
        } catch (Exception e) {
            String message = e.getMessage() == null ? e.toString() : e.getMessage();
            log.warn("tavily_extract failed: {}", message);
            result.put("results", List.of());
            result.put("error", message);
        }
        return result;
    }

    private Object executeFileWrite(Map<String, Object> params) throws Exception {
        String filePath = strParam(params, "file_path", "");
        Object contentObj = params.get("content");
        String content = contentObj == null ? "" : String.valueOf(contentObj);
        String encoding = strParam(params, "encoding", "utf-8");
        if (filePath.isEmpty()) {
            throw new IllegalArgumentException("file_path must not be blank");
        }
        if (contentObj == null) {
            throw new IllegalArgumentException("content must not be blank");
        }
        Path path = Path.of(filePath).toAbsolutePath();
        String ext = extOf(path.getFileName().toString());
        if (!ext.isEmpty() && !WRITABLE_EXTS.contains(ext)) {
            throw new IllegalArgumentException("Unsupported writable file type: " + ext);
        }
        Path parent = path.getParent();
        if (parent != null && !Files.exists(parent)) {
            Files.createDirectories(parent);
        }
        Files.writeString(path, content, charsetOf(encoding));
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("success", true);
        result.put("file_name", path.getFileName().toString());
        result.put("file_path", path.toString());
        result.put("file_size", Files.size(path));
        result.put("content_length", content.length());
        result.put("encoding", encoding);
        result.put("message", "Wrote " + content.length() + " characters to " + path);
        return result;
    }

    private Object executeKnowledgeRetrieve(Map<String, Object> params) {
        int kbId = intParam(params, "knowledge_base_id", -1);
        int chunkIndex = intParam(params, "chunk_index", -1);
        String direction = strParam(params, "direction", "after");
        int limit = intParam(params, "limit", 5);
        List<Map<String, Object>> rows = new ArrayList<>();
        if ("before".equalsIgnoreCase(direction) || "both".equalsIgnoreCase(direction)) {
            rows.addAll(chunks(kbId, chunkIndex, true, limit));
        }
        if ("after".equalsIgnoreCase(direction) || "both".equalsIgnoreCase(direction)) {
            rows.addAll(chunks(kbId, chunkIndex, false, limit));
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("knowledge_base_id", kbId);
        result.put("chunk_index", chunkIndex);
        result.put("direction", direction);
        result.put("count", rows.size());
        result.put("chunks", rows);
        return result;
    }

    private List<Map<String, Object>> chunks(int kbId, int chunkIndex, boolean before, int limit) {
        String operator = before ? "<" : ">";
        String order = before ? "ASC" : "ASC";
        List<Map<String, Object>> rows = new ArrayList<>();
        try {
            List<Map<String, Object>> found = jdbc.queryForList(
                    "SELECT id, chunk_index, content, chunk_metadata FROM knowledge_chunks "
                            + "WHERE knowledge_base_id = ? AND chunk_index " + operator + " ? "
                            + "AND is_deleted = 0 ORDER BY chunk_index " + order + " LIMIT ?",
                    kbId, chunkIndex, limit);
            for (Map<String, Object> row : found) {
                Map<String, Object> out = new LinkedHashMap<>();
                out.put("chunk_id", row.get("id"));
                out.put("chunk_index", row.get("chunk_index"));
                out.put("content", row.get("content"));
                out.put("metadata", metadata(row.get("chunk_metadata")));
                rows.add(out);
            }
        } catch (Exception e) {
            log.warn("knowledge_retrieve query failed: {}", e.getMessage());
        }
        return rows;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> metadata(Object value) {
        if (value instanceof Map<?, ?> map) {
            return (Map<String, Object>) map;
        }
        if (value != null) {
            try {
                Object parsed = om.readValue(value.toString(), Map.class);
                if (parsed instanceof Map<?, ?> map) {
                    return (Map<String, Object>) map;
                }
            } catch (JacksonException e) {
                log.debug("Unable to parse chunk metadata: {}", e.getMessage());
            }
        }
        return Map.of();
    }

    private Object executeWebOpen(Map<String, Object> params) throws Exception {
        String url = strParam(params, "url", "");
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            throw new IllegalArgumentException("URL must begin with http:// or https://");
        }
        String html = fetch(url);
        String title = matchFirst(html, "<title[^>]*>(.*?)</title>");
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("url", url);
        result.put("title", title == null ? "" : stripHtml(title));
        result.put("message", "Opened web page: " + (title == null ? "" : stripHtml(title)));
        return result;
    }

    private Object executeWebScrape(Map<String, Object> params) throws Exception {
        String url = strParam(params, "url", "");
        String selector = strParam(params, "selector", null);
        String html = fetch(url);
        String title = matchFirst(html, "<title[^>]*>(.*?)</title>");
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("url", url);
        result.put("title", title == null ? "" : stripHtml(title));
        result.put("content", stripHtml(html).trim());
        result.put("selector_used", selector);
        return result;
    }

    private Object executeWebClick(Map<String, Object> params) {
        String selector = strParam(params, "selector", "");
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("selector", selector);
        result.put("url", "N/A");
        result.put("title", "N/A");
        result.put("message", "Click simulated without a browser environment: " + selector);
        return result;
    }

    private Object executeWebInput(Map<String, Object> params) {
        String selector = strParam(params, "selector", "");
        String value = strParam(params, "value", "");
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("selector", selector);
        result.put("value", value);
        result.put("message", "Input simulated without a browser environment: " + selector);
        return result;
    }

    private Object executeWebScroll(Map<String, Object> params) {
        String direction = strParam(params, "direction", "down");
        int pixels = intParam(params, "pixels", 500);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("direction", direction);
        result.put("pixels", pixels);
        result.put("message", "Scroll simulated without a browser environment: " + direction + " " + pixels + "px");
        return result;
    }

    private Object executeGetDate(Map<String, Object> params) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("date", LocalDate.now().toString());
        return result;
    }

    private static ToolDefinition def(String name, String displayName, String description, String category,
                                      List<ToolParameter> params) {
        ToolDefinition definition = new ToolDefinition();
        definition.setName(name);
        definition.setDisplayName(displayName);
        definition.setDescription(description);
        definition.setCategory(category);
        definition.setParameters(params);
        definition.setEnabled(true);
        definition.setRequiresAuth(false);
        definition.setTimeout(60);
        definition.setBuiltIn(true);
        return definition;
    }

    private static ToolParameter param(String name, String type, String description) {
        return new ToolParameter(name, type, description, true, null, null);
    }

    private static ToolParameter param(String name, String type, String description, boolean required,
                                       List<String> enumValues) {
        return new ToolParameter(name, type, description, required, null, enumValues);
    }

    private static ToolParameter opt(String name, String type, String description, Object defaultValue) {
        return new ToolParameter(name, type, description, false, defaultValue, null);
    }

    private static int intParam(Map<String, Object> params, String key, int def) {
        Object value = params.get(key);
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value instanceof String text) {
            try {
                return Integer.parseInt(text.trim());
            } catch (NumberFormatException ignored) {
                // fall through
            }
        }
        return def;
    }

    private static String strParam(Map<String, Object> params, String key, String def) {
        Object value = params.get(key);
        return value == null ? def : String.valueOf(value);
    }

    private static String extOf(String fileName) {
        int idx = fileName.lastIndexOf('.');
        return idx < 0 ? "" : fileName.substring(idx).toLowerCase();
    }

    private static Charset charsetOf(String encoding) {
        try {
            return Charset.forName(encoding);
        } catch (Exception e) {
            return StandardCharsets.UTF_8;
        }
    }

    private static String readText(Path path) throws IOException {
        try {
            return Files.readString(path, StandardCharsets.UTF_8);
        } catch (Exception ignored) {
            // try common legacy encodings
        }
        for (String enc : List.of("GBK", "GB2312", "UTF-16")) {
            try {
                return Files.readString(path, Charset.forName(enc));
            } catch (Exception ignored) {
                // try next
            }
        }
        throw new IOException("Unable to decode file; expected UTF-8 or GBK");
    }

    private String fetch(String url) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(30))
                .header("User-Agent",
                        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120 Safari/537.36")
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                .header("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8")
                .GET()
                .build();
        HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IOException("HTTP " + response.statusCode() + " for " + url);
        }
        return response.body();
    }

    private static String matchFirst(String text, String regex) {
        Matcher matcher = Pattern.compile(regex, Pattern.DOTALL).matcher(text);
        return matcher.find() ? matcher.group(1) : null;
    }

    private static final Pattern TAG = Pattern.compile("<[^>]+>");
    private static final Pattern SCRIPT = Pattern.compile("(?is)<script.*?</script>");
    private static final Pattern STYLE = Pattern.compile("(?is)<style.*?</style>");
    private static final Pattern COMMENT = Pattern.compile("(?is)<!--.*?-->");

    private static String stripHtml(String html) {
        String value = COMMENT.matcher(html).replaceAll(" ");
        value = SCRIPT.matcher(value).replaceAll(" ");
        value = STYLE.matcher(value).replaceAll(" ");
        value = TAG.matcher(value).replaceAll(" ");
        value = value.replace("&nbsp;", " ").replace("&amp;", "&").replace("&lt;", "<")
                .replace("&gt;", ">").replace("&quot;", "\"").replace("&#39;", "'");
        return value.replaceAll("\\s+", " ").trim();
    }
}
