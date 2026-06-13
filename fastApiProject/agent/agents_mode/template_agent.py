from abc import ABC, abstractmethod
from typing import Any, List, Dict, Optional, Iterator, Tuple
from langchain_openai import ChatOpenAI
import os
from dotenv import load_dotenv

load_dotenv()

# Token流元素类型: 完整的chunk字典结构
# 包含: content, additional_kwargs, response_metadata, id, tool_calls, invalid_tool_calls, tool_call_chunks
TokenChunk = Dict[str, Any]


class BaseTemplateAgent(ABC):
    """可复用的模板Agent基类，支持流式输出和对话历史管理"""

    def __init__(
        self,
        model: ChatOpenAI,
        system_prompt: str = "",
        tools: List[Any] = None,
        backend: Any = None,
    ):
        self.model = model
        self.system_prompt = system_prompt
        self.tools = tools or []
        self.backend = backend
        self.messages: List[Dict[str, Any]] = []
        self._last_response: str = ""
        self._token_stream: Optional[Iterator[TokenChunk]] = None

    def run(self, query: str) -> str:
        """执行agent，返回完整响应"""
        self.messages.append({"role": "user", "content": query})
        self._last_response = ""
        tokens: List[TokenChunk] = []
        for chunk in self._stream_impl(query):
            tokens.append(chunk)
            self._token_stream = iter(tokens)
        self._last_response = "".join([t.get("content", "") or "" for t in tokens])
        self.messages.append({"role": "assistant", "content": self._last_response})
        return self._last_response

    def stream(self) -> Iterator[TokenChunk]:
        """获取token流（需先调用run），返回完整chunk字典"""
        if self._token_stream is None:
            raise RuntimeError("请先调用 run() 方法")
        return self._token_stream

    def get_messages(self) -> List[Dict[str, Any]]:
        """获取所有对话历史"""
        return self.messages.copy()

    def clear_messages(self):
        """清空对话历史"""
        self.messages = []

    @abstractmethod
    def _stream_impl(self, query: str) -> Iterator[TokenChunk]:
        """子类实现的流式输出逻辑"""
        pass

    @classmethod
    def from_env(cls, **kwargs):
        """从环境变量创建模型"""
        model = ChatOpenAI(
            model=os.getenv("LLM_MODEL_FAST", ""),
            base_url=os.getenv("LLM_API_URL", ""),
            api_key=os.getenv("LLM_API_KEY", ""),
            temperature=0,
            streaming=True,
        )
        return cls(model=model, **kwargs)
