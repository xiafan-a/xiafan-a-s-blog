package com.xiafan.agent.repository;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import javax.sql.DataSource;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public abstract class BaseRepository {

    private static final ObjectMapper JSON = new ObjectMapper().findAndRegisterModules();

    protected final JdbcTemplate jdbc;
    protected final NamedParameterJdbcTemplate named;

    public BaseRepository(DataSource dataSource) {
        this.jdbc = new JdbcTemplate(dataSource);
        this.named = new NamedParameterJdbcTemplate(dataSource);
    }

    protected static String toJson(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return JSON.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new RepositoryException("JSON serialization failed", e);
        }
    }

    protected static <T> T fromJson(String json, Class<T> type) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return JSON.readValue(json, type);
        } catch (JsonProcessingException e) {
            throw new RepositoryException("JSON deserialization failed: " + json, e);
        }
    }

    protected static <T> List<T> fromJsonList(String json, TypeReference<List<T>> ref) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return JSON.readValue(json, ref);
        } catch (JsonProcessingException e) {
            throw new RepositoryException("JSON list deserialization failed", e);
        }
    }

    protected static Map<String, Object> fromJsonMap(String json) {
        if (json == null || json.isBlank()) {
            return new HashMap<>();
        }
        try {
            return JSON.readValue(json, new TypeReference<Map<String, Object>>() {
            });
        } catch (JsonProcessingException e) {
            throw new RepositoryException("JSON map deserialization failed: " + json, e);
        }
    }

    public static class RepositoryException extends RuntimeException {
        public RepositoryException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}