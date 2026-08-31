package com.xiafan.agent.repository;

import com.fasterxml.jackson.core.type.TypeReference;
import com.xiafan.agent.entity.ConversationMessage;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import javax.sql.DataSource;
import java.sql.PreparedStatement;
import java.sql.Types;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Repository
public class ConversationMessageRepository extends BaseRepository {

    public ConversationMessageRepository(DataSource dataSource) {
        super(dataSource);
    }

    public static final RowMapper<ConversationMessage> ROW_MAPPER = (rs, rowNum) -> {
        ConversationMessage m = new ConversationMessage();
        m.setId(rs.getInt("id"));
        m.setKnowledgeBaseId(rs.getInt("knowledge_base_id"));
        m.setRole(rs.getString("role"));
        m.setContent(rs.getString("content"));
        m.setSessionId(rs.getObject("session_id") != null ? rs.getInt("session_id") : null);
        m.setParentMessageId(rs.getObject("parent_message_id") != null ? rs.getInt("parent_message_id") : null);
        m.setContextWindow(rs.getObject("context_window") != null ? rs.getInt("context_window") : 10);
        m.setContextSummary(rs.getString("context_summary"));
        m.setSources(fromJsonList(rs.getString("sources"), new TypeReference<>() {
        }));
        m.setTokenUsage(fromJsonMap(rs.getString("token_usage")));
        m.setFeedback(rs.getObject("feedback") != null ? rs.getInt("feedback") : null);
        m.setMessageMetadata(fromJsonMap(rs.getString("message_metadata")));
        m.setCreatedAt(rs.getTimestamp("created_at").toLocalDateTime());
        m.setUpdatedAt(rs.getTimestamp("updated_at").toLocalDateTime());
        m.setIsDeleted(rs.getInt("is_deleted"));
        return m;
    };

    public ConversationMessage insert(int knowledgeBaseId, String role, String content, Integer sessionId,
                                      Integer parentMessageId, Integer contextWindow, String contextSummary,
                                      List<Object> sources, Map<String, Object> tokenUsage, Integer feedback,
                                      Map<String, Object> messageMetadata) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbc.update(conn -> {
            PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO conversation_messages (knowledge_base_id, role, content, session_id, "
                            + "parent_message_id, context_window, context_summary, sources, token_usage, feedback, "
                            + "message_metadata, created_at, updated_at, is_deleted) "
                            + "VALUES (?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?::jsonb, ?, ?::jsonb, now(), now(), 0)",
                    new String[]{"id"});
            ps.setInt(1, knowledgeBaseId);
            ps.setString(2, role);
            ps.setString(3, content);
            setIntOrNull(ps, 4, sessionId);
            setIntOrNull(ps, 5, parentMessageId);
            ps.setInt(6, contextWindow != null ? contextWindow : 10);
            ps.setString(7, contextSummary);
            ps.setString(8, toJson(sources != null ? sources : List.of()));
            ps.setString(9, toJson(tokenUsage != null ? tokenUsage : Map.of()));
            setIntOrNull(ps, 10, feedback);
            ps.setString(11, toJson(messageMetadata != null ? messageMetadata : Map.of()));
            return ps;
        }, keyHolder);
        return findById(keyHolder.getKey().intValue()).orElseThrow();
    }

    public Optional<ConversationMessage> findById(int id) {
        return jdbc.query("SELECT * FROM conversation_messages WHERE id = ? AND is_deleted = 0", ROW_MAPPER, id)
                .stream().findFirst();
    }

    public List<ConversationMessage> findByKnowledgeBase(int kbId, int skip, int limit) {
        return jdbc.query(
                "SELECT * FROM conversation_messages WHERE knowledge_base_id = ? AND is_deleted = 0 "
                        + "ORDER BY created_at DESC LIMIT ? OFFSET ?",
                ROW_MAPPER, kbId, limit, skip);
    }

    public List<ConversationMessage> findBySession(int sessionId, int skip, int limit) {
        return jdbc.query(
                "SELECT * FROM conversation_messages WHERE session_id = ? AND is_deleted = 0 "
                        + "ORDER BY created_at ASC LIMIT ? OFFSET ?",
                ROW_MAPPER, sessionId, limit, skip);
    }

    public int updateContent(int id, String content) {
        return jdbc.update(
                "UPDATE conversation_messages SET content = ?, updated_at = now() WHERE id = ? AND is_deleted = 0",
                content, id);
    }

    public int updateFeedback(int id, Integer feedback) {
        return jdbc.update(
                "UPDATE conversation_messages SET feedback = ?, updated_at = now() WHERE id = ? AND is_deleted = 0",
                feedback, id);
    }

    public int softDelete(int id) {
        return jdbc.update(
                "UPDATE conversation_messages SET is_deleted = 1, updated_at = now() WHERE id = ? AND is_deleted = 0",
                id);
    }

    private static void setIntOrNull(PreparedStatement ps, int index, Integer value) throws java.sql.SQLException {
        if (value == null) {
            ps.setNull(index, Types.INTEGER);
        } else {
            ps.setInt(index, value);
        }
    }
}