package com.xiafan.ai.skill;

import com.xiafan.ai.config.SkillProperties;
import com.xiafan.ai.mcp.McpToolCatalog;
import com.xiafan.ai.mcp.McpToolDefinition;
import com.xiafan.ai.persistence.RecordRepository;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

@Service
public class SkillRegistry {

    private static final Logger log = LoggerFactory.getLogger(SkillRegistry.class);

    private final SkillProperties props;
    private final RecordRepository records;
    private final PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();

    private final Map<String, SkillDefinition> skills = new LinkedHashMap<>();

    public SkillRegistry(SkillProperties props, RecordRepository records) {
        this.props = props;
        this.records = records;
    }

    @PostConstruct
    public synchronized void initialize() {
        try {
            List<SkillDefinition> loaded = loadDefinitions();
            for (SkillDefinition def : loaded) {
                boolean enabled = def.enabled();
                try {
                    enabled = records.findSkillEnabled(def.name()).orElse(def.enabled());
                } catch (Exception e) {
                    log.warn("Unable to read skill enabled state for '{}': {}", def.name(), e.getMessage());
                }
                SkillDefinition merged = def.withEnabled(enabled);
                skills.put(merged.name(), merged);
                try {
                    records.upsertSkill(merged);
                } catch (Exception e) {
                    log.warn("Unable to upsert skill '{}': {}", merged.name(), e.getMessage());
                }
            }
            persistMcpMetadata();
            log.info("Loaded {} skill(s)", skills.size());
        } catch (Exception e) {
            throw new IllegalStateException("Failed to initialize skill registry", e);
        }
    }

    public List<SkillDefinition> list(boolean enabledOnly) {
        List<SkillDefinition> result = new ArrayList<>();
        for (SkillDefinition def : skills.values()) {
            if (enabledOnly && !def.enabled()) {
                continue;
            }
            result.add(def);
        }
        return result;
    }

    public Map<String, Object> listSummary() {
        Map<String, Object> body = new LinkedHashMap<>();
        List<Map<String, Object>> items = new ArrayList<>();
        for (SkillDefinition def : list(false)) {
            items.add(def.summary());
        }
        body.put("skills", items);
        body.put("count", items.size());
        return body;
    }

    public SkillDefinition getRequired(String name) {
        SkillDefinition def = skills.get(name);
        if (def == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Skill '" + name + "' not found");
        }
        return def;
    }

    public Map<String, Object> skillSummary(String name) {
        return getRequired(name).toMap();
    }

    public SkillApplyResponse apply(String name, String userMessage, List<Map<String, String>> history) {
        if (userMessage == null || userMessage.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "userMessage must not be blank");
        }
        SkillDefinition def = getRequired(name);
        List<Map<String, String>> messages = new ArrayList<>();
        if (history != null) {
            for (Map<String, String> message : history) {
                messages.add(normalizeMessage(message));
            }
        }
        messages.add(Map.of("role", "user", "content", userMessage));
        return new SkillApplyResponse(def, buildSystemPrompt(def), messages);
    }

    private List<SkillDefinition> loadDefinitions() throws IOException {
        String root = props.getRoot();
        if (!root.startsWith("classpath:") && !root.startsWith("classpath*:")
                && !root.startsWith("file:") && !root.startsWith("jar:")) {
            root = "file:" + root;
        }
        String pattern = root.endsWith("/") ? root + "**/SKILL.md" : root + "/**/SKILL.md";
        Resource[] resources = resolver.getResources(pattern);
        List<SkillDefinition> definitions = new ArrayList<>();
        for (Resource resource : resources) {
            SkillDefinition def = parse(resource);
            if (def != null) {
                definitions.add(def);
            }
        }
        return definitions;
    }

    private SkillDefinition parse(Resource resource) throws IOException {
        String raw;
        try (InputStream in = resource.getInputStream()) {
            raw = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
        String defaultName = parentDirectoryName(resource);
        String name = defaultName;
        String description = "";
        String body = raw;

        if (raw.startsWith("---")) {
            int end = raw.indexOf("---", 3);
            if (end > 0) {
                String frontMatter = raw.substring(3, end);
                body = raw.substring(end + 3).stripLeading();
                try {
                    Yaml yaml = new Yaml();
                    Object parsed = yaml.load(frontMatter);
                    if (parsed instanceof Map<?, ?> metadata) {
                        Object rawName = metadata.get("name");
                        Object rawDescription = metadata.get("description");
                        name = rawName == null ? defaultName : String.valueOf(rawName);
                        description = rawDescription == null ? "" : String.valueOf(rawDescription);
                    }
                } catch (Exception e) {
                    log.warn("Invalid front matter in {}: {}", resource.getFilename(), e.getMessage());
                }
            }
        }

        if (name == null || name.isBlank() || body == null || body.isBlank()) {
            log.warn("Skipping invalid skill resource: {}", resource);
            return null;
        }
        return new SkillDefinition(name.trim(), description.trim(), body.trim(), resource.getDescription(), true);
    }

    private void persistMcpMetadata() {
        try {
            records.upsertMcpServer(McpToolCatalog.SERVER_NAME, "http", "/mcp", McpToolCatalog.SERVER_VERSION);
        } catch (Exception e) {
            log.warn("Unable to persist MCP server metadata: {}", e.getMessage());
        }
        for (McpToolDefinition tool : McpToolCatalog.definitions()) {
            try {
                records.upsertMcpTool(tool);
            } catch (Exception e) {
                log.warn("Unable to persist MCP tool metadata '{}': {}", tool.name(), e.getMessage());
            }
        }
    }

    private static String buildSystemPrompt(SkillDefinition skill) {
        return """
                You are an AI assistant for the blog platform. Follow the skill below while handling this conversation.

                Skill: %s
                Description: %s

                Instructions:
                %s

                Follow every instruction from the skill. If the skill is not applicable, answer normally.
                """.formatted(skill.name(), skill.description() == null ? "" : skill.description(),
                skill.instructions());
    }

    private static Map<String, String> normalizeMessage(Map<String, String> message) {
        if (message == null) {
            return Map.of("role", "user", "content", "");
        }
        String role = message.getOrDefault("role", "user");
        String content = message.getOrDefault("content", "");
        return Map.of("role", role == null ? "user" : role, "content", content == null ? "" : content);
    }

    private static String parentDirectoryName(Resource resource) {
        try {
            String path = resource.getURI().toString().replace('\\', '/');
            int lastSlash = path.lastIndexOf('/');
            int start = lastSlash > 0 ? path.lastIndexOf('/', lastSlash - 1) : -1;
            return start >= 0 ? path.substring(start + 1, lastSlash) : "skill";
        } catch (Exception e) {
            return "skill";
        }
    }
}
