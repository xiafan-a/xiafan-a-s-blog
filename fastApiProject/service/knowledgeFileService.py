from typing import List, Optional
from sqlalchemy.orm import Session

from entity.KnowledgeFile import KnowledgeFile
from database import KnowledgeFileModel


class KnowledgeFileService:
    def __init__(self):
        pass

    def create_knowledge_file(self, db: Session, knowledge_base_id: int, file_name: str,
                             file_size: int, file_hash: str, file_type: Optional[str] = None,
                             indexing_method: str = "semantic", status: str = "pending",
                             error_message: Optional[str] = None,
                             file_metadata: Optional[dict] = None) -> KnowledgeFile:
        """创建新的知识文件"""
        db_file = KnowledgeFileModel(
            knowledge_base_id=knowledge_base_id,
            file_name=file_name,
            file_size=file_size,
            file_type=file_type,
            file_hash=file_hash,
            indexing_method=indexing_method,
            status=status,
            error_message=error_message,
            file_metadata=file_metadata or {}
        )
        db.add(db_file)
        db.commit()
        db.refresh(db_file)

        return self._to_pydantic(db_file)

    def get_knowledge_file_by_id(self, db: Session, file_id: int) -> Optional[KnowledgeFile]:
        """根据ID获取知识文件"""
        db_file = db.query(KnowledgeFileModel).filter(
            KnowledgeFileModel.id == file_id,
            KnowledgeFileModel.is_deleted == 0
        ).first()

        if not db_file:
            return None

        return self._to_pydantic(db_file)

    def get_files_by_knowledge_base(self, db: Session, knowledge_base_id: int,
                                   skip: int = 0, limit: int = 100) -> List[KnowledgeFile]:
        """根据知识库ID获取文件列表"""
        db_files = db.query(KnowledgeFileModel).filter(
            KnowledgeFileModel.knowledge_base_id == knowledge_base_id,
            KnowledgeFileModel.is_deleted == 0
        ).order_by(KnowledgeFileModel.created_at.desc()).offset(skip).limit(limit).all()

        return [self._to_pydantic(f) for f in db_files]

    def update_file_status(self, db: Session, file_id: int, status: str,
                          error_message: Optional[str] = None) -> Optional[KnowledgeFile]:
        """更新文件状态"""
        db_file = db.query(KnowledgeFileModel).filter(
            KnowledgeFileModel.id == file_id,
            KnowledgeFileModel.is_deleted == 0
        ).first()

        if not db_file:
            return None

        db_file.status = status
        if error_message is not None:
            db_file.error_message = error_message
        db.commit()
        db.refresh(db_file)

        return self._to_pydantic(db_file)

    def update_file_metadata(self, db: Session, file_id: int,
                            file_metadata: dict) -> Optional[KnowledgeFile]:
        """更新文件元数据"""
        db_file = db.query(KnowledgeFileModel).filter(
            KnowledgeFileModel.id == file_id,
            KnowledgeFileModel.is_deleted == 0
        ).first()

        if not db_file:
            return None

        # 合并元数据
        if db_file.file_metadata:
            db_file.file_metadata = {**db_file.file_metadata, **file_metadata}
        else:
            db_file.file_metadata = file_metadata
        db.commit()
        db.refresh(db_file)

        return self._to_pydantic(db_file)

    def soft_delete_file(self, db: Session, file_id: int) -> bool:
        """软删除知识文件"""
        db_file = db.query(KnowledgeFileModel).filter(
            KnowledgeFileModel.id == file_id,
            KnowledgeFileModel.is_deleted == 0
        ).first()

        if not db_file:
            return False

        db_file.is_deleted = 1
        db.commit()
        return True

    def get_file_by_hash(self, db: Session, knowledge_base_id: int, file_hash: str) -> Optional[KnowledgeFile]:
        """根据文件哈希查找文件（用于去重）"""
        db_file = db.query(KnowledgeFileModel).filter(
            KnowledgeFileModel.knowledge_base_id == knowledge_base_id,
            KnowledgeFileModel.file_hash == file_hash,
            KnowledgeFileModel.is_deleted == 0
        ).first()

        if not db_file:
            return None

        return self._to_pydantic(db_file)

    def _to_pydantic(self, db_file: KnowledgeFileModel) -> KnowledgeFile:
        """ORM模型转Pydantic实体"""
        return KnowledgeFile(
            id=db_file.id,
            knowledge_base_id=db_file.knowledge_base_id,
            file_name=db_file.file_name,
            file_size=db_file.file_size,
            file_type=db_file.file_type,
            file_hash=db_file.file_hash,
            indexing_method=db_file.indexing_method,
            status=db_file.status,
            error_message=db_file.error_message,
            file_metadata=db_file.file_metadata,
            created_at=db_file.created_at,
            updated_at=db_file.updated_at,
            is_deleted=db_file.is_deleted
        )


# 全局KnowledgeFile服务实例
knowledge_file_service = KnowledgeFileService()
