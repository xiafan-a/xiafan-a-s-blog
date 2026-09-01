package com.xiafan.agent.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.xiafan.agent.entity.KnowledgeBase;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

import java.util.List;
import java.util.Optional;

public interface KnowledgeBaseRepository extends BaseMapper<KnowledgeBase> {

    default KnowledgeBase insert(String name, String description, String systemPrompt) {
        KnowledgeBase knowledgeBase = new KnowledgeBase();
        knowledgeBase.setName(name);
        knowledgeBase.setDescription(description);
        knowledgeBase.setSystemPrompt(systemPrompt);
        insert(knowledgeBase);
        return findById(knowledgeBase.getId()).orElseThrow();
    }

    default Optional<KnowledgeBase> findById(int id) {
        return Optional.ofNullable(selectOne(new LambdaQueryWrapper<KnowledgeBase>()
                .eq(KnowledgeBase::getId, id)
                .eq(KnowledgeBase::getIsDeleted, 0)));
    }

    default List<KnowledgeBase> findAll(int skip, int limit) {
        return selectList(new LambdaQueryWrapper<KnowledgeBase>()
                .eq(KnowledgeBase::getIsDeleted, 0)
                .orderByAsc(KnowledgeBase::getId)
                .last("LIMIT " + limit + " OFFSET " + skip));
    }

    @Update("""
            UPDATE knowledge_bases
            SET name = COALESCE(#{name,jdbcType=VARCHAR}, name),
                description = COALESCE(#{description,jdbcType=VARCHAR}, description),
                system_prompt = COALESCE(#{systemPrompt,jdbcType=VARCHAR}, system_prompt),
                updated_at = now()
            WHERE id = #{id} AND is_deleted = 0
            """)
    int update(@Param("id") int id, @Param("name") String name,
               @Param("description") String description, @Param("systemPrompt") String systemPrompt);

    @Update("""
            UPDATE knowledge_bases
            SET is_deleted = 1, updated_at = now()
            WHERE id = #{id} AND is_deleted = 0
            """)
    int softDelete(@Param("id") int id);
}
