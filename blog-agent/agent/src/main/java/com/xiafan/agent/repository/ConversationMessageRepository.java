package com.xiafan.agent.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.xiafan.agent.entity.ConversationMessage;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

import java.util.List;
import java.util.Map;
import java.util.Optional;

public interface ConversationMessageRepository extends BaseMapper<ConversationMessage> {

    default ConversationMessage insert(int knowledgeBaseId, String role, String content, Integer sessionId,
                                      Integer parentMessageId, Integer contextWindow, String contextSummary,
                                      List<Object> sources, Map<String, Object> tokenUsage, Integer feedback,
                                      Map<String, Object> messageMetadata) {
        ConversationMessage message = new ConversationMessage();
        message.setKnowledgeBaseId(knowledgeBaseId);
        message.setRole(role);
        message.setContent(content);
        message.setSessionId(sessionId);
        message.setParentMessageId(parentMessageId);
        message.setContextWindow(contextWindow != null ? contextWindow : 10);
        message.setContextSummary(contextSummary);
        message.setSources(sources != null ? sources : List.of());
        message.setTokenUsage(tokenUsage != null ? tokenUsage : Map.of());
        message.setFeedback(feedback);
        message.setMessageMetadata(messageMetadata != null ? messageMetadata : Map.of());
        insert(message);
        return findById(message.getId()).orElseThrow();
    }

    default Optional<ConversationMessage> findById(int id) {
        return Optional.ofNullable(selectOne(new LambdaQueryWrapper<ConversationMessage>()
                .eq(ConversationMessage::getId, id)
                .eq(ConversationMessage::getIsDeleted, 0)));
    }

    default List<ConversationMessage> findByKnowledgeBase(int kbId, int skip, int limit) {
        return selectList(new LambdaQueryWrapper<ConversationMessage>()
                .eq(ConversationMessage::getKnowledgeBaseId, kbId)
                .eq(ConversationMessage::getIsDeleted, 0)
                .orderByDesc(ConversationMessage::getCreatedAt)
                .last("LIMIT " + limit + " OFFSET " + skip));
    }

    default List<ConversationMessage> findBySession(int sessionId, int skip, int limit) {
        return selectList(new LambdaQueryWrapper<ConversationMessage>()
                .eq(ConversationMessage::getSessionId, sessionId)
                .eq(ConversationMessage::getIsDeleted, 0)
                .orderByAsc(ConversationMessage::getCreatedAt)
                .last("LIMIT " + limit + " OFFSET " + skip));
    }

    @Update("""
            UPDATE conversation_messages
            SET content = #{content,jdbcType=VARCHAR}, updated_at = now()
            WHERE id = #{id} AND is_deleted = 0
            """)
    int updateContent(@Param("id") int id, @Param("content") String content);

    @Update("""
            UPDATE conversation_messages
            SET feedback = #{feedback,jdbcType=INTEGER}, updated_at = now()
            WHERE id = #{id} AND is_deleted = 0
            """)
    int updateFeedback(@Param("id") int id, @Param("feedback") Integer feedback);

    @Update("""
            UPDATE conversation_messages
            SET is_deleted = 1, updated_at = now()
            WHERE id = #{id} AND is_deleted = 0
            """)
    int softDelete(@Param("id") int id);
}
