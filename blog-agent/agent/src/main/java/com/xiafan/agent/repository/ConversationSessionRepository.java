package com.xiafan.agent.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.xiafan.agent.entity.ConversationSession;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

import java.util.List;
import java.util.Optional;

public interface ConversationSessionRepository extends BaseMapper<ConversationSession> {

    default ConversationSession insert(int knowledgeBaseId, String title) {
        ConversationSession session = new ConversationSession();
        session.setKnowledgeBaseId(knowledgeBaseId);
        session.setTitle(title);
        insert(session);
        return findById(session.getId()).orElseThrow();
    }

    default Optional<ConversationSession> findById(int id) {
        return Optional.ofNullable(selectOne(new LambdaQueryWrapper<ConversationSession>()
                .eq(ConversationSession::getId, id)
                .eq(ConversationSession::getIsDeleted, 0)));
    }

    default List<ConversationSession> findByKnowledgeBase(int kbId, int skip, int limit) {
        return selectList(new LambdaQueryWrapper<ConversationSession>()
                .eq(ConversationSession::getKnowledgeBaseId, kbId)
                .eq(ConversationSession::getIsDeleted, 0)
                .orderByDesc(ConversationSession::getUpdatedAt)
                .last("LIMIT " + limit + " OFFSET " + skip));
    }

    default List<ConversationSession> findByKnowledgeBase(int kbId) {
        return selectList(new LambdaQueryWrapper<ConversationSession>()
                .eq(ConversationSession::getKnowledgeBaseId, kbId)
                .eq(ConversationSession::getIsDeleted, 0)
                .orderByDesc(ConversationSession::getUpdatedAt));
    }

    default List<ConversationSession> findStandalone() {
        // 智能体独立会话：没有绑定知识库（knowledge_base_id = -1）
        return selectList(new LambdaQueryWrapper<ConversationSession>()
                .eq(ConversationSession::getKnowledgeBaseId, -1)
                .eq(ConversationSession::getIsDeleted, 0)
                .orderByDesc(ConversationSession::getUpdatedAt));
    }

    default List<ConversationSession> findByNameContaining(String name) {
        return selectList(new LambdaQueryWrapper<ConversationSession>()
                .eq(ConversationSession::getIsDeleted, 0)
                .like(ConversationSession::getTitle, name)
                .orderByDesc(ConversationSession::getUpdatedAt));
    }

    @Update("""
            UPDATE conversation_session
            SET title = COALESCE(#{title,jdbcType=VARCHAR}, title), updated_at = now()
            WHERE id = #{id} AND is_deleted = 0
            """)
    int update(@Param("id") int id, @Param("title") String title);

    @Update("""
            UPDATE conversation_session
            SET is_deleted = 1, updated_at = now()
            WHERE id = #{id} AND is_deleted = 0
            """)
    int softDelete(@Param("id") int id);
}
