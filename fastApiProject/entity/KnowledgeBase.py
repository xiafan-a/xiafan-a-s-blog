from pydantic import BaseModel, Field
from typing import Optional
from datetime import datetime


class KnowledgeBase(BaseModel):
    """知识库信息表"""
    id: Optional[int] = Field(None, description="知识库ID")
    name: str = Field(..., description="知识库名称", max_length=255)
    description: Optional[str] = Field(None, description="知识库描述")
    system_prompt: Optional[str] = Field(None, description="系统提示词")
    created_at: Optional[datetime] = Field(None, description="创建时间")
    updated_at: Optional[datetime] = Field(None, description="更新时间")
    is_deleted: int = Field(0, description="逻辑删除标志:0-未删除,1-已删除")

    class Config:
        from_attributes = True

