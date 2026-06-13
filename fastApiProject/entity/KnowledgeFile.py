from pydantic import BaseModel, Field
from typing import Optional, Dict, Any
from datetime import datetime


class KnowledgeFile(BaseModel):
    """知识文件表"""
    id: Optional[int] = Field(None, description="文件ID")
    knowledge_base_id: int = Field(..., description="所属知识库ID")
    file_name: str = Field(..., description="文件名", max_length=512)
    file_size: int = Field(..., description="文件大小（字节）")
    file_type: Optional[str] = Field(None, description="文件类型", max_length=100)
    file_hash: str = Field(..., description="文件哈希值", max_length=64)
    indexing_method: str = Field("semantic", description="索引方法", max_length=50)
    status: str = Field("pending", description="状态", max_length=20)
    error_message: Optional[str] = Field(None, description="错误信息")
    file_metadata: Dict[str, Any] = Field(default_factory=dict, description="元数据")
    created_at: Optional[datetime] = Field(None, description="创建时间")
    updated_at: Optional[datetime] = Field(None, description="更新时间")
    is_deleted: int = Field(0, description="逻辑删除标志:0-未删除,1-已删除")

    class Config:
        from_attributes = True

