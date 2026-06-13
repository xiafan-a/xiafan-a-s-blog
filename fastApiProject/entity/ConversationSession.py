from pydantic import BaseModel, Field
from typing import Optional
from datetime import datetime


class ConversationSession(BaseModel):
    """对话会话信息表"""
    id: Optional[int] = Field(None, description="会话ID")
    knowledge_base_id: int = Field(..., description="知识库ID")
    title: Optional[str] = Field(None, description="会话标题")
    created_at: Optional[datetime] = Field(None, description="创建时间")
    updated_at: Optional[datetime] = Field(None, description="更新时间")
    is_deleted: int = Field(0, description="逻辑删除标志:0-未删除,1-已删除")

    class Config:
        from_attributes = True
