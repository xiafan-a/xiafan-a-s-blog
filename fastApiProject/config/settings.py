import os
from pathlib import Path

from dotenv import load_dotenv

load_dotenv()
# 文件存储配置
UPLOAD_DIR = Path(os.getenv("UPLOAD_DIR", "./uploads"))
UPLOAD_DIR.mkdir(parents=True, exist_ok=True)

# 分块配置
CHUNK_SIZE = int(os.getenv("CHUNK_SIZE", "5000"))  # 每块字符数
CHUNK_OVERLAP = int(os.getenv("CHUNK_OVERLAP", "600"))  # 重叠字符数

# RAG配置
RAG_TOP_K = int(os.getenv("RAG_TOP_K", "20"))  # 检索返回的top k个结果
RAG_THRESHOLD = float(os.getenv("RAG_THRESHOLD", "0.7"))  # 相似度阈值
MAX_CONTEXT_LENGTH = int(os.getenv("MAX_CONTEXT_LENGTH", "20000"))  # 最大上下文长度

# RAG优化配置
ENABLE_QUERY_OPTIMIZATION = os.getenv("ENABLE_QUERY_OPTIMIZATION", "true").lower() == "true"
ENABLE_SEMANTIC_RERANK = os.getenv("ENABLE_SEMANTIC_RERANK", "true").lower() == "true"
ENABLE_CONTEXT_COMPRESSION = os.getenv("ENABLE_CONTEXT_COMPRESSION", "true").lower() == "true"
ENABLE_RESPONSE_OPTIMIZATION = os.getenv("ENABLE_RESPONSE_OPTIMIZATION", "true").lower() == "true"

# RAG优化参数
RERANK_TOP_K = int(os.getenv("RERANK_TOP_K", "10"))  # 语义排序后返回的数量
COMPRESSION_THRESHOLD = int(os.getenv("COMPRESSION_THRESHOLD", "8000"))  # 触发压缩的上下文长度阈值
QUERY_SELECTION_THRESHOLD = float(os.getenv("QUERY_SELECTION_THRESHOLD", "0.5"))  # 问题选择阈值

# 支持的文件类型
SUPPORTED_FILE_TYPES = {
    ".txt": "text/plain",
    ".md": "text/markdown",
    ".pdf": "application/pdf",
    ".docx": "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
}

# MinIO配置
MINIO_ENDPOINT = os.getenv("MINIO_ENDPOINT", "")
MINIO_ACCESS_KEY = os.getenv("MINIO_ACCESS_KEY", "")
MINIO_SECRET_KEY = os.getenv("MINIO_SECRET_KEY", "")
MINIO_SECURE = os.getenv("MINIO_SECURE", "false").lower() == "true"
MINIO_BUCKET = os.getenv("MINIO_BUCKET", "knowledge-base")

# Agent默认工具配置
DEFAULT_TOOLS = os.getenv("DEFAULT_TOOLS", "web_search,web_browser,get_Date")

# ReAct Agent配置
MAX_REACT_ITERATIONS = int(os.getenv("MAX_REACT_ITERATIONS", "5"))  # 最大推理循环次数
REACT_THINKING_ENABLED = os.getenv("REACT_THINKING_ENABLED", "true").lower() == "true"  # 是否启用思考过程显示

# 浏览器自动化配置
BROWSER_HEADLESS = os.getenv("BROWSER_HEADLESS", "true").lower() == "true"
BROWSER_TIMEOUT = int(os.getenv("BROWSER_TIMEOUT", "30000"))
