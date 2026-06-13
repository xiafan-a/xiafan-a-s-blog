from pydantic import BaseModel, Field
from typing import Optional, List, Dict, Any
from datetime import datetime


class KnowledgeChunk(BaseModel):
    """知识数据表（向量存储）"""
    id: Optional[int] = Field(None, description="分块唯一ID")
    knowledge_base_id: int = Field(..., description="所属知识库ID")
    knowledge_file_id: Optional[int] = Field(None, description="来源文件ID")
    chunk_index: int = Field(..., description="分块序号")
    content: str = Field(..., description="文本内容")
    embedding: Optional[List[float]] = Field(None, description="向量数据（维度与模型一致）")
    chunk_metadata: Dict[str, Any] = Field(default_factory=dict, description="元数据")
    indexing_method: Optional[str] = Field(None, max_length=50, description="建库方式")
    created_at: Optional[datetime] = Field(None, description="创建时间")
    updated_at: Optional[datetime] = Field(None, description="更新时间")
    is_deleted: int = Field(0, description="逻辑删除标志:0-未删除,1-已删除")

    class Config:
        from_attributes = True
