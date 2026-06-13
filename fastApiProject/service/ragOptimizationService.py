"""
RAG优化服务
包含：问题优化、语义排序、上下文压缩、回复优化
"""
import os
import json
import httpx
from typing import List, Dict, Any, Optional

from dotenv import load_dotenv
from openai import OpenAI

# 从环境变量获取配置
load_dotenv()
API_KEY = os.getenv("API_KEY", "")
API_URL = os.getenv("API_URL", "")
MODEL = os.getenv("MODEL", "")
RERAN_MODEL = os.getenv("RERAN_MODEL", "qwen3-vl-rerank")
RESULT_MODEL = os.getenv("RESULT_MODEL", "")
EXTEND_MODEL = os.getenv("EXTEND_MODEL", "qwen3.5-flash")
# Rerank API配置
RERANK_API_URL = os.getenv("RERANK_API_URL", "")


class RAGOptimizationService:
    """RAG优化服务"""

    def __init__(self):
        self.client = None

    def _get_client(self) -> OpenAI:
        """获取OpenAI客户端"""
        if self.client is None:
            self.client = OpenAI(api_key=API_KEY, base_url=API_URL)
        return self.client

    async def optimize_query(self, query: str, history: List[dict] = None) -> dict:
        """
        根据上下文优化用户问题以提升检索效果

        Args:
            query: 用户原始问题
            history: 对话历史消息

        Returns:
            {
                "original_query": str,           # 原始问题
                "expanded_queries": str    # 1个扩写问题
            }
        """
        client = self._get_client()

        # 构建历史消息文本
        history_text = ""
        if history:
            history_text = "\n".join([
                f"{msg.get('role', 'user')}: {msg.get('content', '')}"
                for msg in history[-5:]  # 只取最近5条
            ])

        prompt = f"""你是一个查询优化专家。请将用户的问题从不同角度重新表述，生成5个语义相近但表述不同的问题版本，以便在知识库中检索到更相关的信息。

对话历史：
{history_text if history_text else '（无历史消息）'}

用户当前问题：{query}

请生成扩写版本，要求：
1. 根据上下文信息总结用户问题的核心语义
2. 可以适当补充上下文信息（结合对话历史）

请以JSON格式返回结果，格式如下：
{{
    "expanded_queries": "扩写版本"
}}

注意：只返回JSON，不要有其他内容"""

        try:
            response = client.chat.completions.create(
                model=EXTEND_MODEL,
                messages=[{"role": "user", "content": prompt}],
                temperature=0.1
            )

            result_text = response.choices[0].message.content.strip()

            # 尝试解析JSON
            # 处理可能的markdown代码块
            if "```json" in result_text:
                result_text = result_text.split("```json")[1].split("```")[0]
            elif "```" in result_text:
                result_text = result_text.split("```")[1].split("```")[0]

            result = json.loads(result_text)

            return {
                "original_query": query,
                "expanded_queries": result.get("expanded_queries", [])[:1]  # 最多5个
            }

        except Exception as e:
            return {
                "original_query": query,
                "expanded_queries": []
            }

    async def select_best_query(
            self,
            original_query: str,
            expanded_queries: List[str],
            threshold: float = 0.8
    ) -> str:
        """
        使用 Rerank 选择最佳扩写问题

        Args:
            original_query: 原始问题
            expanded_queries: 扩写的问题列表
            threshold: 相关性阈值，低于此值返回原问题

        Returns:
            最佳问题（如果都低于阈值则返回原问题）
        """
        if not expanded_queries:
            return original_query

        try:
            # 将扩写问题作为 documents 进行 rerank
            documents = [{"text": q} for q in expanded_queries]

            async with httpx.AsyncClient() as client:
                response = await client.post(
                    RERANK_API_URL,
                    headers={
                        "Authorization": f"Bearer {API_KEY}",
                        "Content-Type": "application/json"
                    },
                    json={
                        "model": RERAN_MODEL,
                        "input": {
                            "query": {"text": original_query},
                            "documents": documents
                        },
                        "parameters": {
                            "return_documents": True,
                            "top_n": 1  # 只需要得分最高的一个
                        }
                    },
                    timeout=5.0
                )

                if response.status_code != 200:
                    return original_query

                result = response.json()
                results = result.get("output", {}).get("results", [])

                if not results:
                    return original_query

                # 获取得分最高的结果
                best = results[0]
                relevance_score = best.get("relevance_score", 0)
                best_query = best.get("document", {}).get("text", "")

                # 阈值检查
                if relevance_score >= threshold and best_query:
                    return best_query

                return original_query

        except Exception:
            return original_query

    async def semantic_rerank(self, query: str, contexts: List, top_k: int = 5, threshold: float = 0.6) -> List[dict]:
        """
        对检索结果进行语义重排序（使用阿里云Rerank API）

        Args:
            query: 用户问题
            contexts: 检索到的上下文列表（支持List[dict]或List[str]）
            top_k: 返回的top k个结果
            threshold: 相关性阈值，低于此值的结果将被过滤

        Returns:
            重排序后的上下文列表
        """
        if not contexts:
            return []

        try:
            # 构建文档列表，支持dict和str两种格式
            documents = []
            for ctx in contexts:
                if isinstance(ctx, dict):
                    documents.append({"text": ctx.get('content', '')})
                elif isinstance(ctx, str):
                    documents.append({"text": ctx})
                else:
                    documents.append({"text": str(ctx.content)})
            # 调用阿里云Rerank API
            async with httpx.AsyncClient() as client:
                response = await client.post(
                    RERANK_API_URL,
                    headers={
                        "Authorization": f"Bearer {API_KEY}",
                        "Content-Type": "application/json"
                    },
                    json={
                        "model": RERAN_MODEL,
                        "input": {
                            "query": {"text": query},
                            "documents": documents
                        },
                        "parameters": {
                            "return_documents": True,
                            "top_n": min(top_k, len(contexts))
                        }
                    },
                    timeout=5.0
                )

                if response.status_code != 200:
                    # print(f"Rerank API调用失败: {response.status_code} - {response.text}")
                    return contexts[:top_k] if isinstance(contexts[0], dict) else [{"content": c, "similarity": 0} for c
                                                                                   in contexts[:top_k]]

                result = response.json()
                # print(f"Rerank API返回: {result}")

                # 解析结果
                results = result.get("output", {}).get("results", [])
                ranked_contexts = []

                for item in results:
                    idx = item.get("index", 0)
                    relevance_score = item.get("relevance_score", 0)
                    document_text = item.get("document", {}).get("text", "")

                    # 阈值筛选：只保留相关性分数高于阈值的结果
                    if relevance_score < threshold:
                        # print(f"文档{idx}相关性分数{relevance_score:.4f}低于阈值{threshold}，已过滤")
                        continue

                    if 0 <= idx < len(contexts):
                        original_ctx = contexts[idx]
                        # 构建返回结果
                        if isinstance(original_ctx, dict):
                            ctx = original_ctx.copy()
                            ctx['rerank_score'] = relevance_score
                            ctx['similarity'] = relevance_score
                        else:
                            ctx = {
                                "content": document_text or (
                                    original_ctx if isinstance(original_ctx, str) else str(original_ctx)),
                                "rerank_score": relevance_score,
                                "similarity": relevance_score
                            }
                        ranked_contexts.append(ctx)

                # print(f"Rerank完成，过滤前{len(results)}个，过滤后返回{len(ranked_contexts)}个结果")
                return ranked_contexts

        except httpx.TimeoutException:
            # print("Rerank API超时")
            return contexts[:top_k] if isinstance(contexts[0], dict) else [{"content": c, "similarity": 0} for c in
                                                                           contexts[:top_k]]
        except Exception as e:
            # print(f"语义排序失败: {str(e)}")
            return contexts[:top_k] if contexts and isinstance(contexts[0], dict) else [{"content": c, "similarity": 0}
                                                                                        for c in contexts[
                                                                                                 :top_k]] if contexts else []

    async def compress_context(
            self,
            query: str,
            contexts: List[dict],
            max_length: int = 3000
    ) -> str:
        """
        压缩上下文，保留与问题最相关的信息

        Args:
            query: 用户问题
            contexts: 上下文列表
            max_length: 压缩后的最大长度

        Returns:
            压缩后的上下文文本
        """
        if not contexts:
            return ""

        client = self._get_client()

        # 计算当前上下文总长度
        total_length = sum(len(c.get('content', '')) for c in contexts)

        if total_length <= max_length:
            # 不需要压缩
            return "\n\n".join([
                f"【参考资料{i + 1}】\n{c.get('content', '')}"
                for i, c in enumerate(contexts)
            ])

        # 构建上下文文本
        context_text = "\n\n".join([
            f"【参考资料{i + 1}】\n{c.get('content', '')}"
            for i, c in enumerate(contexts)
        ])

        prompt = f"""你是一个信息提取专家。请从以下参考资料中提取与用户问题最相关的关键信息。

用户问题：{query}

参考资料：
{context_text}

请执行以下操作：
1. 提取与问题直接相关的关键信息
2. 去除无关的描述和冗余内容
3. 保留重要的细节和数据
4. 保持信息的准确性，不要添加原文没有的信息

请输出压缩后的内容，保留资料编号以便追溯。压缩后的内容总长度不要超过{max_length}个字符。"""

        try:
            response = client.chat.completions.create(
                model=EXTEND_MODEL,
                messages=[{"role": "user", "content": prompt}],
                temperature=0.1
            )

            compressed = response.choices[0].message.content.strip()
            return compressed

        except Exception:
            # 返回截断的原始上下文
            result = ""
            for i, c in enumerate(contexts):
                content = c.get('content', '')
                if len(result) + len(content) > max_length:
                    break
                result += f"【参考资料{i + 1}】\n{content}\n\n"
            return result

    async def optimize_response(
            self,
            query: str,
            contexts: List[dict],
            response: str
    ) -> dict:
        """
        优化回复，判断合理性并去除不合理部分

        Args:
            query: 用户问题
            contexts: 使用的上下文
            response: 初步回复

        Returns:
            {
                "is_reasonable": bool,
                "optimized_response": str,
                "issues": List[str],
                "confidence": float  # 置信度 0-1
            }
        """
        client = self._get_client()

        # 构建上下文摘要
        context_summary = "\n".join([
            f"[资料{i + 1}] {c.get('content', '')[:150]}..."
            for i, c in enumerate(contexts[:5])
        ])

        prompt = f"""你是一个回答质量审核专家。请审核以下回答的质量并进行优化。

用户问题：{query}

参考的知识库内容：
{context_summary}

AI的回答：
{response}

请执行以下任务：
1. 判断回答是否合理（是否回答了问题、是否有依据、是否有幻觉）
2. 找出回答中的问题（如：与参考资料不符的内容、过度推断、无关信息等）
3. 优化回答，去除不合理部分

请以JSON格式返回结果：
{{
    "is_reasonable": true/false,
    "issues": ["问题1", "问题2"],
    "optimized_response": "优化后的回答",
    "confidence": 0.85
}}

注意：
- 如果回答整体合理，is_reasonable为true，issues为空列表，optimized_response可以与原回答相同
- confidence表示对回答质量的信心程度，0-1之间
- 只返回JSON，不要有其他内容"""

        try:
            llm_response = client.chat.completions.create(
                model=RESULT_MODEL,
                messages=[{"role": "user", "content": prompt}],
                temperature=0.1
            )

            result_text = llm_response.choices[0].message.content.strip()

            # 处理可能的markdown代码块
            if "```json" in result_text:
                result_text = result_text.split("```json")[1].split("```")[0]
            elif "```" in result_text:
                result_text = result_text.split("```")[1].split("```")[0]

            result = json.loads(result_text)

            return {
                "is_reasonable": result.get("is_reasonable", True),
                "optimized_response": result.get("optimized_response", response),
                "issues": result.get("issues", []),
                "confidence": result.get("confidence", 0.5)
            }

        except Exception:
            return {
                "is_reasonable": True,
                "optimized_response": response,
                "issues": [],
                "confidence": 0.5
            }


# 全局实例
rag_optimization_service = RAGOptimizationService()
