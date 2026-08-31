package com.xiafan.agent.repository;

import com.fasterxml.jackson.core.type.TypeReference;
import com.xiafan.agent.entity.KnowledgeChunk;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import javax.sql.DataSource;
import java.sql.PreparedStatement;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Repository
public class KnowledgeChunkRepository extends BaseRepository {

    public KnowledgeChunkRepository(DataSource dataSource) {
        super(dataSource);
    }

    public static final RowMapper<KnowledgeChunk> ROW_MAPPER = (rs, rowNum) -> {
        KnowledgeChunk c = new KnowledgeChunk();
        c.setId(rs.getInt("id"));
        c.setKnowledgeBaseId(rs.getInt("knowledge_base_id"));
        c.setKnowledgeFileId(rs.getObject("knowledge_file_id") != null ? rs.getInt("knowledge_file_id") : null);
        c.setChunkIndex(rs.getInt("chunk_index"));
        c.setContent(rs.getString("content"));
        c.setEmbedding(fromJsonList(rs.getString("embedding"), new TypeReference<>() {
        }));
        c.setChunkMetadata(fromJsonMap(rs.getString("chunk_metadata")));
        c.setIndexingMethod(rs.getString("indexing_method"));
        c.setCreatedAt(rs.getTimestamp("created_at").toLocalDateTime());
        c.setUpdatedAt(rs.getTimestamp("updated_at").toLocalDateTime());
        c.setIsDeleted(rs.getInt("is_deleted"));
        return c;
    };

    public KnowledgeChunk insert(int knowledgeBaseId, Integer knowledgeFileId, int chunkIndex, String content,
                                 List<Double> embedding, Map<String, Object> chunkMetadata, String indexingMethod) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbc.update(conn -> {
            PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO knowledge_chunks (knowledge_base_id, knowledge_file_id, chunk_index, content, "
                            + "embedding, chunk_metadata, indexing_method, created_at, updated_at, is_deleted) "
                            + "VALUES (?, ?, ?, ?, ?::jsonb, ?::jsonb, ?, now(), now(), 0)",
                    new String[]{"id"});
            ps.setInt(1, knowledgeBaseId);
            setIntOrNull(ps, 2, knowledgeFileId);
            ps.setInt(3, chunkIndex);
            ps.setString(4, content);
            ps.setString(5, embedding == null ? null : toJson(embedding));
            ps.setString(6, toJson(chunkMetadata));
            ps.setString(7, indexingMethod);
            return ps;
        }, keyHolder);
        return findById(keyHolder.getKey().intValue()).orElseThrow();
    }

    public Optional<KnowledgeChunk> findById(int id) {
        return jdbc.query("SELECT * FROM knowledge_chunks WHERE id = ? AND is_deleted = 0", ROW_MAPPER, id)
                .stream().findFirst();
    }

    public List<KnowledgeChunk> findByKnowledgeBase(int kbId, int skip, int limit) {
        return jdbc.query(
                "SELECT * FROM knowledge_chunks WHERE knowledge_base_id = ? AND is_deleted = 0 "
                        + "ORDER BY chunk_index ASC LIMIT ? OFFSET ?",
                ROW_MAPPER, kbId, limit, skip);
    }

    public List<KnowledgeChunk> findByFile(int fileId, int skip, int limit) {
        return jdbc.query(
                "SELECT * FROM knowledge_chunks WHERE knowledge_file_id = ? AND is_deleted = 0 "
                        + "ORDER BY chunk_index ASC LIMIT ? OFFSET ?",
                ROW_MAPPER, fileId, limit, skip);
    }

    public List<KnowledgeChunk> findAllWithEmbedding(int kbId) {
        return jdbc.query(
                "SELECT * FROM knowledge_chunks WHERE knowledge_base_id = ? AND is_deleted = 0 AND embedding IS NOT NULL",
                ROW_MAPPER, kbId);
    }

    public List<KnowledgeChunk> findBefore(int kbId, int chunkIndex, int limit) {
        return jdbc.query(
                "SELECT * FROM knowledge_chunks WHERE knowledge_base_id = ? AND is_deleted = 0 "
                        + "AND chunk_index < ? ORDER BY chunk_index DESC LIMIT ?",
                ROW_MAPPER, kbId, chunkIndex, limit);
    }

    public List<KnowledgeChunk> findAfter(int kbId, int chunkIndex, int limit) {
        return jdbc.query(
                "SELECT * FROM knowledge_chunks WHERE knowledge_base_id = ? AND is_deleted = 0 "
                        + "AND chunk_index > ? ORDER BY chunk_index ASC LIMIT ?",
                ROW_MAPPER, kbId, chunkIndex, limit);
    }

    public int update(int id, String content, List<Double> embedding, Map<String, Object> metadata) {
        return jdbc.update(
                "UPDATE knowledge_chunks SET content = COALESCE(?, content), embedding = COALESCE(?::jsonb, embedding), "
                        + "chunk_metadata = COALESCE(?::jsonb, chunk_metadata), updated_at = now() "
                        + "WHERE id = ? AND is_deleted = 0",
                content, embedding == null ? null : toJson(embedding),
                metadata == null ? null : toJson(metadata), id);
    }

    public int softDelete(int id) {
        return jdbc.update("UPDATE knowledge_chunks SET is_deleted = 1, updated_at = now() WHERE id = ? AND is_deleted = 0", id);
    }

    public int deleteByFile(int fileId) {
        return jdbc.update(
                "UPDATE knowledge_chunks SET is_deleted = 1, updated_at = now() "
                        + "WHERE knowledge_file_id = ? AND is_deleted = 0",
                fileId);
    }

    private static void setIntOrNull(PreparedStatement ps, int index, Integer value) throws java.sql.SQLException {
        if (value == null) {
            ps.setNull(index, java.sql.Types.INTEGER);
        } else {
            ps.setInt(index, value);
        }
    }
}