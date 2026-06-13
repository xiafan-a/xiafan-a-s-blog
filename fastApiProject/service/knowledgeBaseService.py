from typing import List, Optional
from entity.KnowledgeBase import KnowledgeBase
from database import SessionLocal, KnowledgeBaseModel
from sqlalchemy.orm import Session


class KnowledgeBaseService:
    def __init__(self):
        pass

    def create_knowledge_base(self, name: str, description: Optional[str] = None, 
                             system_prompt: Optional[str] = None) -> KnowledgeBase:
        """创建新的知识库"""
        db = SessionLocal()
        try:
            db_kb = KnowledgeBaseModel(
                name=name,
                description=description,
                system_prompt=system_prompt
            )
            db.add(db_kb)
            db.commit()
            db.refresh(db_kb)
            
            # 转换为Pydantic模型
            return KnowledgeBase(
                id=db_kb.id,
                name=db_kb.name,
                description=db_kb.description,
                system_prompt=db_kb.system_prompt,
                created_at=db_kb.created_at,
                updated_at=db_kb.updated_at,
                is_deleted=db_kb.is_deleted
            )
        finally:
            db.close()

    def get_knowledge_base_by_id(self, kb_id: int) -> Optional[KnowledgeBase]:
        """根据ID获取知识库"""
        db = SessionLocal()
        try:
            db_kb = db.query(KnowledgeBaseModel).filter(
                KnowledgeBaseModel.id == kb_id,
                KnowledgeBaseModel.is_deleted == 0
            ).first()
            
            if not db_kb:
                return None
            
            # 转换为Pydantic模型
            return KnowledgeBase(
                id=db_kb.id,
                name=db_kb.name,
                description=db_kb.description,
                system_prompt=db_kb.system_prompt,
                created_at=db_kb.created_at,
                updated_at=db_kb.updated_at,
                is_deleted=db_kb.is_deleted
            )
        finally:
            db.close()

    def get_knowledge_bases(self, skip: int = 0, limit: int = 100) -> List[KnowledgeBase]:
        """获取知识库列表"""
        db = SessionLocal()
        try:
            db_kbs = db.query(KnowledgeBaseModel).filter(
                KnowledgeBaseModel.is_deleted == 0
            ).offset(skip).limit(limit).all()
            
            # 转换为Pydantic模型列表
            return [
                KnowledgeBase(
                    id=kb.id,
                    name=kb.name,
                    description=kb.description,
                    system_prompt=kb.system_prompt,
                    created_at=kb.created_at,
                    updated_at=kb.updated_at,
                    is_deleted=kb.is_deleted
                )
                for kb in db_kbs
            ]
        finally:
            db.close()

    def update_knowledge_base(self, kb_id: int, name: Optional[str] = None, 
                             description: Optional[str] = None, system_prompt: Optional[str] = None) -> Optional[KnowledgeBase]:
        """更新知识库信息"""
        db = SessionLocal()
        try:
            db_kb = db.query(KnowledgeBaseModel).filter(
                KnowledgeBaseModel.id == kb_id,
                KnowledgeBaseModel.is_deleted == 0
            ).first()
            
            if not db_kb:
                return None
            
            if name is not None:
                db_kb.name = name
            if description is not None:
                db_kb.description = description
            if system_prompt is not None:
                db_kb.system_prompt = system_prompt
            
            db.commit()
            db.refresh(db_kb)
            
            # 转换为Pydantic模型
            return KnowledgeBase(
                id=db_kb.id,
                name=db_kb.name,
                description=db_kb.description,
                system_prompt=db_kb.system_prompt,
                created_at=db_kb.created_at,
                updated_at=db_kb.updated_at,
                is_deleted=db_kb.is_deleted
            )
        finally:
            db.close()

    def soft_delete_knowledge_base(self, kb_id: int) -> bool:
        """软删除知识库"""
        db = SessionLocal()
        try:
            db_kb = db.query(KnowledgeBaseModel).filter(
                KnowledgeBaseModel.id == kb_id,
                KnowledgeBaseModel.is_deleted == 0
            ).first()
            
            if not db_kb:
                return False
            
            db_kb.is_deleted = 1
            db.commit()
            return True
        finally:
            db.close()

    def delete_knowledge_base(self, kb_id: int) -> bool:
        """硬删除知识库"""
        db = SessionLocal()
        try:
            db_kb = db.query(KnowledgeBaseModel).filter(
                KnowledgeBaseModel.id == kb_id,
                KnowledgeBaseModel.is_deleted == 0
            ).first()
            
            if not db_kb:
                return False
            
            db.delete(db_kb)
            db.commit()
            return True
        finally:
            db.close()


# 全局KnowledgeBase服务实例
knowledge_base_service = KnowledgeBaseService()
