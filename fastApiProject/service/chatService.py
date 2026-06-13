import asyncio
from fastapi import APIRouter, HTTPException, Depends
from fastapi.responses import StreamingResponse
import os
import json
from typing import Optional, AsyncGenerator, List
from pydantic import BaseModel
from openai import OpenAI
import ast
import time

from sqlmodel import Session

from database import get_db
from service.redisService import redis_service
from service.knowledgeChunkService import knowledge_chunk_service
from service.conversationMessageService import conversation_message_service
from service.knowledgeBaseService import knowledge_base_service
from service.embeddingService import embedding_service
from service.ragOptimizationService import rag_optimization_service
from config.settings import (
    RAG_TOP_K, RAG_THRESHOLD, MAX_CONTEXT_LENGTH,
    ENABLE_QUERY_OPTIMIZATION, ENABLE_SEMANTIC_RERANK,
    ENABLE_CONTEXT_COMPRESSION, ENABLE_RESPONSE_OPTIMIZATION,
    RERANK_TOP_K, COMPRESSION_THRESHOLD, QUERY_SELECTION_THRESHOLD
)

router = APIRouter()

# 从环境变量获取配置
API_KEY = os.getenv("API_KEY", "")
API_URL = os.getenv("API_URL", "")
MODEL = os.getenv("MODEL", "")
PRE_MODEL = os.getenv("PRE_MODEL", "")
ZAI_TEMPERATURE = float(os.getenv("TEMPERATURE", "0"))
USE_MOCK = os.getenv("USE_MOCK", "true").lower() == "true"

REDIS_SET_NAME = "identificationSet"
HEAD_KEY = "identification"


class ChatRequest(BaseModel):
    message: str
    conversation_history: Optional[str] = None
    model: Optional[str] = MODEL
    temperature: Optional[float] = ZAI_TEMPERATURE


class RAGChatRequest(BaseModel):
    """RAG聊天请求"""
    message: str
    knowledge_base_id: int
    session_id: Optional[int] = None
    conversation_history: Optional[str] = None  # 历史消息JSON字符串
    model: Optional[str] = MODEL
    temperature: Optional[float] = ZAI_TEMPERATURE


class ChatResponse(BaseModel):
    id: str
    message: str
    role: str
    finish_reason: Optional[str] = None


class StreamResponse(BaseModel):
    id: str
    message: str
    role: str
    finish_reason: Optional[str] = None
    done: bool


def build_rag_prompt(query: str, contexts: List[dict],
                     system_prompt: str = None) -> str:
    """构建RAG提示词"""
    base_prompt = system_prompt or "你是一个有帮助的助手，请根据提供的知识回答用户问题。"

    if contexts:
        # 截断过长的上下文
        context_text = ""
        total_length = 0
        for i, c in enumerate(contexts):
            content = f"【参考资料{i+1}】(相似度: {c['similarity']:.2f})\n{c['content']}\n\n"
            if total_length + len(content) > MAX_CONTEXT_LENGTH:
                break
            context_text += content
            total_length += len(content)

        prompt = f"""{base_prompt}

以下是与用户问题相关的参考资料：

{context_text}
请根据以上参考资料回答用户问题。如果参考资料中没有相关信息，请明确告知用户。

用户问题：{query}
"""
    else:
        prompt = f"""{base_prompt}

用户问题：{query}

提示：知识库中未找到与用户问题相关的资料。
"""
    return prompt


async def real_stream_response(messages: str, model: str, temperature: float) -> AsyncGenerator[str, None]:
    """真实流式响应（原有功能）"""
    if not API_KEY:
        yield f"data: {json.dumps({'id': 'error', 'message': '请设置有效的API_KEY环境变量', 'role': 'assistant', 'done': True})}\n\n"
        return

    try:
        client = OpenAI(
            api_key=API_KEY,
            base_url=API_URL,
        )
        prompt = "You are a helpful assistant."
        messages_list = ast.literal_eval(messages)
        messages_list.insert(0, {"role": "system", "content": prompt})
        stream = client.chat.completions.create(
            model=model,
            messages=messages_list,
            temperature=temperature,
            stream=True
        )

        for chunk in stream:
            if chunk.choices:
                choice = chunk.choices[0]
                delta = choice.delta
                finish_reason = choice.finish_reason
                if delta.content:
                    data = {
                        'id': chunk.id,
                        'message': delta.content,
                        'role': delta.role or 'assistant',
                        'finish_reason': None,
                        'done': False
                    }
                    yield f"data: {json.dumps(data)}\n\n"
                    await asyncio.sleep(0.02)

                if finish_reason:
                    data = {
                        'id': chunk.id,
                        'message': '',
                        'role': 'assistant',
                        'finish_reason': finish_reason,
                        'done': True
                    }
                    yield f"data: {json.dumps(data)}\n\n"
                    break

    except Exception as e:
        yield f"data: {json.dumps({'id': 'error', 'message': f'错误: {str(e)}', 'role': 'assistant', 'done': True})}\n\n"


