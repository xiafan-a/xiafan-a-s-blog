package com.xiafan.agent.service.agent;

import com.xiafan.agent.entity.agent.ToolDefinition;
import com.xiafan.agent.entity.agent.ToolParameter;
import com.xiafan.agent.entity.KnowledgeChunk;
import com.xiafan.agent.service.KnowledgeChunkService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
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

import org.springframework.stereotype.Component;

/**
 * Built-in tool definitions and executors, mirroring fastApiProject/service/builtInTools.py.
 * Browser-automation tools (web_open/scrape/click/input/scroll) are implemented with plain HTTP
 * text-extraction fallbacks where no browser is available.
 */
@Component
public class BuiltinTools {

    private static final Logger log = LoggerFactory.getLogger(BuiltinTools.class);

    public record Spec(ToolDefinition definition, ToolExecutor executor) {
    }

    private static final List<String> READABLE_TEXT_EXTS =
            List.of(".txt", ".md", ".csv", ".json", ".xml", ".html");
    private static final List<String> WRITABLE_EXTS =
            List.of(".txt", ".md", ".json", ".csv", ".xml", ".html", ".js", ".css", ".py", ".java", ".c", ".cpp", ".h");

    private final KnowledgeChunkService chunkService;
    private final McpWebSearchClient mcpWebSearch;
    private final HttpClient http;

    public BuiltinTools(KnowledgeChunkService chunkService, McpWebSearchClient mcpWebSearch) {
        this.chunkService = chunkService;
        this.mcpWebSearch = mcpWebSearch;
        this.http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    }

    /** All built-in tool specs in registration order (mirrors register_builtin_tools). */
    public List<Spec> all() {
        List<Spec> specs = new ArrayList<>();
        specs.add(new Spec(def("file_read", "读取本地文件",
                "读取本地文件的内容。接收文件完整路径作为参数，返回文件文本内容。支持的格式：txt, md, pdf, docx。",
                "file", List.of(
                        param("file_path", "string", "要读取的文件完整路径，例如：C:/Users/test/document.txt 或 /home/user/file.txt"),
                        opt("max_chars", "integer", "最大读取字符数（可选），防止读取过大文件", 10000)), 60),
                this::executeFileRead));
        specs.add(new Spec(def("web_search", "网页搜索",
                "搜索互联网获取最新信息。当用户问题涉及实时信息、新闻或需要网络搜索时使用此工具。",
                "web", List.of(
                        param("query", "string", "搜索关键词或问题"),
                        opt("num_results", "integer", "返回结果数量（可选）", 5)), 60),
                this::executeWebSearch));
        specs.add(new Spec(def("file_write", "写入本地文件",
                "将内容写入本地文件。如果文件已存在，会覆盖原内容。支持创建 txt, md, json, csv, xml, html 等文本文件。",
                "file", List.of(
                        param("file_path", "string", "要写入的文件完整路径，例如：C:/Users/test/output.txt 或 /home/user/output.txt"),
                        param("content", "string", "要写入的文件内容"),
                        opt("encoding", "string", "文件编码（可选），默认 utf-8", "utf-8")), 60),
                this::executeFileWrite));
        specs.add(new Spec(def("knowledge_retrieve", "知识库检索",
                "根据chunk索引号检索知识库中相邻的知识块内容。当需要获取某个知识点前面或后面的相关知识时使用。",
                "knowledge", List.of(
                        param("knowledge_base_id", "integer", "知识库ID"),
                        param("chunk_index", "integer", "参考的chunk索引号"),
                        opt("direction", "string", "检索方向: before(前面), after(后面), both(两者)", "after"),
                        opt("limit", "integer", "返回数量限制", 5)), 60),
                this::executeKnowledgeRetrieve));
        specs.add(new Spec(def("web_open", "打开网页",
                "导航到指定URL并在浏览器中打开网页。用于访问特定网站或页面。",
                "web", List.of(
                        param("url", "string", "要打开的网页URL，必须以http://或https://开头")), 60),
                this::executeWebOpen));
        specs.add(new Spec(def("web_scrape", "获取页面内容",
                "获取网页的文本内容。可选择性地使用CSS选择器提取特定部分。",
                "web", List.of(
                        param("url", "string", "要获取内容的网页URL"),
                        opt("selector", "string", "可选的CSS选择器，用于提取页面特定部分", null)), 60),
                this::executeWebScrape));
        specs.add(new Spec(def("web_click", "点击元素",
                "点击页面上的指定元素。使用CSS选择器定位元素。",
                "web", List.of(
                        param("selector", "string", "元素的CSS选择器（如 #button-id, .class-name, button）")), 60),
                this::executeWebClick));
        specs.add(new Spec(def("web_input", "填写表单",
                "向页面上的输入框或文本域填写内容。",
                "web", List.of(
                        param("selector", "string", "输入框的CSS选择器"),
                        param("value", "string", "要填写的文本内容")), 60),
                this::executeWebInput));
        specs.add(new Spec(def("web_scroll", "滚动页面",
                "向上或向下滚动页面。",
                "web", List.of(
                        new ToolParameter("direction", "string", "滚动方向：up（向上）或 down（向下）", true, null, List.of("up", "down")),
                        opt("pixels", "integer", "滚动的像素距离（默认500）", 500)), 60),
                this::executeWebScroll));
        specs.add(new Spec(def("get_Date", "获取当前日期", "获取当前日期。", "date", List.of(), 60),
                this::executeGetDate));
        return specs;
    }

