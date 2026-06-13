from sqlalchemy.orm import Session
from typing import List, Optional

from entity.ConversationSession import ConversationSession
from database import get_db, ConversationSessionModel


class ConversationSessionService:
    def __init__(self):
        pass

    def create_session(self, db: Session, knowledge_base_id: int, title: Optional[str] = None) -> ConversationSession:
        """创建新的对话会话"""
        db_session = ConversationSessionModel(
            knowledge_base_id=knowledge_base_id,
            title=title
        )
        db.add(db_session)
        db.commit()
        db.refresh(db_session)

        # 转换为Pydantic模型
        return ConversationSession(
            id=db_session.id,
            knowledge_base_id=db_session.knowledge_base_id,
            title=db_session.title,
            created_at=db_session.created_at,
            updated_at=db_session.updated_at,
            is_deleted=db_session.is_deleted
        )

    def get_session_by_id(self, db: Session, session_id: int) -> Optional[ConversationSession]:
        """根据ID获取对话会话"""
        db_session = db.query(ConversationSessionModel).filter(
            ConversationSessionModel.id == session_id,
            ConversationSessionModel.is_deleted == 0
        ).first()

        if not db_session:
            return None

        # 转换为Pydantic模型
        return ConversationSession(
            id=db_session.id,
            knowledge_base_id=db_session.knowledge_base_id,
            title=db_session.title,
            created_at=db_session.created_at,
            updated_at=db_session.updated_at,
            is_deleted=db_session.is_deleted
        )

    def get_sessions_by_knowledge_base(self, db: Session, knowledge_base_id: int, skip: int = 0, limit: int = 100) -> \
            List[ConversationSession]:
        """根据知识库ID获取对话会话列表"""
        db_sessions = db.query(ConversationSessionModel).filter(
            ConversationSessionModel.knowledge_base_id == knowledge_base_id,
            ConversationSessionModel.is_deleted == 0
        ).order_by(ConversationSessionModel.updated_at.desc()).offset(skip).limit(limit).all()

        # 转换为Pydantic模型列表
        return [
            ConversationSession(
                id=session.id,
                knowledge_base_id=session.knowledge_base_id,
                title=session.title,
                created_at=session.created_at,
                updated_at=session.updated_at,
                is_deleted=session.is_deleted
            )
            for session in db_sessions
        ]

    def update_session(self, db: Session, session_id: int, title: Optional[str] = None) -> Optional[
        ConversationSession]:
        """更新对话会话信息"""
        db_session = db.query(ConversationSessionModel).filter(
            ConversationSessionModel.id == session_id,
            ConversationSessionModel.is_deleted == 0
        ).first()

        if not db_session:
            return None

        if title is not None:
            db_session.title = title

        db.commit()
        db.refresh(db_session)

        # 转换为Pydantic模型
        return ConversationSession(
            id=db_session.id,
            knowledge_base_id=db_session.knowledge_base_id,
            title=db_session.title,
            created_at=db_session.created_at,
            updated_at=db_session.updated_at,
            is_deleted=db_session.is_deleted
        )

    def soft_delete_session(self, db: Session, session_id: int) -> bool:
        """软删除对话会话"""
        db_session = db.query(ConversationSessionModel).filter(
            ConversationSessionModel.id == session_id,
            ConversationSessionModel.is_deleted == 0
        ).first()

        if not db_session:
            return False

        db_session.is_deleted = 1
        db.commit()
        return True

    def get_session_by_name(self, db: Session, session_name: str,knowledge_base_id: int = -1) -> Optional[ConversationSession]:
        """根据名称获取对话会话"""
        db_session = db.query(ConversationSessionModel).filter(
            ConversationSessionModel.title.like(f"%{session_name}%"),
            ConversationSessionModel.is_deleted == 0,
            ConversationSessionModel.knowledge_base_id == knowledge_base_id
        )

        if not db_session:
            return None
        res = []
        for session in db_session:
            res.append(ConversationSession(
                id=db_session.id,
                knowledge_base_id=db_session.knowledge_base_id,
                title=db_session.title,
                created_at=db_session.created_at,
                updated_at=db_session.updated_at,
                is_deleted=db_session.is_deleted
            ))
        return res


conversation_session_service = ConversationSessionService()
