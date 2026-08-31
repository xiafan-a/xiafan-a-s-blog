package com.xiafan.agent.repository;

import com.xiafan.agent.entity.KnowledgeBase;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import javax.sql.DataSource;
import java.sql.PreparedStatement;
import java.util.List;
import java.util.Optional;

@Repository
public class KnowledgeBaseRepository extends BaseRepository {

    public KnowledgeBaseRepository(DataSource dataSource) {
        super(dataSource);
    }

    public static final RowMapper<KnowledgeBase> ROW_MAPPER = (rs, rowNum) -> {
        KnowledgeBase kb = new KnowledgeBase();
        kb.setId(rs.getInt("id"));
        kb.setName(rs.getString("name"));
        kb.setDescription(rs.getString("description"));
        kb.setSystemPrompt(rs.getString("system_prompt"));
        kb.setCreatedAt(rs.getTimestamp("created_at").toLocalDateTime());
        kb.setUpdatedAt(rs.getTimestamp("updated_at").toLocalDateTime());
        kb.setIsDeleted(rs.getInt("is_deleted"));
        return kb;
    };

    public KnowledgeBase insert(String name, String description, String systemPrompt) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbc.update(conn -> {
            PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO knowledge_bases (name, description, system_prompt, created_at, updated_at, is_deleted) "
                            + "VALUES (?, ?, ?, now(), now(), 0)",
                    new String[]{"id"});
            ps.setString(1, name);
            ps.setString(2, description);
            ps.setString(3, systemPrompt);
            return ps;
        }, keyHolder);
        Number id = keyHolder.getKey();
        return findById(id.intValue()).orElseThrow();
    }

    public Optional<KnowledgeBase> findById(int id) {
        return jdbc.query("SELECT * FROM knowledge_bases WHERE id = ? AND is_deleted = 0", ROW_MAPPER, id)
                .stream().findFirst();
    }

    public List<KnowledgeBase> findAll(int skip, int limit) {
        return jdbc.query("SELECT * FROM knowledge_bases WHERE is_deleted = 0 ORDER BY id LIMIT ? OFFSET ?",
                ROW_MAPPER, limit, skip);
    }

    public int update(int id, String name, String description, String systemPrompt) {
        return jdbc.update(
                "UPDATE knowledge_bases SET name = COALESCE(?, name), description = COALESCE(?, description), "
                        + "system_prompt = COALESCE(?, system_prompt), updated_at = now() WHERE id = ? AND is_deleted = 0",
                name, description, systemPrompt, id);
    }

    public int softDelete(int id) {
        return jdbc.update("UPDATE knowledge_bases SET is_deleted = 1, updated_at = now() WHERE id = ? AND is_deleted = 0", id);
    }
}