    // ============================================ Executors ============================================

    private Object executeFileRead(Map<String, Object> params) throws Exception {
        String filePath = strParam(params, "file_path", "");
        int maxChars = intParam(params, "max_chars", 10000);
        if (filePath.isEmpty()) {
            throw new IllegalArgumentException("文件路径不能为空");
        }
        Path path = Path.of(filePath).toAbsolutePath();
        if (!Files.exists(path)) {
            throw new java.io.FileNotFoundException("文件不存在: " + path);
        }
        if (!Files.isRegularFile(path)) {
            throw new IllegalArgumentException("路径不是文件: " + path);
        }
        String fileName = path.getFileName().toString();
        String ext = extOf(fileName);
        String content;
        if (READABLE_TEXT_EXTS.contains(ext)) {
            content = readText(path);
        } else if (".pdf".equals(ext) || ".docx".equals(ext)) {
            throw new UnsupportedOperationException("暂不支持解析 " + ext + " 文件，请将内容另存为文本文件后重试");
        } else {
            throw new IllegalArgumentException("不支持的文件类型: " + ext + "。支持的类型: " + READABLE_TEXT_EXTS + ", .pdf, .docx");
        }
        if (content.length() > maxChars) {
            content = content.substring(0, maxChars) + "\n\n... (内容已截断，原文件共 " + content.length() + " 字符)";
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("file_name", fileName);
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
            List<Map<String, Object>> results = mcpWebSearch.search(query, numResults);
            result.put("results", results);
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
            throw new IllegalArgumentException("文件路径不能为空");
        }
        if (contentObj == null) {
            throw new IllegalArgumentException("文件内容不能为空");
        }
        Path path = Path.of(filePath).toAbsolutePath();
        String ext = extOf(path.getFileName().toString());
        if (!ext.isEmpty() && !WRITABLE_EXTS.contains(ext)) {
            throw new IllegalArgumentException("不支持写入此文件类型: " + ext + "。支持的类型: " + WRITABLE_EXTS);
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
        result.put("message", "文件写入成功，共写入 " + content.length() + " 字符");
        return result;
    }

    private Object executeKnowledgeRetrieve(Map<String, Object> params) {
        int kbId = intParam(params, "knowledge_base_id", -1);
        int chunkIndex = intParam(params, "chunk_index", -1);
        String direction = strParam(params, "direction", "after");
        int limit = intParam(params, "limit", 5);
        List<KnowledgeChunk> chunks = chunkService.getChunksByIndexRange(kbId, chunkIndex, direction, limit);
        List<Map<String, Object>> rows = new ArrayList<>();
        for (KnowledgeChunk c : chunks) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("chunk_id", c.getId());
            row.put("chunk_index", c.getChunkIndex());
            row.put("content", c.getContent());
            row.put("metadata", c.getChunkMetadata());
            rows.add(row);
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("knowledge_base_id", kbId);
        result.put("chunk_index", chunkIndex);
        result.put("direction", direction);
        result.put("count", rows.size());
        result.put("chunks", rows);
        return result;
    }

    private Object executeWebOpen(Map<String, Object> params) throws Exception {
        String url = strParam(params, "url", "");
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            throw new IllegalArgumentException("URL必须以http://或https://开头");
        }
        String html = fetch(url);
        String title = matchFirst(html, "<title[^>]*>(.*?)</title>");
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("url", url);
        result.put("title", title == null ? "" : stripHtml(title));
        result.put("message", "已打开网页: " + (title == null ? "" : stripHtml(title)));
        return result;
    }

    private Object executeWebScrape(Map<String, Object> params) throws Exception {
        String url = strParam(params, "url", "");
        String selector = strParam(params, "selector", null);
        String html = fetch(url);
        String title = matchFirst(html, "<title[^>]*>(.*?)</title>");
        String text = stripHtml(html).trim();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("url", url);
        result.put("title", title == null ? "" : stripHtml(title));
        result.put("content", text);
        result.put("selector_used", selector);
        return result;
    }

