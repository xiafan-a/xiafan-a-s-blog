package com.xiafan.agent.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.xiafan.agent.entity.KnowledgeFile;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public interface KnowledgeFileRepository extends BaseMapper<KnowledgeFile> {

    default KnowledgeFile insert(int knowledgeBaseId, String fileName, int fileSize, String fileType,
                                 String fileHash, Map<String, Object> fileMetadata) {
        KnowledgeFile file = new KnowledgeFile();
        file.setKnowledgeBaseId(knowledgeBaseId);
        file.setFileName(fileName);
        file.setFileSize(fileSize);
        file.setFileType(fileType);
        file.setFileHash(fileHash);
        file.setIndexingMethod("semantic");
        file.setStatus("pending");
        file.setFileMetadata(fileMetadata != null ? fileMetadata : new HashMap<>());
        insert(file);
        return findById(file.getId()).orElseThrow();
    }

    default Optional<KnowledgeFile> findById(int id) {
        return Optional.ofNullable(selectOne(new LambdaQueryWrapper<KnowledgeFile>()
                .eq(KnowledgeFile::getId, id)
                .eq(KnowledgeFile::getIsDeleted, 0)));
    }

    default Optional<KnowledgeFile> findExistingByHash(int kbId, String hash) {
        return Optional.ofNullable(selectOne(new LambdaQueryWrapper<KnowledgeFile>()
                .eq(KnowledgeFile::getKnowledgeBaseId, kbId)
                .eq(KnowledgeFile::getFileHash, hash)
                .eq(KnowledgeFile::getIsDeleted, 0)));
    }

    default List<KnowledgeFile> findByKnowledgeBase(int kbId, int skip, int limit) {
        return selectList(new LambdaQueryWrapper<KnowledgeFile>()
                .eq(KnowledgeFile::getKnowledgeBaseId, kbId)
                .eq(KnowledgeFile::getIsDeleted, 0)
                .orderByDesc(KnowledgeFile::getCreatedAt)
                .last("LIMIT " + limit + " OFFSET " + skip));
    }

    @Update("""
            UPDATE knowledge_files
            SET status = #{status,jdbcType=VARCHAR},
                error_message = COALESCE(#{errorMessage,jdbcType=VARCHAR}, error_message),
                updated_at = now()
            WHERE id = #{id}
            """)
    int updateStatus(@Param("id") int id, @Param("status") String status,
                     @Param("errorMessage") String errorMessage);

    @Update("""
            UPDATE knowledge_files
            SET file_metadata =
                #{fileMetadata,jdbcType=OTHER,typeHandler=com.xiafan.agent.repository.PostgresJsonTypeHandler},
                updated_at = now()
            WHERE id = #{id}
            """)
    int updateMetadata(@Param("id") int id, @Param("fileMetadata") Map<String, Object> fileMetadata);

    @Update("""
            UPDATE knowledge_files
            SET is_deleted = 1, updated_at = now()
            WHERE id = #{id} AND is_deleted = 0
            """)
    int softDelete(@Param("id") int id);
}
