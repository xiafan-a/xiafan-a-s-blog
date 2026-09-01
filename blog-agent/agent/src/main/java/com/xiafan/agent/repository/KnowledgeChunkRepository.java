package com.xiafan.agent.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.xiafan.agent.entity.KnowledgeChunk;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

import java.util.List;
import java.util.Map;
import java.util.Optional;

public interface KnowledgeChunkRepository extends BaseMapper<KnowledgeChunk> {

    default KnowledgeChunk insert(int knowledgeBaseId, Integer knowledgeFileId, int chunkIndex, String content,
                                  List<Double> embedding, Map<String, Object> chunkMetadata, String indexingMethod) {
        KnowledgeChunk chunk = new KnowledgeChunk();
        chunk.setKnowledgeBaseId(knowledgeBaseId);
        chunk.setKnowledgeFileId(knowledgeFileId);
        chunk.setChunkIndex(chunkIndex);
        chunk.setContent(content);
        chunk.setEmbedding(embedding);
        chunk.setChunkMetadata(chunkMetadata != null ? chunkMetadata : Map.of());
        chunk.setIndexingMethod(indexingMethod);
        insert(chunk);
        return findById(chunk.getId()).orElseThrow();
    }

    default Optional<KnowledgeChunk> findById(int id) {
        return Optional.ofNullable(selectOne(new LambdaQueryWrapper<KnowledgeChunk>()
                .eq(KnowledgeChunk::getId, id)
                .eq(KnowledgeChunk::getIsDeleted, 0)));
    }

    default List<KnowledgeChunk> findByKnowledgeBase(int kbId, int skip, int limit) {
        return selectList(new LambdaQueryWrapper<KnowledgeChunk>()
                .eq(KnowledgeChunk::getKnowledgeBaseId, kbId)
                .eq(KnowledgeChunk::getIsDeleted, 0)
                .orderByAsc(KnowledgeChunk::getChunkIndex)
                .last("LIMIT " + limit + " OFFSET " + skip));
    }

    default List<KnowledgeChunk> findByFile(int fileId, int skip, int limit) {
        return selectList(new LambdaQueryWrapper<KnowledgeChunk>()
                .eq(KnowledgeChunk::getKnowledgeFileId, fileId)
                .eq(KnowledgeChunk::getIsDeleted, 0)
                .orderByAsc(KnowledgeChunk::getChunkIndex)
                .last("LIMIT " + limit + " OFFSET " + skip));
    }

    default List<KnowledgeChunk> findAllWithEmbedding(int kbId) {
        return selectList(new LambdaQueryWrapper<KnowledgeChunk>()
                .eq(KnowledgeChunk::getKnowledgeBaseId, kbId)
                .eq(KnowledgeChunk::getIsDeleted, 0)
                .isNotNull(KnowledgeChunk::getEmbedding));
    }

    default List<KnowledgeChunk> findBefore(int kbId, int chunkIndex, int limit) {
        return selectList(new LambdaQueryWrapper<KnowledgeChunk>()
                .eq(KnowledgeChunk::getKnowledgeBaseId, kbId)
                .eq(KnowledgeChunk::getIsDeleted, 0)
                .lt(KnowledgeChunk::getChunkIndex, chunkIndex)
                .orderByDesc(KnowledgeChunk::getChunkIndex)
                .last("LIMIT " + limit));
    }

    default List<KnowledgeChunk> findAfter(int kbId, int chunkIndex, int limit) {
        return selectList(new LambdaQueryWrapper<KnowledgeChunk>()
                .eq(KnowledgeChunk::getKnowledgeBaseId, kbId)
                .eq(KnowledgeChunk::getIsDeleted, 0)
                .gt(KnowledgeChunk::getChunkIndex, chunkIndex)
                .orderByAsc(KnowledgeChunk::getChunkIndex)
                .last("LIMIT " + limit));
    }

    @Update("""
            UPDATE knowledge_chunks
            SET content = COALESCE(#{content,jdbcType=VARCHAR}, content),
                embedding = COALESCE(
                    #{embedding,jdbcType=OTHER,typeHandler=com.xiafan.agent.repository.PostgresJsonTypeHandler},
                    embedding),
                chunk_metadata = COALESCE(
                    #{chunkMetadata,jdbcType=OTHER,typeHandler=com.xiafan.agent.repository.PostgresJsonTypeHandler},
                    chunk_metadata),
                updated_at = now()
            WHERE id = #{id} AND is_deleted = 0
            """)
    int update(@Param("id") int id, @Param("content") String content,
               @Param("embedding") List<Double> embedding, @Param("chunkMetadata") Map<String, Object> chunkMetadata);

    @Update("""
            UPDATE knowledge_chunks
            SET is_deleted = 1, updated_at = now()
            WHERE id = #{id} AND is_deleted = 0
            """)
    int softDelete(@Param("id") int id);

    @Update("""
            UPDATE knowledge_chunks
            SET is_deleted = 1, updated_at = now()
            WHERE knowledge_file_id = #{fileId} AND is_deleted = 0
            """)
    int deleteByFile(@Param("fileId") int fileId);
}
