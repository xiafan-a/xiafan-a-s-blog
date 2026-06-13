"""
Agent Service - Core service for intelligent agent with ReAct-style tool calling capabilities.
"""
import os
import json
import uuid
import re
import logging
import time
from typing import AsyncGenerator, List, Dict, Any, Optional

from dotenv import load_dotenv
from openai import OpenAI
from entity.Tool import ToolCall, ToolResult, RESPONSE_TYPE_STEP_DONE
from entity.Tool import RESPONSE_TYPE_THOUGHT, RESPONSE_TYPE_ACTION, RESPONSE_TYPE_OBSERVATION, RESPONSE_TYPE_SUMMARY
from service.toolRegistryService import tool_registry
from config.settings import MAX_REACT_ITERATIONS, REACT_THINKING_ENABLED

logger = logging.getLogger(__name__)

load_dotenv()

# ReAct Prompt Template
REACT_PROMPT_TEMPLATE = """
请注意，你是一个有能力调用外部工具的智能助手。

可用工具如下:
{tools}

请严格按照以下格式进行回应:

Thought: 你的思考过程，用于分析问题、拆解任务和规划下一步行动。
Action: 你决定采取的行动，必须是以下格式之一:
- `{{tool_name}}[{{tool_input}}]`:调用一个可用工具。
- `Finish[最终答案]`:当你认为已经获得最终答案时。
- 当你收集到足够的信息，能够回答用户的最终问题时，你必须在Action:字段后使用 Finish[最终答案] 来输出最终答案。

现在，请开始解决以下问题:
Question: {question}
History: {history}
"""

# Load configuration from environment
API_KEY = os.getenv("API_KEY")
API_URL = os.getenv("API_URL")
MODEL_NAME = os.getenv("MODEL", "")