async def rag_stream_response(
    query: str,
    knowledge_base_id: int,
    messages: str,
    model: str,
    temperature: float,
    session_id: int = None,
    db: Session = Depends(get_db)
) -> AsyncGenerator[str, None]:
    """RAG流式响应（集成优化流程）"""
    from database import SessionLocal

    # 解析历史消息
    history = []
    if messages:
        try:
            history = ast.literal_eval(messages)
        except:
            history = []

    # ========== 步骤1: 问题优化 + 选择最佳问题 ==========
    async def search_single(q: str):
        """单个问题的检索任务"""
        try:
            query_embedding = embedding_service.encode_single(q)
            return knowledge_chunk_service.search_similar_chunks(
                db=db,
                knowledge_base_id=knowledge_base_id,
                query_embedding=query_embedding,
                top_k=RAG_TOP_K,
                threshold=RAG_THRESHOLD
            )
        except Exception:
            return []

    # 选择最终用于检索的问题
    final_query = query
    star = time.perf_counter()
    if ENABLE_QUERY_OPTIMIZATION:
        try:
            # 1. 根据上下文优化问题
            optimized = await rag_optimization_service.optimize_query(query, history)
            expanded_queries = optimized.get('expanded_queries', [])
            if expanded_queries:
                final_query = expanded_queries[0]
        except Exception:
            pass
    print(final_query)
    end = time.perf_counter()
    print(f"问题优化耗时: {end - star:.4f}秒")
    star = end
    # ========== 步骤2: 使用最佳问题进行检索 ==========
    star = time.perf_counter()
    all_contexts = await search_single(final_query)
    end = time.perf_counter()
    print(f"检索耗时: {end - star:.4f}秒")
    star = end

    # ========== 步骤3: 语义排序 ==========
    if ENABLE_SEMANTIC_RERANK and len(all_contexts) > 0:
        try:
            all_contexts = await rag_optimization_service.semantic_rerank(
                final_query, all_contexts, top_k=RERANK_TOP_K
            )
        except Exception as e:
            all_contexts = all_contexts
    end = time.perf_counter()
    print(f"语义排序耗时: {end - star:.4f}秒")
    star = end
    context_text = ""
    # ========== 步骤4: 上下文压缩 ==========
    total_context_length = sum(len(c['content']) for c in all_contexts)

    if ENABLE_CONTEXT_COMPRESSION and total_context_length > COMPRESSION_THRESHOLD:
        try:
            context_text = await rag_optimization_service.compress_context(
                query, all_contexts, max_length=MAX_CONTEXT_LENGTH
            )
        except Exception as e:
            context_text = build_context_text(all_contexts, MAX_CONTEXT_LENGTH)
    else:
        context_text = build_context_text(all_contexts, MAX_CONTEXT_LENGTH)
    end = time.perf_counter()
    print(f"上下文压缩耗时: {end - star:.4f}秒")
    star = end
    # ========== 步骤5: 获取知识库系统提示词 ==========
    kb = knowledge_base_service.get_knowledge_base_by_id(knowledge_base_id)
    system_prompt = kb.system_prompt if kb else None
    end = time.perf_counter()
    print(f"获取知识库系统提示词耗时: {end - star:.4f}秒")
    # ========== 步骤6: 构建RAG提示词并生成回复 ==========
    rag_prompt = build_rag_prompt_from_context(query, context_text, system_prompt)

    try:
        client = OpenAI(api_key=API_KEY, base_url=API_URL)

        # 构建消息历史
        msg_list = history.copy() if history else []
        msg_list.insert(0, {"role": "system", "content": rag_prompt})

        # 根据是否开启回复优化选择不同的输出方式
        if ENABLE_RESPONSE_OPTIMIZATION:
            # 开启优化：需要先获取完整回复，优化后再流式输出
            star = time.perf_counter()
            response = client.chat.completions.create(
                model=PRE_MODEL,
                messages=msg_list,
                temperature=temperature,
                stream=False
            )
            end = time.perf_counter()
            print(f"生成初步回复耗时: {end - star:.4f}秒")
            star = end

            initial_response = response.choices[0].message.content

            # 回复优化
            final_response = initial_response
            try:
                optimization_result = await rag_optimization_service.optimize_response(
                    query, all_contexts, initial_response
                )
                final_response = optimization_result['optimized_response']
            except Exception as e:
                pass
            end = time.perf_counter()
            print(f"回复优化耗时: {end - star:.4f}秒")

            # 流式输出优化后的回复
            response_id = "rag_response"
            for char in final_response:
                data = {
                    'id': response_id,
                    'message': char,
                    'role': 'assistant',
                    'finish_reason': None,
                    'done': False
                }
                yield f"data: {json.dumps(data)}\n\n"
                await asyncio.sleep(0.02)

            # 保存对话消息
            if session_id:
                conversation_message_service.create_message(
                    db=db,
                    knowledge_base_id=knowledge_base_id,
                    role="user",
                    content=query,
                    session_id=session_id
                )
                conversation_message_service.create_message(
                    db=db,
                    knowledge_base_id=knowledge_base_id,
                    role="assistant",
                    content=final_response,
                    session_id=session_id,
                    message_metadata={
                        "sources": [
                            {"chunk_id": c['chunk_id'], "similarity": c['similarity']}
                            for c in all_contexts
                        ]
                    }
                )

            # 发送结束标志
            data = {
                'id': response_id,
                'message': '',
                'role': 'assistant',
                'finish_reason': 'stop',
                'done': True
            }
            yield f"data: {json.dumps(data)}\n\n"
        else:
            # 不开启优化：直接流式输出
            star = time.perf_counter()
            stream = client.chat.completions.create(
                model=model,
                messages=msg_list,
                temperature=temperature,
                stream=True
            )

            final_response = ""
            for chunk in stream:
                if chunk.choices:
                    choice = chunk.choices[0]
                    delta = choice.delta
                    finish_reason = choice.finish_reason

                    if delta.content:
                        final_response += delta.content
                        data = {
                            'id': chunk.id,
                            'message': delta.content,
                            'role': delta.role or 'assistant',
                            'finish_reason': None,
                            'done': False
                        }
                        yield f"data: {json.dumps(data)}\n\n"
                        await asyncio.sleep(0.02)

                    if finish_reason:
                        end = time.perf_counter()
                        print(f"流式生成回复耗时: {end - star:.4f}秒")

                        # 保存对话消息
                        if session_id:
                            conversation_message_service.create_message(
                                db=db,
                                knowledge_base_id=knowledge_base_id,
                                role="user",
                                content=query,
                                session_id=session_id
                            )
                            conversation_message_service.create_message(
                                db=db,
                                knowledge_base_id=knowledge_base_id,
                                role="assistant",
                                content=final_response,
                                session_id=session_id,
                                message_metadata={
                                    "sources": [
                                        {"chunk_id": c['chunk_id'], "similarity": c['similarity']}
                                        for c in all_contexts
                                    ]
                                }
                            )

                        data = {
                            'id': chunk.id,
                            'message': '',
                            'role': 'assistant',
                            'finish_reason': finish_reason,
                            'done': True
                        }
                        yield f"data: {json.dumps(data)}\n\n"
                        break

    except Exception as e:
        yield f"data: {json.dumps({'id': 'error', 'message': f'错误: {str(e)}', 'role': 'assistant', 'done': True})}\n\n"


