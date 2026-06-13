from fastapi import APIRouter, HTTPException, Depends
from typing import List, Optional
from pydantic import BaseModel
from sqlalchemy.orm import Session

from service.conversationSessionService import conversation_session_service
from entity.ConversationSession import ConversationSession
from util.response import ApiResponse
from database import get_db

router = APIRouter()


class ConversationSessionCreate(BaseModel):
    knowledge_base_id: int
    title: Optional[str] = None


class ConversationSessionUpdate(BaseModel):
    title: Optional[str] = None


@router.post("/sessions", response_model=ApiResponse[ConversationSession])
def create_session(
        session: ConversationSessionCreate,
        db: Session = Depends(get_db)
):
    """创建新的对话会话"""
    result = conversation_session_service.create_session(
        db=db,
        knowledge_base_id=session.knowledge_base_id,
        title=session.title
    )
    return ApiResponse(code="200", data=result)


@router.get("/sessions/{session_id}", response_model=ApiResponse[ConversationSession])
def get_session(
        session_id: int,
        db: Session = Depends(get_db)
):
    """根据ID获取对话会话"""
    session = conversation_session_service.get_session_by_id(db, session_id)
    if not session:
        raise HTTPException(status_code=404, detail="会话不存在")
    return ApiResponse(code="200", data=session)


@router.get("/knowledge-bases/{kb_id}/sessions", response_model=ApiResponse[List[ConversationSession]])
def get_sessions_by_knowledge_base(
        kb_id: int,
        skip: int = 0,
        limit: int = 100,
        db: Session = Depends(get_db)
):
    """根据知识库ID获取对话会话列表"""
    result = conversation_session_service.get_sessions_by_knowledge_base(db, kb_id, skip, limit)
    return ApiResponse(code="200", data=result)


@router.post("/sessions/{session_id}", response_model=ApiResponse[ConversationSession])
def update_session(
        session_id: int,
        session_update: ConversationSessionUpdate,
        db: Session = Depends(get_db)
):
    """更新对话会话信息"""
    updated_session = conversation_session_service.update_session(
        db=db,
        session_id=session_id,
        title=session_update.title
    )
    if not updated_session:
        raise HTTPException(status_code=404, detail="会话不存在")
    return ApiResponse(code="200", data=updated_session)


@router.delete("/sessions/{session_id}", response_model=ApiResponse[dict])
def delete_session(
        session_id: int,
        db: Session = Depends(get_db)
):
    """删除对话会话"""
    success = conversation_session_service.soft_delete_session(db, session_id)
    if not success:
        raise HTTPException(status_code=404, detail="会话不存在")
    return ApiResponse(code="200", data={"message": "会话删除成功"})


@router.get("/sessions/{kb_id}", response_model=ApiResponse[List[ConversationSession]])
def get_sessions_by_knowledge_base(
        session_name: str,
        db: Session = Depends(get_db)
):
    """根据会话名称获取对话会话列表"""
    result = conversation_session_service.get_session_by_name(db, session_name);
    return ApiResponse(code="200", data=result)
