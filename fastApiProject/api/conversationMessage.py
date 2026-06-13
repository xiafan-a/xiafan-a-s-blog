from fastapi import APIRouter, HTTPException, Depends
from sqlalchemy.orm import Session
from typing import List, Optional
from pydantic import BaseModel

from service.conversationMessageService import conversation_message_service
from entity.ConversationMessage import ConversationMessage
from util.response import ApiResponse
from database import get_db

router = APIRouter()


class ConversationMessageCreate(BaseModel):
    knowledge_base_id: int
    role: str
    content: str
    session_id: Optional[int] = None
    parent_message_id: Optional[int] = None
    context_window: int = 10
    context_summary: Optional[str] = None
    sources: Optional[list] = None
    token_usage: Optional[dict] = None
    feedback: Optional[int] = None
    metadata: Optional[dict] = None


@router.post("/messages", response_model=ApiResponse[ConversationMessage])
def create_message(
    message: ConversationMessageCreate,
    db: Session = Depends(get_db)
):
    """创建新的对话消息"""
    result = conversation_message_service.create_message(
        db=db,
        knowledge_base_id=message.knowledge_base_id,
        role=message.role,
        content=message.content,
        session_id=message.session_id,
        parent_message_id=message.parent_message_id,
        context_window=message.context_window,
        context_summary=message.context_summary,
        sources=message.sources,
        token_usage=message.token_usage,
        feedback=message.feedback,
        message_metadata=message.metadata
    )
    return ApiResponse(code="200", data=result)


@router.get("/messages/{message_id}", response_model=ApiResponse[ConversationMessage])
def get_message(
    message_id: int,
    db: Session = Depends(get_db)
):
    """根据ID获取对话消息"""
    message = conversation_message_service.get_message_by_id(db, message_id)
    if not message:
        raise HTTPException(status_code=404, detail="消息不存在")
    return ApiResponse(code="200", data=message)


@router.get("/knowledge-bases/{kb_id}/messages", response_model=ApiResponse[List[ConversationMessage]])
def get_messages_by_knowledge_base(
    kb_id: int,
    skip: int = 0,
    limit: int = 100,
    db: Session = Depends(get_db)
):
    """根据知识库ID获取对话消息列表"""
    result = conversation_message_service.get_messages_by_knowledge_base(db, kb_id, skip, limit)
    return ApiResponse(code="200", data=result)


@router.get("/sessions/{session_id}/messages", response_model=ApiResponse[List[ConversationMessage]])
def get_messages_by_session(
    session_id: int,
    skip: int = 0,
    limit: int = 100,
    db: Session = Depends(get_db)
):
    """根据会话ID获取对话消息列表"""
    result = conversation_message_service.get_messages_by_session(db, session_id, skip, limit)
    return ApiResponse(code="200", data=result)


@router.put("/messages/{message_id}/feedback", response_model=ApiResponse[ConversationMessage])
def update_message_feedback(
    message_id: int,
    feedback: int,
    db: Session = Depends(get_db)
):
    """更新消息反馈"""
    updated_message = conversation_message_service.update_message_feedback(db, message_id, feedback)
    if not updated_message:
        raise HTTPException(status_code=404, detail="消息不存在")
    return ApiResponse(code="200", data=updated_message)


@router.delete("/messages/{message_id}", response_model=ApiResponse[dict])
def delete_message(
    message_id: int,
    db: Session = Depends(get_db)
):
    """删除对话消息"""
    success = conversation_message_service.soft_delete_message(db, message_id)
    if not success:
        raise HTTPException(status_code=404, detail="消息不存在")
    return ApiResponse(code="200", data={"message": "消息删除成功"})
