package com.xiafan.agent.repository;

import com.xiafan.agent.entity.ConversationSession;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import javax.sql.DataSource;
import java.sql.PreparedStatement;
import java.util.List;
import java.util.Optional;

@Repository
public class ConversationSessionRepository extends BaseRepository {

    public ConversationSessionRepository(DataSource dataSource) {
        super(dataSource);
    }

    public static final RowMapper<ConversationSession> ROW_MAPPER = (rs, rowNum) -> {
        ConversationSession s = new ConversationSession();
        s.setId(rs.getInt("id"));
        s.setKnowledgeBaseId(rs.getInt("knowledge_base_id"));
        s.setTitle(rs.getString("title"));
        s.setCreatedAt(rs.getTimestamp("created_at").toLocalDateTime());
        s.setUpdatedAt(rs.getTimestamp("updated_at").toLocalDateTime());
        s.setIsDeleted(rs.getInt("is_deleted"));
        return s;
    };

    public ConversationSession insert(int knowledgeBaseId, String title) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbc.update(conn -> {
            PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO conversation_session (knowledge_base_id, title, created_at, updated_at, is_deleted) "
                            + "VALUES (?, ?, now(), now(), 0)",
                    new String[]{"id"});
            ps.setInt(1, knowledgeBaseId);
            ps.setString(2, title);
            return ps;
        }, keyHolder);
        return findById(keyHolder.getKey().intValue()).orElseThrow();
    }

    public Optional<ConversationSession> findById(int id) {
        return jdbc.query("SELECT * FROM conversation_session WHERE id = ? AND is_deleted = 0", ROW_MAPPER, id)
                .stream().findFirst();
    }

    public List<ConversationSession> findByKnowledgeBase(int kbId, int skip, int limit) {
        return jdbc.query(
                "SELECT * FROM conversation_session WHERE knowledge_base_id = ? AND is_deleted = 0 "
                        + "ORDER BY updated_at DESC LIMIT ? OFFSET ?",
                ROW_MAPPER, kbId, limit, skip);
    }

    public List<ConversationSession> findByKnowledgeBase(int kbId) {
        return jdbc.query(
                "SELECT * FROM conversation_session WHERE knowledge_base_id = ? AND is_deleted = 0 ORDER BY updated_at DESC",
                ROW_MAPPER, kbId);
    }

    public List<ConversationSession> findAllNotDeleted() {
        return jdbc.query(
                "SELECT * FROM conversation_session WHERE is_deleted = 0 ORDER BY updated_at DESC",
                ROW_MAPPER);
    }

    public List<ConversationSession> findByNameContaining(String name) {
        return jdbc.query(
                "SELECT * FROM conversation_session WHERE title LIKE ? AND is_deleted = 0 ORDER BY updated_at DESC",
                ROW_MAPPER, "%" + name + "%");
    }

    public int update(int id, String title) {
        return jdbc.update(
                "UPDATE conversation_session SET title = COALESCE(?, title), updated_at = now() "
                        + "WHERE id = ? AND is_deleted = 0",
                title, id);
    }

    public int softDelete(int id) {
        return jdbc.update(
                "UPDATE conversation_session SET is_deleted = 1, updated_at = now() WHERE id = ? AND is_deleted = 0",
                id);
    }
}