from typing import Any, Iterator, List, Dict

from deepagents.backends import FilesystemBackend
from langchain_openai import ChatOpenAI
from langchain.messages import HumanMessage
import uuid
import os
from deepagents import create_deep_agent
from agent.tools.tool import mcp_client

from agent.chat_mode.chat_openai_template import ChatOpenAITemplate
from ..template_agent import BaseTemplateAgent, TokenChunk
from agent.tools.tool import get_date

DEFAULT_BACKEND = FilesystemBackend(
    root_dir=os.path.dirname(os.path.dirname(os.path.abspath(__file__))),
    virtual_mode=True,
)
def format_chunk(chunk: Any) -> Dict[str, Any]:
    """格式化chunk为统一输出格式"""
    result = {
        "content": getattr(chunk, "content", "") or "",
        "additional_kwargs": getattr(chunk, "additional_kwargs", {}) or {},
        "response_metadata": getattr(chunk, "response_metadata", {}) or {},
        "id": getattr(chunk, "id", "") or f"lc_run--{uuid.uuid4().hex[:24]}",
        "tool_calls": [],
        "invalid_tool_calls": [],
        "tool_call_chunks": [],
    }

    # 检查是否有tool_calls
    if hasattr(chunk, "tool_calls") and chunk.tool_calls:
        result["tool_calls"] = chunk.tool_calls
        result["response_metadata"] = {
            **(result.get("response_metadata") or {}),
            "finish_reason": "tool_calls",
        }
    if hasattr(chunk, "invalid_tool_calls") and chunk.invalid_tool_calls:
        result["invalid_tool_calls"] = chunk.invalid_tool_calls
    if hasattr(chunk, "tool_call_chunks") and chunk.tool_call_chunks:
        result["tool_call_chunks"] = chunk.tool_call_chunks

    return result


# ============== ReAct Agent ==============

class ReActAgent(BaseTemplateAgent):
    """ReAct Agent - 结合推理和工具调用的Agent"""

    def __init__(
            self,
            model: ChatOpenAI,
            tools: List[Any] = None,
            backend: Any = None,
    ):
        super().__init__(model=model, tools=tools or [], backend=backend or DEFAULT_BACKEND)

    def _stream_impl(self, query: str) -> Iterator[TokenChunk]:
        agent = create_deep_agent(
            model=self.model,
            tools=self.tools,
            backend=self.backend,
            skills=["./skills"]
        )
        inputs = {"messages": [HumanMessage(content=query)]}
        try:
            import asyncio
            loop = asyncio.get_event_loop()
        except RuntimeError:
            loop = asyncio.new_event_loop()
            asyncio.set_event_loop(loop)

        try:
            async_gen = agent.astream(inputs, stream_mode="messages")
            while True:
                try:
                    chunk, _ = loop.run_until_complete(async_gen.__anext__())
                    yield format_chunk(chunk)
                except StopAsyncIteration:
                    break
        finally:
            pass


import asyncio


async def init_react_agent():
    tool_ = await mcp_client.get_tools()
    tool_.append(get_date)
    return ReActAgent(
        ChatOpenAITemplate.from_env(),
        tools=tool_
    )


react_agent = asyncio.run(init_react_agent())