class AgentService:
    """Intelligent Agent Service with ReAct-style tool calling capabilities"""

    def __init__(self):
        self.client = OpenAI(
            api_key=API_KEY,
            base_url=API_URL
        )
        self.tool_registry = tool_registry

    async def process_message(
        self,
        user_message: str,
        conversation_history: List[Dict] = None,
        available_tools: List[str] = None,
        session_id: str = None,
        max_iterations: int = MAX_REACT_ITERATIONS,
        enable_thought: bool = REACT_THINKING_ENABLED
    ) -> AsyncGenerator[Dict, None]:
        """
        Process user message with ReAct-style tool calling support.

        ReAct loop: Thought -> Action -> Observation -> ... -> Final Response

        Args:
            user_message: User's input message
            conversation_history: Previous conversation messages
            available_tools: List of tool names to make available (None = all)
            session_id: Session identifier for tracking
            max_iterations: Maximum number of ReAct loops (default from settings)
            enable_thought: Whether to stream thought process (default from settings)

        Yields:
            Dictionary with response chunks including thought, action, observation, text
        """
        if not session_id:
            session_id = str(uuid.uuid4())

        # Build messages with new ReAct format
        messages = self._build_react_messages(user_message, conversation_history, available_tools)

        try:
            async for event in self._react_loop(messages, session_id, max_iterations, enable_thought):
                yield event
        except Exception as e:
            logger.error(f"Error in ReAct agent processing: {str(e)}")
            yield {
                "type": "error",
                "error": str(e),
                "session_id": session_id,
                "done": True
            }

    def _build_react_messages(
        self,
        user_message: str,
        conversation_history: List[Dict] = None,
        available_tools: List[str] = None
    ) -> List[Dict]:
        """使用新 ReAct 模板构建消息"""
        tools_desc = self._format_tools_for_prompt(available_tools)

        # 构建历史记录（文本格式）
        history_text = ""
        if conversation_history:
            for msg in conversation_history:
                if msg["role"] == "user":
                    history_text += f"User: {msg['content']}\n"
                elif msg["role"] == "assistant":
                    history_text += f"Assistant: {msg['content']}\n"

        # 格式化系统提示词
        system_prompt = REACT_PROMPT_TEMPLATE.format(
            tools=tools_desc,
            question=user_message,
            history=history_text if history_text else "无"
        )

        return [{"role": "system", "content": system_prompt}]

    def _format_tools_for_prompt(self, available_tools: List[str] = None) -> str:
        """将工具列表格式化为文本格式用于提示词"""
        tools = self.tool_registry.list_tools()
        if available_tools:
            tools = [t for t in tools if t.name in available_tools]

        tool_descriptions = []
        for tool in tools:
            params = ", ".join([f"{p.name}({p.type})" for p in tool.parameters])
            desc = f"- {tool.name}: {tool.description}, 参数: {params}"
            tool_descriptions.append(desc)

        return "\n".join(tool_descriptions)

    def _parse_output(self, text: str):
        """解析LLM的输出，提取Thought和Action。"""
        # Thought: 匹配到 Action: 或文本末尾
        thought_match = re.search(r"Thought:\s*(.*?)(?=\nAction:|$)", text, re.DOTALL)
        # Action: 匹配到文本末尾
        action_match = re.search(r"Action:\s*(.*?)$", text, re.DOTALL)
        if not thought_match:
            thought = None
        else:
            thought = thought_match.group(1).strip()
        if not action_match:
            action = None
        else:
            action = action_match.group(1).strip()
        return thought, action

    def _parse_action(self, action_text: str):
        """解析Action字符串，提取工具名称和输入。"""
        # 匹配 tool_name[...] 格式，支持带引号的 JSON 或简单字符串
        match = re.match(r"(\w+)\[(.*?)\]$", action_text, re.DOTALL)
        if match:
            tool_name = match.group(1)
            input_text = match.group(2).strip()
            return tool_name, input_text
        return None, None

    async def _react_loop(
        self,
        messages: List[Dict],
        session_id: str,
        max_iterations: int,
        enable_thought: bool
    ) -> AsyncGenerator[Dict, None]:
        """基于文本格式的 ReAct 循环 - 获取完整输出后流式返回"""
        start_time = time.time()
        iteration = 0
        react_steps = []
        while iteration < max_iterations:
            # 1. 获取 LLM 输出
            response = self.client.chat.completions.create(
                model=MODEL_NAME,
                messages=messages,
                stream=False
            )
            output_text = response.choices[0].message.content

            # 2. 解析输出提取 Thought 和 Action
            thought, action = self._parse_output(output_text)

            # 3. 输出 Thought
            if thought:
                # print(f"\n🤔 思考: {thought}")
                if enable_thought:
                    yield {
                        "type": RESPONSE_TYPE_THOUGHT,
                        "content": thought,
                        "step": iteration,
                        "session_id": session_id,
                        "partial": False
                    }

            # 4. 检查 Action
            if not action:
                # print("⚠️ 警告: 未能解析出有效的 Action，流程终止。")
                break

            # 5. 检查是否为 Finish 指令
            if action.startswith("Finish"):
                final_answer = re.match(r"Finish\[(.*)\]", action).group(1)
                # print(f"\n🎉 最终答案: {final_answer}")
                yield {
                    "type": "text",
                    "content": final_answer,
                    "step": iteration,
                    "session_id": session_id
                }
                break

            # 6. 解析 Action 中的工具调用
            tool_name, tool_input = self._parse_action(action)
            if not tool_name:
                # print(f"⚠️ 警告: 无法解析 Action 格式: {action}")
                # 尝试直接返回完整输出
                yield {
                    "type": "text",
                    "content": output_text,
                    "step": iteration,
                    "session_id": session_id
                }
                break

            # 7. 输出 Action
            params = self._parse_action_params(tool_input)
            # print(f"🎬 行动: {tool_name}[{tool_input}]")

            yield {
                "type": RESPONSE_TYPE_ACTION,
                "tool_name": tool_name,
                "parameters": params,
                "step": iteration,
                "session_id": session_id
            }

            # 8. 检查工具是否存在
            tool = self.tool_registry.get_tool(tool_name)
            if not tool:
                observation = f"错误: 未找到名为 '{tool_name}' 的工具。"
                # print(f"❌ {observation}")
            else:
                # 9. 执行工具
                tool_call = {"name": tool_name, "params": params}
                result = await self._execute_tool(tool_call)

                # 10. 输出 Observation
                if result["success"]:
                    observation_result = result.get("result", "执行成功")
                    # print(f"👁️ 观察: {observation_result}")
                else:
                    observation_result = result.get("error", "执行失败")
                    # print(f"❌ 观察: {observation_result}")

                yield {
                    "type": RESPONSE_TYPE_OBSERVATION,
                    "tool_name": tool_name,
                    "success": result["success"],
                    "result": result.get("result"),
                    "error": result.get("error"),
                    "execution_time": result.get("execution_time", 0),
                    "step": iteration,
                    "session_id": session_id
                }

                # 11. 添加到消息历史
                observation_text = f"Observation: {observation_result}"
                assistant_content = f"Thought: {thought}\nAction: {action}"
                messages.append({"role": "assistant", "content": assistant_content})
                messages.append({"role": "user", "content": observation_text})

                react_steps.append({
                    "thought": thought,
                    "action": tool_call,
                    "observation": result.get("result"),
                    "success": result["success"]
                })

            iteration += 1
            # print(f"--- 第 {iteration} 轮完成 ---\n")

        # 12. 生成总结和完成信号
        summary = self._generate_summary(
            react_steps=react_steps,
            session_id=session_id,
            total_iterations=iteration,
            start_time=start_time
        )
        # print(f"\n📊 {summary['summary']}")
        yield summary

        yield {
            "type": "done",
            "session_id": session_id,
            "done": True,
            "iterations": iteration,
            "total_steps": len(react_steps)
        }

    def _parse_action_params(self, input_text: str) -> Dict:
        """解析 Action 输入参数"""
        input_text = input_text.strip() if input_text else ""
        if not input_text:
            return {}

        # 尝试解析为 JSON
        try:
            return json.loads(input_text)
        except json.JSONDecodeError:
            # 尝试解析为 key=value 格式
            params = {}
            has_kv = False
            for item in input_text.split(","):
                if "=" in item:
                    key, value = item.split("=", 1)
                    params[key.strip()] = value.strip()
                    has_kv = True

            # 如果不是 key=value 格式，将整个字符串作为 query 参数
            if not has_kv:
                return {"query": input_text}

            return params

    def _extract_finish_answer(self, text: str) -> str:
        """提取 Finish[...] 中的答案"""
        match = re.search(r'Finish\[(.*?)\]', text, re.DOTALL)
        if match:
            return match.group(1).strip()
        return text.replace("Finish[", "").replace("]", "").strip()

    async def _execute_tool(self, tool_call: Dict) -> Dict:
        """执行工具调用"""
        tool_name = tool_call["name"]
        params = tool_call["params"]

        call = ToolCall(
            tool_name=tool_name,
            parameters=params,
            call_id=str(uuid.uuid4())
        )

        result = await self.tool_registry.execute(call)

        return {
            "success": result.success,
            "result": result.result,
            "error": result.error,
            "execution_time": result.execution_time
        }

    def _generate_summary(
        self,
        react_steps: List[Dict],
        session_id: str,
        total_iterations: int,
        start_time: float
    ) -> Dict:
        """生成任务总结信息"""
        end_time = time.time()
        total_time = end_time - start_time

        # 统计使用的工具
        tools_used = {}
        for step in react_steps:
            if "action" in step:
                tool_name = step["action"]["name"]
                tools_used[tool_name] = tools_used.get(tool_name, 0) + 1

        # 统计成功的工具调用
        successful_calls = sum(1 for step in react_steps if step.get("success", False))
        failed_calls = len(react_steps) - successful_calls

        # 构建总结描述
        summary_parts = [
            f"任务完成",
            f"共执行 {total_iterations} 次迭代"
        ]
        if tools_used:
            summary_parts.append(f"使用了 {len(tools_used)} 个工具")
            tools_summary = ", ".join([f"{name}({count}次)" for name, count in tools_used.items()])
            summary_parts.append(f"({tools_summary})")

        if failed_calls > 0:
            summary_parts.append(f"其中 {failed_calls} 次调用失败")

        return {
            "type": RESPONSE_TYPE_SUMMARY,
            "session_id": session_id,
            "total_time": round(total_time, 2),
            "iterations": total_iterations,
            "steps_count": len(react_steps),
            "successful_calls": successful_calls,
            "failed_calls": failed_calls,
            "tools_used": tools_used,
            "summary": "，".join(summary_parts)
        }

    async def chat(
        self,
        user_message: str,
        conversation_history: List[Dict] = None,
        available_tools: List[str] = None
    ) -> Dict[str, Any]:
        """
        Non-streaming chat method for simple use cases.
        Returns the complete response as a dictionary.
        """
        full_response = {
            "content": "",
            "tool_calls": [],
            "steps": [],
            "session_id": str(uuid.uuid4())
        }

        async for chunk in self.process_message(
            user_message=user_message,
            conversation_history=conversation_history,
            available_tools=available_tools,
            session_id=full_response["session_id"]
        ):
            if chunk["type"] == "text":
                full_response["content"] += chunk["content"]
            elif chunk["type"] == RESPONSE_TYPE_ACTION:
                full_response["tool_calls"].append({
                    "tool_name": chunk["tool_name"],
                    "parameters": chunk["parameters"]
                })
            elif chunk["type"] == RESPONSE_TYPE_THOUGHT:
                full_response["steps"].append({
                    "type": "thought",
                    "content": chunk["content"]
                })
            elif chunk["type"] == RESPONSE_TYPE_OBSERVATION:
                if full_response["steps"]:
                    full_response["steps"][-1]["observation"] = chunk["result"]

        return full_response


# Global agent service instance
agent_service = AgentService()


def get_agent_service() -> AgentService:
    """Get the global agent service instance"""
    return agent_service