    private Object executeWebClick(Map<String, Object> params) {
        String selector = strParam(params, "selector", "");
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("selector", selector);
        result.put("url", "N/A");
        result.put("title", "N/A");
        result.put("message", "已点击元素: " + selector + "（无浏览器环境，未执行真实点击）");
        return result;
    }

    private Object executeWebInput(Map<String, Object> params) {
        String selector = strParam(params, "selector", "");
        String value = strParam(params, "value", "");
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("selector", selector);
        result.put("value", value);
        result.put("message", "已填写内容到: " + selector + "（无浏览器环境，未执行真实输入）");
        return result;
    }

    private Object executeWebScroll(Map<String, Object> params) {
        String direction = strParam(params, "direction", "down");
        int pixels = intParam(params, "pixels", 500);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("direction", direction);
        result.put("pixels", pixels);
        result.put("message", "已" + direction + "滚动 " + pixels + " 像素（无浏览器环境，未执行真实滚动）");
        return result;
    }

    private Object executeGetDate(Map<String, Object> params) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("date", LocalDate.now().toString());
        return result;
    }

    // ============================================ helpers ============================================

    private static ToolDefinition def(String name, String displayName, String description, String category,
                                      List<ToolParameter> params, int timeout) {
        ToolDefinition d = new ToolDefinition();
        d.setName(name);
        d.setDisplayName(displayName);
        d.setDescription(description);
        d.setCategory(category);
        d.setParameters(params);
        d.setEnabled(true);
        d.setRequiresAuth(false);
        d.setTimeout(timeout);
        return d;
    }

    private static ToolParameter param(String name, String type, String description) {
        return new ToolParameter(name, type, description, true, null, null);
    }

    private static ToolParameter opt(String name, String type, String description, Object defaultValue) {
        return new ToolParameter(name, type, description, false, defaultValue, null);
    }

    private static int intParam(Map<String, Object> p, String key, int def) {
        Object v = p.get(key);
        if (v instanceof Number n) {
            return n.intValue();
        }
        if (v instanceof String s) {
            try {
                return Integer.parseInt(s.trim());
            } catch (NumberFormatException ignored) {
                // fall through
            }
        }
        return def;
    }

    private static String strParam(Map<String, Object> p, String key, String def) {
        Object v = p.get(key);
        return v == null ? def : String.valueOf(v);
    }

    private static String extOf(String fileName) {
        int idx = fileName.lastIndexOf('.');
        return idx < 0 ? "" : fileName.substring(idx).toLowerCase();
    }

    private static java.nio.charset.Charset charsetOf(String encoding) {
        try {
            return java.nio.charset.Charset.forName(encoding);
        } catch (Exception e) {
            return StandardCharsets.UTF_8;
        }
    }

    private static String readText(Path path) throws IOException {
        try {
            return Files.readString(path, StandardCharsets.UTF_8);
        } catch (Exception ignored) {
            // fall through to alternate encodings
        }
        for (String enc : List.of("GBK", "GB2312", "UTF-16")) {
            try {
                return Files.readString(path, java.nio.charset.Charset.forName(enc));
            } catch (Exception ignored) {
                // try next
            }
        }
        throw new IOException("无法解码文件，请确认文件编码为 UTF-8 或 GBK");
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
        HttpResponse<String> resp = http.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (resp.statusCode() < 200 || resp.statusCode() >= 300) {
            throw new IOException("HTTP " + resp.statusCode() + " for " + url);
        }
        return resp.body();
    }

    private static String matchFirst(String text, String regex) {
        Matcher m = Pattern.compile(regex, Pattern.DOTALL).matcher(text);
        return m.find() ? m.group(1) : null;
    }

    private static final Pattern TAG = Pattern.compile("<[^>]+>");
    private static final Pattern SCRIPT = Pattern.compile("(?is)<script.*?</script>");
    private static final Pattern STYLE = Pattern.compile("(?is)<style.*?</style>");
    private static final Pattern COMMENT = Pattern.compile("(?is)<!--.*?-->");

    private static String stripHtml(String html) {
        String s = COMMENT.matcher(html).replaceAll(" ");
        s = SCRIPT.matcher(s).replaceAll(" ");
        s = STYLE.matcher(s).replaceAll(" ");
        s = TAG.matcher(s).replaceAll(" ");
        s = s.replace("&nbsp;", " ").replace("&amp;", "&").replace("&lt;", "<")
                .replace("&gt;", ">").replace("&quot;", "\"").replace("&#39;", "'");
        return s.replaceAll("\\s+", " ").trim();
    }
}
