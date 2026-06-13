from langchain.tools import tool
from datetime import datetime
from langchain_mcp_adapters.client import MultiServerMCPClient
from service.minioService import minio_service


@tool
def get_date() -> str:
    """
    获取当前日期时间

    Returns:
        当前日期时间字符串，格式为 "YYYY-MM-DD HH:MM:SS"
    """
    return datetime.now().strftime("%Y-%m-%d %H:%M:%S")


@tool
def upload(file_data: bytes, file_name: str, file_type: str) -> str:
    """
    上传文件到 MinIO 对象存储服务。

    Args:
        file_data (bytes):
            文件的二进制数据内容。

        file_name (str):
            文件名称（包含后缀名），例如：
            "test.pdf"、"image.png"。

        file_type (str):
            文件类型/MIME 类型，例如：
            "application/pdf"、
            "image/png"、
            "text/plain"。

    Returns:
        str:
            上传成功后的文件访问路径或文件唯一标识（URL/object_name）。
    """
    return minio_service.upload_file_with_original_byte(file_data=file_data, file_name=file_name, file_type=file_type)[1]


mcp_client = MultiServerMCPClient({
    # "12306-mcp": {
    #     "command": "npx",
    #     "args": ["-y", "12306-mcp"],
    #     "transport": "stdio"
    # }
    # ,
    "bing-search": {
        "command": "cmd",
        "args": ["/c", "npx", "-y", "bing-cn-mcp"],
        "transport": "stdio"
    }
    # ,
    # "leetcode": {
    #     "transport": "stdio",
    #     "command": "cmd",
    #     "args": ["/c", "npx", "-y", "@jinzcdev/leetcode-mcp-server"],
    #     "env": {
    #         "LEETCODE_SITE": "cn",
    #         "LEETCODE_SESSION": ""
    #     }
    # }

})
