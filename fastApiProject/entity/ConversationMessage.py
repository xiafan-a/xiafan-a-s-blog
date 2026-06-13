from pydantic import BaseModel, Field
from typing import Optional, List, Dict, Any
from datetime import datetime


class ConversationMessage(BaseModel):
    """对话消息表"""
    id: Optional[int] = Field(None, description="消息ID")
    knowledge_base_id: int = Field(..., description="所属知识库ID")
    role: str = Field(..., description="角色", max_length=20)
    content: str = Field(..., description="消息内容")
    session_id: Optional[int] = Field(None, description="会话ID")
    parent_message_id: Optional[int] = Field(None, description="父消息ID")
    context_window: int = Field(10, description="上下文窗口大小")
    context_summary: Optional[str] = Field(None, description="上下文摘要")
    sources: List[Any] = Field(default_factory=list, description="引用来源")
    token_usage: Dict[str, Any] = Field(default_factory=dict, description="token使用情况")
    feedback: Optional[int] = Field(None, description="反馈评分")
    message_metadata: Dict[str, Any] = Field(default_factory=dict, description="元数据")
    created_at: Optional[datetime] = Field(None, description="创建时间")
    updated_at: Optional[datetime] = Field(None, description="更新时间")
    is_deleted: int = Field(0, description="逻辑删除标志:0-未删除,1-已删除")

    class Config:
        from_attributes = True
