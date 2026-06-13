from fastapi import APIRouter, HTTPException, Request, Depends
from fastapi.responses import StreamingResponse
from sqlalchemy.orm import Session

import service.chatService as chatService
from service.redisService import redis_service
from database import get_db

router = APIRouter()


@router.post("/chat/stream")
async def stream_chat(
    chat_request: chatService.ChatRequest,
    request: Request
):
    """流式聊天API（原有功能）"""
    if not chat_request.message:
        raise HTTPException(status_code=400, detail="消息内容不能为空")
    identification = request.headers.get(chatService.HEAD_KEY)
    if not identification or not redis_service.check_set_member(chatService.REDIS_SET_NAME, identification):
        raise HTTPException(status_code=403, detail="权限不足")

    async def generate():
        async for chunk in chatService.real_stream_response(
            messages=chat_request.conversation_history,
            model=chatService.MODEL,
            temperature=chatService.ZAI_TEMPERATURE
        ):
            yield chunk

    return StreamingResponse(
        generate(),
        media_type="text/event-stream",
        headers={
            "Cache-Control": "no-cache",
            "Connection": "keep-alive",
            "X-Accel-Buffering": "no"
        }
    )


@router.post("/chat/rag/stream")
async def rag_stream_chat(
    chat_request: chatService.RAGChatRequest,
    request: Request,
    db: Session = Depends(get_db)
):
    """RAG流式聊天API"""
    if not chat_request.message:
        raise HTTPException(status_code=400, detail="消息内容不能为空")

    identification = request.headers.get(chatService.HEAD_KEY)
    if not identification or not redis_service.check_set_member(chatService.REDIS_SET_NAME, identification):
        raise HTTPException(status_code=403, detail="权限不足")

    async def generate():
        async for chunk in chatService.rag_stream_response(
            db=db,
            query=chat_request.message,
            knowledge_base_id=chat_request.knowledge_base_id,
            messages=chat_request.conversation_history or "[]",
            model=chat_request.model or chatService.MODEL,
            temperature=chat_request.temperature or chatService.ZAI_TEMPERATURE,
            session_id=chat_request.session_id
        ):
            yield chunk

    return StreamingResponse(
        generate(),
        media_type="text/event-stream",
        headers={
            "Cache-Control": "no-cache",
            "Connection": "keep-alive",
            "X-Accel-Buffering": "no"
        }
    )
