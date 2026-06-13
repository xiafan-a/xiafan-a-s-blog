from pydantic import BaseModel, Field
from typing import Optional, Any, Generic, TypeVar

T = TypeVar('T')


class ApiResponse(BaseModel, Generic[T]):
    code: str = Field(default="200", description="状态码")
    data: T = Field(..., description="返回数据")


class ErrorResponse(BaseModel):
    code: str = Field(default="400", description="错误状态码")
    data: str = Field(..., description="错误信息")
