package com.xiafan.agent.service.agent;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiafan.agent.config.AppProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * HTTP client for the centralized mcp-skill-service. Blog-agent no longer owns built-in
 * tool implementations or their audit records; it reads definitions and invokes them remotely.
 */
@Component
public class CapabilityServiceClient {

    private static final Logger log = LoggerFactory.getLogger(CapabilityServiceClient.class);

    private final AppProperties props;
    private final ObjectMapper om;
    private final HttpClient http;

    public CapabilityServiceClient(AppProperties props, ObjectMapper om) {
        this.props = props;
        this.om = om;
        this.http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
    }

    public boolean isEnabled() {
        AppProperties.CapabilityServiceConfig config = props.getCapabilityService();
        return config.isEnabled() && config.getBaseUrl() != null && !config.getBaseUrl().isBlank();
    }

    public List<Map<String, Object>> listTools() {
        Map<String, Object> body = get("/api/v1/tools");
        return list(body.get("tools"));
    }

    public Map<String, Object> getTool(String name) {
        return get("/api/v1/tools/" + path(name));
    }

    public Map<String, Object> registerTool(Object request) {
        return send("POST", "/api/v1/tools", request);
    }

    public Map<String, Object> unregisterTool(String name) {
        return send("DELETE", "/api/v1/tools/" + path(name), null);
    }

    public Map<String, Object> executeTool(String name, Map<String, Object> parameters) {
        return send("POST", "/api/v1/tools/" + path(name) + "/execute", parameters == null ? Map.of() : parameters);
    }

    public Map<String, Object> listSkills() {
        return get("/api/v1/skills");
    }

    public Map<String, Object> getSkill(String name) {
        return get("/api/v1/skills/" + path(name));
    }

    public Map<String, Object> applySkill(String name, Map<String, Object> request) {
        return send("POST", "/api/v1/skills/" + path(name) + "/apply", request);
    }

    private Map<String, Object> get(String path) {
        return send("GET", path, null);
    }

    private Map<String, Object> send(String method, String path, Object body) {
        String baseUrl = props.getCapabilityService().getBaseUrl();
        if (!isEnabled()) {
            throw new CapabilityServiceException(503, "Capability service is disabled");
        }
        if (baseUrl.endsWith("/")) {
            baseUrl = baseUrl.substring(0, baseUrl.length() - 1);
        }
        Duration timeout = Duration.ofSeconds(Math.max(1, props.getCapabilityService().getTimeoutSeconds()));
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + path))
                .timeout(timeout)
                .header("Accept", "application/json");
        if ("GET".equalsIgnoreCase(method) || "DELETE".equalsIgnoreCase(method)) {
            builder.method(method.toUpperCase(), HttpRequest.BodyPublishers.noBody());
        } else {
            String payload = toJson(body);
            builder.header("Content-Type", "application/json")
                    .method(method.toUpperCase(), HttpRequest.BodyPublishers.ofString(payload, StandardCharsets.UTF_8));
        }
        try {
            HttpResponse<String> response = http.send(builder.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            String raw = response.body();
            if (response.statusCode() >= 400) {
                throw new CapabilityServiceException(response.statusCode(), message(raw, response.statusCode()));
            }
            if (raw == null || raw.isBlank()) {
                return Map.of();
            }
            return om.readValue(raw, new TypeReference<Map<String, Object>>() {
            });
        } catch (CapabilityServiceException e) {
            throw e;
        } catch (Exception e) {
            log.warn("capability service call failed {} {}", method, path, e);
            throw new CapabilityServiceException(502, "Capability service unavailable: "
                    + (e.getMessage() == null ? e.toString() : e.getMessage()));
        }
    }

    private String message(String raw, int status) {
        if (raw == null || raw.isBlank()) {
            return "Capability service returned HTTP " + status;
        }
        try {
            JsonNode node = om.readTree(raw);
            JsonNode detail = node.path("message");
            JsonNode error = node.path("detail");
            if (!detail.isMissingNode() && !detail.asText().isBlank()) {
                return detail.asText();
            }
            if (!error.isMissingNode()) {
                return error.isTextual() ? error.asText() : error.toString();
            }
            return raw;
        } catch (Exception ignored) {
            return raw;
        }
    }

    private String toJson(Object value) {
        try {
            return om.writeValueAsString(value == null ? Map.of() : value);
        } catch (Exception e) {
            throw new IllegalArgumentException("Unable to serialize capability request", e);
        }
    }

    private static String path(String name) {
        String encoded = name == null ? "" : name.replace("/", "%2F").replace(" ", "%20");
        return encoded;
    }

    private static List<Map<String, Object>> list(Object value) {
        List<Map<String, Object>> result = new ArrayList<>();
        if (value instanceof List<?> raw) {
            for (Object item : raw) {
                if (item instanceof Map<?, ?> map) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> typed = (Map<String, Object>) map;
                    result.add(typed);
                }
            }
        }
        return result;
    }
}
