from pydantic import BaseModel, Field
from typing import Optional
from datetime import datetime


class GuessRecord(BaseModel):
    """猜字游戏猜测记录实体"""
    id: Optional[int] = Field(None, description="记录唯一ID")
    guess_word_id: int = Field(..., description="关联的目标字ID")
    guess: str = Field(..., description="猜测的字/词")
    similarity: float = Field(..., description="相似度:0-1")
    is_correct: bool = Field(False, description="是否猜中")
    created_at: Optional[datetime] = Field(None, description="猜测时间")

    class Config:
        from_attributes = True