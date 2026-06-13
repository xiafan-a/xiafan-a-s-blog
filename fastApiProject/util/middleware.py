from fastapi import Request
from fastapi.responses import JSONResponse
from starlette.middleware.base import BaseHTTPMiddleware
from starlette.responses import StreamingResponse
import json

from util.response import ApiResponse


class ResponseMiddleware(BaseHTTPMiddleware):
    async def dispatch(self, request: Request, call_next):
        try:
            response = await call_next(request)
            
            # 检查是否为流式响应或其他特殊响应
            if isinstance(response, StreamingResponse) or response.media_type == "text/event-stream":
                return response
            
            # 读取响应内容
            body = b""
            async for chunk in response.body_iterator:
                body += chunk
            
            # 尝试解析响应内容
            try:
                data = json.loads(body)
            except json.JSONDecodeError:
                data = body.decode()
            
            # 构建统一响应格式
            unified_response = ApiResponse(
                code="200",
                data=data
            )
            
            # 保留原始响应的头信息，但移除Content-Length
            headers = {}
            for name, value in response.headers.items():
                if name.lower() != "content-length":
                    headers[name] = value
            
            # 确保设置正确的Content-Type
            headers["Content-Type"] = "application/json"
            
            # 返回新的JSON响应
            return JSONResponse(
                content=unified_response.model_dump(),
                status_code=response.status_code,
                headers=headers
            )
        except Exception as e:
            # 处理异常
            return JSONResponse(
                content={"code": "500", "data": str(e)},
                status_code=500
            )
