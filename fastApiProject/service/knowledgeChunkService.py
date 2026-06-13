from sqlalchemy.orm import Session
from typing import List, Optional, Dict, Any
from datetime import datetime

from entity.KnowledgeChunk import KnowledgeChunk
from database import KnowledgeChunkModel
from service.embeddingService import embedding_service
from config.settings import RAG_TOP_K, RAG_THRESHOLD


class KnowledgeChunkService:
    def __init__(self):
        pass

    def create_chunk(self, db: Session, knowledge_base_id: int, chunk_index: int, content: str,
                    knowledge_file_id: Optional[int] = None, embedding: Optional[List[float]] = None,
                    metadata: Optional[Dict[str, Any]] = None,
                    indexing_method: Optional[str] = "semantic") -> KnowledgeChunk:
        """创建新的知识分块"""
        db_chunk = KnowledgeChunkModel(
            knowledge_base_id=knowledge_base_id,
            knowledge_file_id=knowledge_file_id,
            chunk_index=chunk_index,
            content=content,
            embedding=embedding,
            chunk_metadata=metadata or {},
            indexing_method=indexing_method,
            created_at=datetime.now(),
            updated_at=datetime.now(),
            is_deleted=0
        )
        db.add(db_chunk)
        db.commit()
        db.refresh(db_chunk)

        return self._to_pydantic(db_chunk)

    def create_chunk_with_embedding(self, db: Session, knowledge_base_id: int, chunk_index: int,
                                    content: str, knowledge_file_id: Optional[int] = None,
                                    metadata: Optional[Dict[str, Any]] = None) -> KnowledgeChunk:
        """创建知识分块并生成向量"""
        # 生成embedding
        try:
            embedding = embedding_service.encode_single(content)
        except NotImplementedError:
            embedding = None

        return self.create_chunk(
            db=db,
            knowledge_base_id=knowledge_base_id,
            knowledge_file_id=knowledge_file_id,
            chunk_index=chunk_index,
            content=content,
            embedding=embedding,
            metadata=metadata
        )

    def batch_create_chunks(self, db: Session, knowledge_base_id: int,
                           chunks_data: List[dict],
                           knowledge_file_id: Optional[int] = None) -> List[KnowledgeChunk]:
        """
        批量创建分块
        Args:
            db: 数据库会话
            knowledge_base_id: 知识库ID
            chunks_data: 分块数据列表，每个包含 content, chunk_index, metadata
            knowledge_file_id: 文件ID
        Returns:
            创建的分块列表
        """
        # 批量生成embedding（每批最多10个文本，因为API限制）
        contents = [c['content'] for c in chunks_data]
        embeddings = []
        batch_size = 10  # API限制每批最多10个文本

        try:
            for i in range(0, len(contents), batch_size):
                batch_contents = contents[i:i + batch_size]
                batch_embeddings = embedding_service.encode(batch_contents)
                embeddings.extend(batch_embeddings)
        except NotImplementedError:
            embeddings = [None] * len(contents)
        except Exception as e:
            embeddings = [None] * len(contents)

        # 批量写入数据库
        db_chunks = []
        for i, chunk_data in enumerate(chunks_data):
            db_chunk = KnowledgeChunkModel(
                knowledge_base_id=knowledge_base_id,
                knowledge_file_id=knowledge_file_id,
                chunk_index=chunk_data['chunk_index'],
                content=chunk_data['content'],
                embedding=embeddings[i],
                chunk_metadata=chunk_data.get('metadata', {}),
                indexing_method="semantic",
                created_at=datetime.now(),
                updated_at=datetime.now(),
                is_deleted=0
            )
            db.add(db_chunk)
            db_chunks.append(db_chunk)

        db.commit()
        for chunk in db_chunks:
            db.refresh(chunk)

        return [self._to_pydantic(chunk) for chunk in db_chunks]

    def get_chunk_by_id(self, db: Session, chunk_id: int) -> Optional[KnowledgeChunk]:
        """根据ID获取知识分块"""
        db_chunk = db.query(KnowledgeChunkModel).filter(
            KnowledgeChunkModel.id == chunk_id,
            KnowledgeChunkModel.is_deleted == 0
        ).first()

        if not db_chunk:
            return None

        return self._to_pydantic(db_chunk)

    def get_chunks_by_knowledge_base(self, db: Session, knowledge_base_id: int,
                                    skip: int = 0, limit: int = 100) -> List[KnowledgeChunk]:
        """根据知识库ID获取知识分块列表"""
        db_chunks = db.query(KnowledgeChunkModel).filter(
            KnowledgeChunkModel.knowledge_base_id == knowledge_base_id,
            KnowledgeChunkModel.is_deleted == 0
        ).order_by(KnowledgeChunkModel.chunk_index.asc()).offset(skip).limit(limit).all()

        return [self._to_pydantic(chunk) for chunk in db_chunks]

    def get_chunks_by_file(self, db: Session, knowledge_file_id: int,
                          skip: int = 0, limit: int = 100) -> List[KnowledgeChunk]:
        """根据文件ID获取知识分块列表"""
        db_chunks = db.query(KnowledgeChunkModel).filter(
            KnowledgeChunkModel.knowledge_file_id == knowledge_file_id,
            KnowledgeChunkModel.is_deleted == 0
        ).order_by(KnowledgeChunkModel.chunk_index.asc()).offset(skip).limit(limit).all()

        return [self._to_pydantic(chunk) for chunk in db_chunks]

    def update_chunk(self, db: Session, chunk_id: int, content: Optional[str] = None,
                    embedding: Optional[List[float]] = None,
                    metadata: Optional[Dict[str, Any]] = None) -> Optional[KnowledgeChunk]:
        """更新知识分块"""
        db_chunk = db.query(KnowledgeChunkModel).filter(
            KnowledgeChunkModel.id == chunk_id,
            KnowledgeChunkModel.is_deleted == 0
        ).first()

        if not db_chunk:
            return None

        if content is not None:
            db_chunk.content = content
        if embedding is not None:
            db_chunk.embedding = embedding
        if metadata is not None:
            db_chunk.chunk_metadata = metadata

        db_chunk.updated_at = datetime.now()
        db.commit()
        db.refresh(db_chunk)

        return self._to_pydantic(db_chunk)

    def delete_chunk(self, db: Session, chunk_id: int) -> bool:
        """软删除知识分块"""
        db_chunk = db.query(KnowledgeChunkModel).filter(
            KnowledgeChunkModel.id == chunk_id,
            KnowledgeChunkModel.is_deleted == 0
        ).first()

        if not db_chunk:
            return False

        db_chunk.is_deleted = 1
        db.commit()
        return True

    def delete_chunks_by_file(self, db: Session, knowledge_file_id: int) -> int:
        """删除文件关联的所有分块"""
        result = db.query(KnowledgeChunkModel).filter(
            KnowledgeChunkModel.knowledge_file_id == knowledge_file_id,
            KnowledgeChunkModel.is_deleted == 0
        ).update({"is_deleted": 1})
        db.commit()
        return result

    def get_chunks_by_index_range(self, db: Session, knowledge_base_id: int,
                                   chunk_index: int, direction: str = "after",
                                   limit: int = 5) -> List[KnowledgeChunk]:
        """
        根据chunk_index获取相邻的知识块
        Args:
            db: 数据库会话
            knowledge_base_id: 知识库ID
            chunk_index: 参考的chunk索引号
            direction: 检索方向 - "before"(前面), "after"(后面), "both"(两者)
            limit: 返回数量限制
        Returns:
            相邻的知识块列表
        """
        if direction == "before":
            # 获取前面的块（chunk_index < 当前index，按倒序）
            db_chunks = db.query(KnowledgeChunkModel).filter(
                KnowledgeChunkModel.knowledge_base_id == knowledge_base_id,
                KnowledgeChunkModel.chunk_index < chunk_index,
                KnowledgeChunkModel.is_deleted == 0
            ).order_by(KnowledgeChunkModel.chunk_index.desc()).limit(limit).all()
            # 返回正序排列
            db_chunks = list(reversed(db_chunks))
        elif direction == "after":
            # 获取后面的块（chunk_index > 当前index，按正序）
            db_chunks = db.query(KnowledgeChunkModel).filter(
                KnowledgeChunkModel.knowledge_base_id == knowledge_base_id,
                KnowledgeChunkModel.chunk_index > chunk_index,
                KnowledgeChunkModel.is_deleted == 0
            ).order_by(KnowledgeChunkModel.chunk_index.asc()).limit(limit).all()
        else:  # "both"
            # 获取前后两部分的块
            before_chunks = db.query(KnowledgeChunkModel).filter(
                KnowledgeChunkModel.knowledge_base_id == knowledge_base_id,
                KnowledgeChunkModel.chunk_index < chunk_index,
                KnowledgeChunkModel.is_deleted == 0
            ).order_by(KnowledgeChunkModel.chunk_index.desc()).limit(limit).all()

            after_chunks = db.query(KnowledgeChunkModel).filter(
                KnowledgeChunkModel.knowledge_base_id == knowledge_base_id,
                KnowledgeChunkModel.chunk_index > chunk_index,
                KnowledgeChunkModel.is_deleted == 0
            ).order_by(KnowledgeChunkModel.chunk_index.asc()).limit(limit).all()
            # 合并：前面的倒序转正序 + 后面的正序
            db_chunks = list(reversed(before_chunks)) + list(after_chunks)

        return [self._to_pydantic(chunk) for chunk in db_chunks]

    def search_similar_chunks(self, db: Session, knowledge_base_id: int,
                             query_embedding: List[float],
                             top_k: int = RAG_TOP_K,
                             threshold: float = RAG_THRESHOLD) -> List[Dict[str, Any]]:
        """
        搜索相似的知识分块
        Args:
            db: 数据库会话
            knowledge_base_id: 知识库ID
            query_embedding: 查询向量
            top_k: 返回数量
            threshold: 相似度阈值
        Returns:
            相似分块列表，包含 content, chunk_id, similarity, metadata
        """
        # 从数据库读取该知识库的所有分块
        # db_chunks = db.query(KnowledgeChunkModel).filter(
        #     KnowledgeChunkModel.is_deleted == 0,
        #     KnowledgeChunkModel.knowledge_base_id == knowledge_base_id
        # ).all()
        # print(len(db_chunks))
        # if not db_chunks:
        #     return []
        #
        # # 计算相似度
        # results = []
        # for db_chunk in db_chunks:
        #     if db_chunk.embedding:
        #         similarity = embedding_service.cosine_similarity(query_embedding, db_chunk.embedding)
        #         if similarity >= threshold:
        #             results.append({
        #                 "chunk_id": db_chunk.id,
        #                 "content": db_chunk.content,
        #                 "similarity": similarity,
        #                 "metadata": db_chunk.chunk_metadata,
        #                 "knowledge_file_id": db_chunk.knowledge_file_id,
        #                 "chunk_index": db_chunk.chunk_index
        #             })

        # 按相似度排序并返回top_k
        # results.sort(key=lambda x: x['similarity'], reverse=True)
        # return results[:top_k]
        distance = KnowledgeChunkModel.embedding.cosine_distance(query_embedding)
        similarity = 1 - distance
        query = db.query(
            KnowledgeChunkModel.id,
            KnowledgeChunkModel.content,
            similarity.label('similarity'),
            KnowledgeChunkModel.chunk_metadata,
            KnowledgeChunkModel.knowledge_file_id,
            KnowledgeChunkModel.chunk_index
        ).filter(
            KnowledgeChunkModel.is_deleted == 0,
            KnowledgeChunkModel.knowledge_base_id == knowledge_base_id,
            similarity >= threshold  # 注意：这可能在 WHERE 中需要重复表达式
        ).all()
        return query

    def search_similar_chunks_by_text(self, db: Session, knowledge_base_id: int,
                                      query_text: str,
                                      top_k: int = RAG_TOP_K,
                                      threshold: float = RAG_THRESHOLD) -> List[Dict[str, Any]]:
        """
        通过文本搜索相似的知识分块
        Args:
            db: 数据库会话
            knowledge_base_id: 知识库ID
            query_text: 查询文本
            top_k: 返回数量
            threshold: 相似度阈值
        Returns:
            相似分块列表
        """
        # 生成查询向量
        try:
            query_embedding = embedding_service.encode_single(query_text)
        except NotImplementedError:
            return []

        return self.search_similar_chunks(
            db=db,
            knowledge_base_id=knowledge_base_id,
            query_embedding=query_embedding,
            top_k=top_k,
            threshold=threshold
        )

    def _to_pydantic(self, db_chunk: KnowledgeChunkModel) -> KnowledgeChunk:
        """ORM模型转Pydantic实体"""
        return KnowledgeChunk(
            id=db_chunk.id,
            knowledge_base_id=db_chunk.knowledge_base_id,
            knowledge_file_id=db_chunk.knowledge_file_id,
            chunk_index=db_chunk.chunk_index,
            content=db_chunk.content,
            embedding=db_chunk.embedding,
            chunk_metadata=db_chunk.chunk_metadata,
            indexing_method=db_chunk.indexing_method,
            created_at=db_chunk.created_at,
            updated_at=db_chunk.updated_at,
            is_deleted=db_chunk.is_deleted
        )


# 全局KnowledgeChunk服务实例
knowledge_chunk_service = KnowledgeChunkService()
