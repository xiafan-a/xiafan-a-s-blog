from typing import List, Optional
import numpy as np
import os

from openai import OpenAI

from dotenv import load_dotenv

load_dotenv()


class EmbeddingService:
    """嵌入模型服务接口"""

    def __init__(self, model_name: Optional[str] = None):
        """
        初始化嵌入服务
        Args:
            model_name: 模型名称，待定
        """
        self.model_name = model_name or os.getenv("EMBEDDING_MODEL", "")
        self.model = None
        self.dimension = int(os.getenv("EMBEDDING_DIMENSION", 768))  # 默认维度，根据实际模型调整
        self.api_key = os.getenv("API_KEY", "")
        self.api_url = os.getenv("API_URL", "")

    def _load_model(self):
        """延迟加载模型"""

        self.model = OpenAI(
            # 若没有配置环境变量，请用阿里云百炼API Key将下行替换为：api_key="sk-xxx",
            # 各地域的API Key不同。获取API Key：https://help.aliyun.com/zh/model-studio/get-api-key
            api_key=self.api_key,
            # 以下是北京地域base-url，如果使用新加坡地域的模型，需要将base_url替换为：https://dashscope-intl.aliyuncs.com/compatible-mode/v1
            base_url=self.api_url
        )
        if self.model is None:
            raise NotImplementedError("嵌入模型尚未配置，请先选择并配置嵌入模型")

    def encode(self, texts: List[str]) -> List[List[float]]:
        """
        将文本列表转换为向量
        Args:
            texts: 文本列表
        Returns:
            向量列表
        """
        self._load_model()
        completion = self.model.embeddings.create(
            model=self.model_name,
            input=texts,
            dimensions=self.dimension
        )
        return [item.embedding for item in completion.data]

    def encode_single(self, text: str) -> List[float]:
        """
        将单个文本转换为向量
        Args:
            text: 文本内容
        Returns:
            向量
        """
        return self.encode([text])[0]

    @staticmethod
    def cosine_similarity(vec1: List[float], vec2: List[float]) -> float:
        """
        计算两个向量的余弦相似度
        Args:
            vec1: 向量1
            vec2: 向量2
        Returns:
            相似度值 (0-1)
        """
        arr1 = np.array(vec1)
        arr2 = np.array(vec2)

        dot_product = np.dot(arr1, arr2)
        norm1 = np.linalg.norm(arr1)
        norm2 = np.linalg.norm(arr2)

        if norm1 == 0 or norm2 == 0:
            return 0.0

        return float(dot_product / (norm1 * norm2))

    @staticmethod
    def batch_cosine_similarity(query_vec: List[float], vectors: List[List[float]]) -> List[float]:
        """
        批量计算查询向量与多个向量的余弦相似度
        Args:
            query_vec: 查询向量
            vectors: 向量列表
        Returns:
            相似度列表
        """
        query_arr = np.array(query_vec)
        vectors_arr = np.array(vectors)

        # 归一化
        query_norm = query_arr / (np.linalg.norm(query_arr) + 1e-8)
        vectors_norm = vectors_arr / (np.linalg.norm(vectors_arr, axis=1, keepdims=True) + 1e-8)

        # 批量点积
        similarities = np.dot(vectors_norm, query_norm)

        return similarities.tolist()


# 全局实例
embedding_service = EmbeddingService()
