from pydantic import BaseModel, Field
from typing import Optional, List
from datetime import datetime


class GuessWord(BaseModel):
    """猜字游戏目标字实体"""
    id: Optional[int] = Field(None, description="目标字唯一ID")
    word: str = Field(..., description="要猜的字/词")
    hint: Optional[str] = Field(None, description="提示信息")
    difficulty: int = Field(1, description="难度等级:1-简单,2-中等,3-困难")
    is_passed: bool = Field(False, description="是否已通过")
    pass_count: int = Field(0, description="通过次数")
    created_at: Optional[datetime] = Field(None, description="创建时间")
    embedding: Optional[List[float]] = Field(None, description="词语向量数据")

    class Config:
        from_attributes = True