from fastapi import APIRouter, HTTPException, Depends
from typing import List, Optional
from pydantic import BaseModel
from sqlalchemy.orm import Session

from service.knowledgeBaseService import knowledge_base_service
from service.conversationSessionService import conversation_session_service
from entity.KnowledgeBase import KnowledgeBase
from entity.ConversationSession import ConversationSession
from util.response import ApiResponse
from database import get_db

router = APIRouter()


class KnowledgeBaseCreate(BaseModel):
    name: str
    description: Optional[str] = None
    system_prompt: Optional[str] = None


class KnowledgeBaseUpdate(BaseModel):
    name: Optional[str] = None
    description: Optional[str] = None
    system_prompt: Optional[str] = None


@router.post("/create/knowledge-base", response_model=ApiResponse[KnowledgeBase])
def create_knowledge_base(
    kb: KnowledgeBaseCreate
):
    """创建新的知识库"""
    result = knowledge_base_service.create_knowledge_base(
        name=kb.name,
        description=kb.description,
        system_prompt=kb.system_prompt
    )
    return ApiResponse(code="200", data=result)


@router.get("/knowledge-bases/{kb_id}", response_model=ApiResponse[List[ConversationSession]])
def get_knowledge_base(
    kb_id: int,
    skip: int = 0,
    limit: int = 100,
    db: Session = Depends(get_db)
):
    """根据知识库ID获取未删除的会话列表"""
    # 先验证知识库是否存在
    kb = knowledge_base_service.get_knowledge_base_by_id(kb_id)
    if not kb:
        raise HTTPException(status_code=404, detail="知识库不存在")
    
    # 获取该知识库下未删除的会话列表
    sessions = conversation_session_service.get_sessions_by_knowledge_base(db, kb_id, skip, limit)
    return ApiResponse(code="200", data=sessions)


@router.get("/knowledge-bases", response_model=ApiResponse[List[KnowledgeBase]])
def get_knowledge_bases(
    skip: int = 0,
    limit: int = 100
):
    """获取知识库列表"""
    result = knowledge_base_service.get_knowledge_bases(skip, limit)
    return ApiResponse(code="200", data=result)


@router.post("/knowledge-bases/{kb_id}", response_model=ApiResponse[KnowledgeBase])
def update_knowledge_base(
    kb_id: int,
    kb_update: KnowledgeBaseUpdate
):
    """更新知识库信息"""
    updated_kb = knowledge_base_service.update_knowledge_base(
        kb_id=kb_id,
        name=kb_update.name,
        description=kb_update.description,
        system_prompt=kb_update.system_prompt
    )
    if not updated_kb:
        raise HTTPException(status_code=404, detail="知识库不存在")
    return ApiResponse(code="200", data=updated_kb)


@router.delete("/knowledge-bases/{kb_id}", response_model=ApiResponse[dict])
def delete_knowledge_base(
    kb_id: int
):
    """删除知识库"""
    success = knowledge_base_service.soft_delete_knowledge_base(kb_id)
    if not success:
        raise HTTPException(status_code=404, detail="知识库不存在")
    return ApiResponse(code="200", data={"message": "知识库删除成功"})
