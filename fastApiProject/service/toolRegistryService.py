"""
Tool Registry Service - Manages tool registration and execution.
"""
from typing import Dict, List, Callable, Any, Optional
import time
import json
import logging

from entity.Tool import ToolDefinition, ToolParameter, ToolCall, ToolResult

logger = logging.getLogger(__name__)


class ToolRegistry:
    """Tool registry for managing and executing tools"""

    def __init__(self):
        self._tools: Dict[str, ToolDefinition] = {}
        self._executors: Dict[str, Callable] = {}

    def register(self, tool_def: ToolDefinition, executor: Callable) -> None:
        """Register a tool with its executor"""
        self._tools[tool_def.name] = tool_def
        self._executors[tool_def.name] = executor
        logger.info(f"Registered tool: {tool_def.name}")

    def unregister(self, name: str) -> bool:
        """Unregister a tool"""
        if name in self._tools:
            del self._tools[name]
            del self._executors[name]
            logger.info(f"Unregistered tool: {name}")
            return True
        return False

    def get_tool(self, name: str) -> Optional[ToolDefinition]:
        """Get tool definition by name"""
        return self._tools.get(name)

    def list_tools(self, category: str = None, enabled_only: bool = True) -> List[ToolDefinition]:
        """List all available tools"""
        tools = list(self._tools.values())
        if category:
            tools = [t for t in tools if t.category == category]
        if enabled_only:
            tools = [t for t in tools if t.enabled]
        return tools

    def get_default_tools(self) -> List[str]:
        """Get list of default tool names based on configuration"""
        from config.settings import DEFAULT_TOOLS
        if DEFAULT_TOOLS:
            return [t.strip() for t in DEFAULT_TOOLS.split(",")]
        # Fallback: return all tools in 'web' category
        return [t.name for t in self.list_tools(category="web")]

    def get_tools_for_llm(self, tool_names: List[str] = None) -> List[Dict]:
        """
        Get tools in OpenAI Function Calling format.
        This format is compatible with OpenAI API and many LLM providers.
        """
        tools = []
        all_tools = self.list_tools()

        for tool in all_tools:
            # Filter by tool names if specified
            if tool_names and tool.name not in tool_names:
                continue

            # Build parameters schema
            params = {
                "type": "object",
                "properties": {},
                "required": []
            }

            for p in tool.parameters:
                params["properties"][p.name] = {
                    "type": p.type,
                    "description": p.description
                }
                if p.enum:
                    params["properties"][p.name]["enum"] = p.enum
                if p.default is not None:
                    params["properties"][p.name]["default"] = p.default
                if p.required:
                    params["required"].append(p.name)

            tools.append({
                "type": "function",
                "function": {
                    "name": tool.name,
                    "description": tool.description,
                    "parameters": params
                }
            })

        return tools

    async def execute(self, call: ToolCall) -> ToolResult:
        """Execute a tool call"""
        start_time = time.time()

        try:
            executor = self._executors.get(call.tool_name)
            if not executor:
                raise ValueError(f"Tool '{call.tool_name}' not found")

            tool_def = self._tools.get(call.tool_name)
            if not tool_def:
                raise ValueError(f"Tool definition for '{call.tool_name}' not found")

            if not tool_def.enabled:
                raise ValueError(f"Tool '{call.tool_name}' is disabled")

            # Validate parameters
            validated_params = self._validate_parameters(tool_def, call.parameters)

            # Execute the tool
            result = await executor(**validated_params)

            execution_time = time.time() - start_time
            logger.info(f"Tool '{call.tool_name}' executed successfully in {execution_time:.2f}s")

            return ToolResult(
                call_id=call.call_id,
                tool_name=call.tool_name,
                success=True,
                result=result,
                execution_time=execution_time
            )

        except Exception as e:
            execution_time = time.time() - start_time
            error_msg = str(e)
            logger.error(f"Tool '{call.tool_name}' execution failed: {error_msg}")

            return ToolResult(
                call_id=call.call_id,
                tool_name=call.tool_name,
                success=False,
                result=None,
                error=error_msg,
                execution_time=execution_time
            )

    def _validate_parameters(self, tool_def: ToolDefinition, params: Dict[str, Any]) -> Dict[str, Any]:
        """Validate and fill default values for parameters"""
        validated = {}
        param_defs = {p.name: p for p in tool_def.parameters}

        # Check required parameters
        for param in tool_def.parameters:
            if param.required and param.name not in params:
                if param.default is not None:
                    validated[param.name] = param.default
                else:
                    raise ValueError(f"Missing required parameter: {param.name}")

        # Add provided parameters
        for key, value in params.items():
            if key in param_defs:
                validated[key] = value
            else:
                logger.warning(f"Unknown parameter '{key}' for tool '{tool_def.name}'")

        # Fill defaults for missing optional parameters
        for param in tool_def.parameters:
            if param.name not in validated and param.default is not None:
                validated[param.name] = param.default

        return validated


# Global tool registry instance
tool_registry = ToolRegistry()


def get_tool_registry() -> ToolRegistry:
    """Get the global tool registry instance"""
    return tool_registry