def build_context_text(contexts: List[dict], max_length: int) -> str:
    """构建上下文文本"""
    context_text = ""
    total_length = 0
    for i, c in enumerate(contexts):
        content = f"【参考资料{i+1}】(相似度: {c['similarity']:.2f})\n{c['content']}\n\n"
        if total_length + len(content) > max_length:
            break
        context_text += content
        total_length += len(content)
    return context_text


def build_rag_prompt_from_context(query: str, context_text: str, system_prompt: str = None) -> str:
    """从上下文文本构建RAG提示词"""
    base_prompt = system_prompt or """
    你是一个智能助手，请根据提供的知识回答用户问题。
    任务：基于知识库中的信息进行总结并回答用户的问题。
    要求与限制：
    - 不要编造内容，尤其是数字。
    - 如果知识库中的信息与用户问题无关，只需回复：抱歉，没有提供相关信息。
    - 使用 Markdown 格式的文本回答。
    - 使用用户提问的语言回答。
    - 不要编造内容，尤其是数字。
    """

    if context_text:
        prompt = f"""{base_prompt}

以下是与用户问题相关的参考资料：

{context_text}
请根据以上参考资料回答用户问题。如果参考资料中没有相关信息，请明确告知用户。
不要在问题的回复中提及所参考的资料信息，只对问题进行回复。
当对用户所提的问题回答没有充足把握时，回复：“相关信息不足无法回复”。

用户问题：{query}
"""
    else:
        prompt = f"""{base_prompt}

用户问题：{query}

提示：知识库中未找到与用户问题相关的资料。
当对用户所提的问题回答没有充足把握时，回复：“相关信息不足无法回复”。
"""
    return prompt
