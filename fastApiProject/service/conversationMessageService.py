from typing import List, Optional, Dict, Any
from sqlalchemy.orm import Session

from entity.ConversationMessage import ConversationMessage
from database import ConversationMessageModel


class ConversationMessageService:
    def __init__(self):
        pass

    def create_message(self, db: Session, knowledge_base_id: int, role: str, content: str,
                      session_id: Optional[int] = None, parent_message_id: Optional[int] = None,
                      context_window: int = 10, context_summary: Optional[str] = None,
                      sources: Optional[list] = None, token_usage: Optional[dict] = None,
                      feedback: Optional[int] = None,
                      message_metadata: Optional[dict] = None) -> ConversationMessage:
        """创建新的对话消息"""
        db_message = ConversationMessageModel(
            knowledge_base_id=knowledge_base_id,
            role=role,
            content=content,
            session_id=session_id,
            parent_message_id=parent_message_id,
            context_window=context_window,
            context_summary=context_summary,
            sources=sources or [],
            token_usage=token_usage or {},
            feedback=feedback,
            message_metadata=message_metadata or {}
        )
        db.add(db_message)
        db.commit()
        db.refresh(db_message)

        return self._to_pydantic(db_message)

    def get_message_by_id(self, db: Session, message_id: int) -> Optional[ConversationMessage]:
        """根据ID获取对话消息"""
        db_message = db.query(ConversationMessageModel).filter(
            ConversationMessageModel.id == message_id,
            ConversationMessageModel.is_deleted == 0
        ).first()

        if not db_message:
            return None

        return self._to_pydantic(db_message)

    def get_messages_by_knowledge_base(self, db: Session, knowledge_base_id: int,
                                       skip: int = 0, limit: int = 100) -> List[ConversationMessage]:
        """根据知识库ID获取对话消息列表"""
        db_messages = db.query(ConversationMessageModel).filter(
            ConversationMessageModel.knowledge_base_id == knowledge_base_id,
            ConversationMessageModel.is_deleted == 0
        ).order_by(ConversationMessageModel.created_at.desc()).offset(skip).limit(limit).all()

        return [self._to_pydantic(msg) for msg in db_messages]

    def get_messages_by_session(self, db: Session, session_id: int,
                                skip: int = 0, limit: int = 100) -> List[ConversationMessage]:
        """根据会话ID获取对话消息列表"""
        db_messages = db.query(ConversationMessageModel).filter(
            ConversationMessageModel.session_id == session_id,
            ConversationMessageModel.is_deleted == 0
        ).order_by(ConversationMessageModel.created_at.asc()).offset(skip).limit(limit).all()

        return [self._to_pydantic(msg) for msg in db_messages]

    def update_message_feedback(self, db: Session, message_id: int, feedback: int) -> Optional[ConversationMessage]:
        """更新消息反馈"""
        db_message = db.query(ConversationMessageModel).filter(
            ConversationMessageModel.id == message_id,
            ConversationMessageModel.is_deleted == 0
        ).first()

        if not db_message:
            return None

        db_message.feedback = feedback
        db.commit()
        db.refresh(db_message)

        return self._to_pydantic(db_message)

    def soft_delete_message(self, db: Session, message_id: int) -> bool:
        """软删除对话消息"""
        db_message = db.query(ConversationMessageModel).filter(
            ConversationMessageModel.id == message_id,
            ConversationMessageModel.is_deleted == 0
        ).first()

        if not db_message:
            return False

        db_message.is_deleted = 1
        db.commit()
        return True

    def delete_messages_by_session(self, db: Session, session_id: int) -> int:
        """删除会话关联的所有消息"""
        result = db.query(ConversationMessageModel).filter(
            ConversationMessageModel.session_id == session_id,
            ConversationMessageModel.is_deleted == 0
        ).update({"is_deleted": 1})
        db.commit()
        return result

    def _to_pydantic(self, db_message: ConversationMessageModel) -> ConversationMessage:
        """ORM模型转Pydantic实体"""
        return ConversationMessage(
            id=db_message.id,
            knowledge_base_id=db_message.knowledge_base_id,
            role=db_message.role,
            content=db_message.content,
            session_id=db_message.session_id,
            parent_message_id=db_message.parent_message_id,
            context_window=db_message.context_window,
            context_summary=db_message.context_summary,
            sources=db_message.sources,
            token_usage=db_message.token_usage,
            feedback=db_message.feedback,
            message_metadata=db_message.message_metadata,
            created_at=db_message.created_at,
            updated_at=db_message.updated_at,
            is_deleted=db_message.is_deleted
        )


# 全局ConversationMessage服务实例
conversation_message_service = ConversationMessageService()
