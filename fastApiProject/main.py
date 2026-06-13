from fastapi import FastAPI, UploadFile, File
from fastapi.middleware.cors import CORSMiddleware
import uvicorn
import os
from dotenv import load_dotenv
from minio import S3Error
from pydantic import BaseModel
from service.minioService import minio_service

# 加载环境变量
load_dotenv()

# 创建FastAPI应用
app = FastAPI(
    title="本地知识库API",
    description="支持文档上传、检索和轻量级Agent（文件读写、网页搜索）的后端服务",
    version="1.0.0"
)


@app.on_event("startup")
async def startup_event():
    """Initialize services on startup"""
    # Import and register built-in tools
    from service.builtInTools import register_builtin_tools
    register_builtin_tools()
    print("✓ Built-in tools registered")

# 添加CORS中间件
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],  # 生产环境应限制来源
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)


# 健康检查端点
@app.get("/")
async def root():
    from util.response import ApiResponse
    return ApiResponse(code="200", data={"message": "本地知识库后端服务运行中"})


class RerankRequest(BaseModel):
    query: str
    contexts: list
    result_model: str = None


@app.post("/rerank")
async def rerank(file_data: UploadFile = File(...)):
    from util.response import ApiResponse
    """接收文件上传并存储到MinIO"""
    try:
        # 将文件流式上传到MinIO
        # 注意：这里直接使用 file.file，它实现了Python的文件接口
        result = await minio_service.upload_file_with_original_format(
            file_data
        )
        return {"message": "上传成功", "object_name": result}
    except S3Error as e:
        raise ApiResponse(status_code=500, detail=f"MinIO错误: {e}")
    return ApiResponse(code="200", data={"message": result})


@app.get("/health")
async def health():
    from util.response import ApiResponse
    return ApiResponse(code="200", data={"status": "healthy"})


# 导入路由
from api import chat, knowledgeBase, knowledgeFile, conversationMessage, conversationSession, knowledgeChunk
from api import agent, guessWord

# 注册路由
app.include_router(chat.router, prefix="/api/v1", tags=["聊天"])
app.include_router(knowledgeBase.router, prefix="/api/v1", tags=["知识库管理"])
app.include_router(knowledgeFile.router, prefix="/api/v1", tags=["知识文件管理"])
app.include_router(conversationMessage.router, prefix="/api/v1", tags=["对话消息管理"])
app.include_router(conversationSession.router, prefix="/api/v1", tags=["对话会话管理"])
app.include_router(knowledgeChunk.router, prefix="/api/v1", tags=["知识分块管理"])
app.include_router(guessWord.router, prefix="/api/v1", tags=["猜字游戏"])

# Agent routes
app.include_router(agent.router, prefix="/api/v1", tags=["Agent智能体"])

if __name__ == "__main__":
    host = os.getenv("HOST", "0.0.0.0")
    port = int(os.getenv("PORT", 8000))
    uvicorn.run(app, host=host, port=port)
