from fastapi import APIRouter, HTTPException, Depends
from typing import List, Optional, Dict, Any
from pydantic import BaseModel
from sqlmodel import Session

from database import get_db
from service.knowledgeChunkService import knowledge_chunk_service
from entity.KnowledgeChunk import KnowledgeChunk
from util.response import ApiResponse

router = APIRouter()


class KnowledgeChunkCreate(BaseModel):
    knowledge_base_id: int
    chunk_index: int
    content: str
    knowledge_file_id: Optional[int] = None
    embedding: Optional[List[float]] = None
    metadata: Optional[Dict[str, Any]] = None
    indexing_method: Optional[str] = None


class SearchRequest(BaseModel):
    knowledge_base_id: int
    query_embedding: List[float]
    top_k: int = 5
    threshold: float = 0.5


@router.post("/chunks", response_model=ApiResponse[KnowledgeChunk])
def create_chunk(
    chunk: KnowledgeChunkCreate
):
    """创建新的知识分块"""
    result = knowledge_chunk_service.create_chunk(
        knowledge_base_id=chunk.knowledge_base_id,
        chunk_index=chunk.chunk_index,
        content=chunk.content,
        knowledge_file_id=chunk.knowledge_file_id,
        embedding=chunk.embedding,
        metadata=chunk.metadata,
        indexing_method=chunk.indexing_method
    )
    return ApiResponse(code="200", data=result)


@router.get("/chunks/{chunk_id}", response_model=ApiResponse[KnowledgeChunk])
def get_chunk(
    chunk_id: int,
db: Session = Depends(get_db)
):
    """根据ID获取知识分块"""
    chunk = knowledge_chunk_service.get_chunk_by_id(db,chunk_id)
    if not chunk:
        raise HTTPException(status_code=404, detail="知识分块不存在")
    return ApiResponse(code="200", data=chunk)


@router.get("/knowledge-bases/{kb_id}/chunks", response_model=ApiResponse[List[KnowledgeChunk]])
def get_chunks_by_knowledge_base(
    kb_id: int,
    skip: int = 0,
    limit: int = 100,
    db: Session = Depends(get_db)
):
    """根据知识库ID获取知识分块列表"""
    result = knowledge_chunk_service.get_chunks_by_knowledge_base(db,kb_id, skip, limit)
    return ApiResponse(code="200", data=result)


@router.get("/files/{file_id}/chunks", response_model=ApiResponse[List[KnowledgeChunk]])
def get_chunks_by_file(
    file_id: int
):
    """根据文件ID获取知识分块列表"""
    result = knowledge_chunk_service.get_chunks_by_file(file_id)
    return ApiResponse(code="200", data=result)


@router.post("/chunks/search", response_model=ApiResponse[List[KnowledgeChunk]])
def search_similar_chunks(
    search_request: SearchRequest
):
    """搜索相似的知识分块"""
    result = knowledge_chunk_service.search_similar_chunks(
        knowledge_base_id=search_request.knowledge_base_id,
        query_embedding=search_request.query_embedding,
        top_k=search_request.top_k,
        threshold=search_request.threshold
    )
    return ApiResponse(code="200", data=result)


@router.delete("/chunks/{chunk_id}", response_model=ApiResponse[dict])
def delete_chunk(
    chunk_id: int
):
    """删除知识分块"""
    success = knowledge_chunk_service.delete_chunk(chunk_id)
    if not success:
        raise HTTPException(status_code=404, detail="知识分块不存在")
    return ApiResponse(code="200", data={"message": "知识分块删除成功"})
