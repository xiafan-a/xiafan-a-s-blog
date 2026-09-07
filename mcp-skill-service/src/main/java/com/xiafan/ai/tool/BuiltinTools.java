package com.xiafan.ai.tool;

import com.xiafan.ai.search.McpWebSearchClient;
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

    private final McpWebSearchClient webSearch;
    private final JdbcTemplate jdbc;
    private final ObjectMapper om;
    private final HttpClient http;

    public BuiltinTools(McpWebSearchClient webSearch, JdbcTemplate jdbc, ObjectMapper om) {
        this.webSearch = webSearch;
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
                "Search the web when the latest or external information is needed.",
                "web", List.of(
                        param("query", "string", "Search query or question"),
                        opt("num_results", "integer", "Number of results to return", 5))),
                this::executeWebSearch));
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

    private Object executeWebSearch(Map<String, Object> params) {
        String query = strParam(params, "query", "");
        int numResults = intParam(params, "num_results", 5);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("query", query);
        try {
            result.put("results", webSearch.search(query, numResults));
        } catch (Exception e) {
            String message = e.getMessage() == null ? e.toString() : e.getMessage();
            log.warn("web_search failed: {}", message);
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
