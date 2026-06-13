from typing import List, Optional
import os
import httpx

from dotenv import load_dotenv
from service.embeddingService import embedding_service

load_dotenv()

RERANK_API_URL = os.getenv("RERANK_API_URL", "")
API_KEY = os.getenv("API_KEY", "")
RERAN_MODEL = os.getenv("RERANK_MODEL", "")


class RerankService:
    """Rerank模型服务接口（使用阿里云Rerank API）"""

    def __init__(self):
        self.api_key = API_KEY
        self.api_url = RERANK_API_URL
        self.model = RERAN_MODEL

    def calculate_similarity(self, query: str, candidate: str) -> float:
        """
        计算两个文本之间的相似度（使用阿里云Rerank API）
        Args:
            query: 查询文本（目标词）
            candidate: 候选文本（猜测词）
        Returns:
            相似度值 (0-1)
        """
        try:
            # gte-rerank模型使用字符串格式
            documents = [candidate]
            response = httpx.post(
                self.api_url,
                headers={
                    "Authorization": f"Bearer {self.api_key}",
                    "Content-Type": "application/json"
                },
                json={
                    "model": self.model,
                    "input": {
                        "query": query,
                        "documents": documents
                    },
                    "parameters": {
                        "return_documents": True,
                        "top_n": 1
                    }
                },
                timeout=5.0
            )
            print(query, documents, self.model)
            print(response.json())
            if response.status_code != 200:
                return 0.0

            result = response.json()
            results = result.get("output", {}).get("results", [])
            if results:
                return results[0].get("relevance_score", 0.0)
            return 0.0

        except Exception as e:
            print(f"Rerank API调用失败: {e}")
            return 0.0

    def calculate_similarity_by_embedding(self, query: str, candidate: str) -> float:
        """
        计算两个文本之间的相似度（使用Embedding向量）
        Args:
            query: 查询文本（目标词）
            candidate: 候选文本（猜测词）
        Returns:
            相似度值 (0-1)
        """
        try:
            query_vec = embedding_service.encode_single(query)
            candidate_vec = embedding_service.encode_single(candidate)
            return embedding_service.cosine_similarity(query_vec, candidate_vec)
        except Exception as e:
            print(f"Embedding相似度计算失败: {e}")
            return 0.0

    def calculate_similarity_from_embedding(self, target_embedding: List[float], candidate: str) -> float:
        """
        使用已存储的目标词向量计算相似度（减少一次 API 调用）
        Args:
            target_embedding: 已存储的目标词向量
            candidate: 候选文本（猜测词）
        Returns:
            相似度值 (0-1)
        """
        try:
            candidate_vec = embedding_service.encode_single(candidate)
            return embedding_service.cosine_similarity(target_embedding, candidate_vec)
        except Exception as e:
            print(f"Embedding相似度计算失败: {e}")
            return 0.0

    def rerank(self, query: str, candidates: List[str], top_n: int = 5, threshold: float = 0.6) -> List[dict]:
        """
        对候选文本进行重排序（使用阿里云Rerank API）
        Args:
            query: 查询文本
            candidates: 候选文本列表
            top_n: 返回前n个结果
            threshold: 相关性阈值，低于此值的结果将被过滤
        Returns:
            重排序后的结果列表 [{"index": idx, "similarity": score, "text": str}, ...]
        """
        if not candidates:
            return []

        try:
            # gte-rerank模型使用字符串格式
            documents = candidates
            response = httpx.post(
                self.api_url,
                headers={
                    "Authorization": f"Bearer {self.api_key}",
                    "Content-Type": "application/json"
                },
                json={
                    "model": self.model,
                    "input": {
                        "query": query,
                        "documents": documents
                    },
                    "parameters": {
                        "return_documents": True,
                        "top_n": min(top_n, len(candidates))
                    }
                },
                timeout=5.0
            )

            if response.status_code != 200:
                # 降级处理：返回原始顺序
                return [{"index": i, "similarity": 0.0, "text": c} for i, c in enumerate(candidates[:top_n])]

            result = response.json()
            results = result.get("output", {}).get("results", [])
            ranked_results = []

            for item in results:
                idx = item.get("index", 0)
                relevance_score = item.get("relevance_score", 0)

                if relevance_score < threshold:
                    continue

                if 0 <= idx < len(candidates):
                    ranked_results.append({
                        "index": idx,
                        "similarity": relevance_score,
                        "text": candidates[idx]
                    })

            return ranked_results

        except Exception as e:
            print(f"Rerank API调用失败: {e}")
            return [{"index": i, "similarity": 0.0, "text": c} for i, c in enumerate(candidates[:top_n])]

    def batch_calculate_similarity_by_embedding(self, query: str, candidates: List[str]) -> List[float]:
        """
        批量计算查询向量与多个候选向量的相似度（使用Embedding向量）
        Args:
            query: 查询文本（目标词）
            candidates: 候选文本列表
        Returns:
            相似度列表
        """
        try:
            query_vec = embedding_service.encode_single(query)
            candidate_vecs = embedding_service.encode(candidates)
            return embedding_service.batch_cosine_similarity(query_vec, candidate_vecs)
        except Exception as e:
            print(f"Embedding批量相似度计算失败: {e}")
            return [0.0] * len(candidates)


# 全局实例
rerank_service = RerankService()