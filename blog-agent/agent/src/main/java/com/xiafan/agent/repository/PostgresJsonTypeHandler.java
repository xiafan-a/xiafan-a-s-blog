package com.xiafan.agent.repository;

import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.ibatis.type.JdbcType;
import org.postgresql.util.PGobject;

import java.lang.reflect.Field;
import java.sql.PreparedStatement;
import java.sql.SQLException;

/** Writes JSON fields as PostgreSQL jsonb values instead of plain varchar strings. */
public class PostgresJsonTypeHandler extends JacksonTypeHandler {

    private static final ObjectMapper JSON = new ObjectMapper().findAndRegisterModules();

    public PostgresJsonTypeHandler(Class<?> type) {
        super(type);
    }

    public PostgresJsonTypeHandler(Class<?> type, Field field) {
        super(type, field);
    }

    @Override
    public void setNonNullParameter(PreparedStatement ps, int i, Object parameter, JdbcType jdbcType)
            throws SQLException {
        try {
            PGobject json = new PGobject();
            json.setType("jsonb");
            json.setValue(JSON.writeValueAsString(parameter));
            ps.setObject(i, json);
        } catch (JsonProcessingException e) {
            throw new SQLException("Failed to serialize JSONB value", e);
        }
    }
}
