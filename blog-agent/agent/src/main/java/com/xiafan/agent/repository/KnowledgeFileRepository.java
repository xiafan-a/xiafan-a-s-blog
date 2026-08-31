package com.xiafan.agent.repository;

import com.xiafan.agent.entity.KnowledgeFile;
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
public class KnowledgeFileRepository extends BaseRepository {

    public KnowledgeFileRepository(DataSource dataSource) {
        super(dataSource);
    }

    public static final RowMapper<KnowledgeFile> ROW_MAPPER = (rs, rowNum) -> {
        KnowledgeFile f = new KnowledgeFile();
        f.setId(rs.getInt("id"));
        f.setKnowledgeBaseId(rs.getInt("knowledge_base_id"));
        f.setFileName(rs.getString("file_name"));
        f.setFileSize(rs.getInt("file_size"));
        f.setFileType(rs.getString("file_type"));
        f.setFileHash(rs.getString("file_hash"));
        f.setIndexingMethod(rs.getString("indexing_method"));
        f.setStatus(rs.getString("status"));
        f.setErrorMessage(rs.getString("error_message"));
        f.setFileMetadata(fromJsonMap(rs.getString("file_metadata")));
        f.setCreatedAt(rs.getTimestamp("created_at").toLocalDateTime());
        f.setUpdatedAt(rs.getTimestamp("updated_at").toLocalDateTime());
        f.setIsDeleted(rs.getInt("is_deleted"));
        return f;
    };

    public KnowledgeFile insert(int knowledgeBaseId, String fileName, int fileSize, String fileType,
                                String fileHash, Map<String, Object> fileMetadata) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbc.update(conn -> {
            PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO knowledge_files (knowledge_base_id, file_name, file_size, file_type, file_hash, "
                            + "indexing_method, status, file_metadata, created_at, updated_at, is_deleted) "
                            + "VALUES (?, ?, ?, ?, ?, 'semantic', 'pending', ?::jsonb, now(), now(), 0)",
                    new String[]{"id"});
            ps.setInt(1, knowledgeBaseId);
            ps.setString(2, fileName);
            ps.setInt(3, fileSize);
            ps.setString(4, fileType);
            ps.setString(5, fileHash);
            ps.setString(6, toJson(fileMetadata));
            return ps;
        }, keyHolder);
        return findById(keyHolder.getKey().intValue()).orElseThrow();
    }

    public Optional<KnowledgeFile> findById(int id) {
        return jdbc.query("SELECT * FROM knowledge_files WHERE id = ? AND is_deleted = 0", ROW_MAPPER, id)
                .stream().findFirst();
    }

    public Optional<KnowledgeFile> findExistingByHash(int kbId, String hash) {
        return jdbc.query(
                "SELECT * FROM knowledge_files WHERE knowledge_base_id = ? AND file_hash = ? AND is_deleted = 0",
                ROW_MAPPER, kbId, hash).stream().findFirst();
    }

    public List<KnowledgeFile> findByKnowledgeBase(int kbId, int skip, int limit) {
        return jdbc.query(
                "SELECT * FROM knowledge_files WHERE knowledge_base_id = ? AND is_deleted = 0 "
                        + "ORDER BY created_at DESC LIMIT ? OFFSET ?",
                ROW_MAPPER, kbId, limit, skip);
    }

    public int updateStatus(int id, String status, String errorMessage) {
        return jdbc.update(
                "UPDATE knowledge_files SET status = ?, error_message = COALESCE(?, error_message), updated_at = now() "
                        + "WHERE id = ?",
                status, errorMessage, id);
    }

    public int updateMetadata(int id, Map<String, Object> metadata) {
        return jdbc.update("UPDATE knowledge_files SET file_metadata = ?::jsonb, updated_at = now() WHERE id = ?",
                toJson(metadata), id);
    }

    public int softDelete(int id) {
        return jdbc.update("UPDATE knowledge_files SET is_deleted = 1, updated_at = now() WHERE id = ? AND is_deleted = 0", id);
    }
}