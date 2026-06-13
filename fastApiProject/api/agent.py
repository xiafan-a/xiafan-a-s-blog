"""
Agent API Endpoints - Handles agent chat and tool management (simplified).
"""
import json
import uuid
from typing import Dict, List, Optional

from fastapi import APIRouter, HTTPException, Body, Depends
from fastapi.responses import StreamingResponse
from pydantic import BaseModel
from sqlmodel import Session

from database import get_db
from entity import ConversationSession
from entity.Tool import ToolDefinition, ToolParameter, ToolCreate, ToolUpdate, ToolCall
from service.agentService import get_agent_service
from service.conversationSessionService import conversation_session_service
from service.toolRegistryService import tool_registry, get_tool_registry
from util.response import ApiResponse

router = APIRouter(prefix="/agent", tags=["Agent"])


# ============================================
# Request Models
# ============================================

class AgentChatRequest(BaseModel):
    """Request model for agent chat with ReAct support"""
    message: str
    session_id: Optional[int] = None
    conversation_history: Optional[List[Dict[str, str]]] = None
    available_tools: Optional[List[str]] = None
    max_iterations: Optional[int] = 5  # ReAct loop max iterations
    enable_thought: Optional[bool] = True  # Enable thought process streaming


# ============================================
# Agent Chat Endpoints
# ============================================

@router.post("/chat/stream")
async def agent_chat_stream(request: AgentChatRequest):
    """
    Agent chat with streaming response using ReAct style.
    Supports file_read, web_search, and other tools.
    Streams: thought, action, observation, text, done, error
    """
    agent = get_agent_service()

    # Resolve default tools if not specified
    available_tools = request.available_tools
    if available_tools is None:
        registry = get_tool_registry()
        available_tools = registry.get_default_tools()

    async def generate():
        try:
            print(request.message)
            async for chunk in agent.process_message(
                user_message=request.message,
                conversation_history=request.conversation_history,
                available_tools=available_tools,
                session_id=request.session_id,
                max_iterations=request.max_iterations,
                enable_thought=request.enable_thought
            ):
                print(chunk)
                yield f"data: {json.dumps(chunk, ensure_ascii=False, default=str)}\n\n"
        except Exception as e:
            yield f"data: {json.dumps({'type': 'error', 'error': str(e)}, ensure_ascii=False)}\n\n"

    return StreamingResponse(generate(), media_type="text/event-stream")


@router.post("/chat")
async def agent_chat(request: AgentChatRequest):
    """
    Agent chat with non-streaming response.
    Returns complete response after processing.
    """
    agent = get_agent_service()

    try:
        result = await agent.chat(
            user_message=request.message,
            conversation_history=request.conversation_history,
            available_tools=request.available_tools
        )
        return result
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))


# ============================================
# Tool Management Endpoints
# ============================================

@router.get("/tools")
async def list_tools(category: str = None):
    """
    List all available tools.
    Optionally filter by category.
    """
    registry = get_tool_registry()
    tools = registry.list_tools(category=category, enabled_only=False)

    return {
        "tools": [
            {
                "name": t.name,
                "display_name": t.display_name,
                "description": t.description,
                "category": t.category,
                "parameters": [p.dict() for p in t.parameters],
                "enabled": t.enabled,
                "timeout": t.timeout
            }
            for t in tools
        ]
    }


@router.get("/tools/{tool_name}")
async def get_tool(tool_name: str):
    """Get details of a specific tool"""
    registry = get_tool_registry()
    tool = registry.get_tool(tool_name)

    if not tool:
        raise HTTPException(status_code=404, detail=f"Tool '{tool_name}' not found")

    return {
        "name": tool.name,
        "display_name": tool.display_name,
        "description": tool.description,
        "category": tool.category,
        "parameters": [p.dict() for p in tool.parameters],
        "enabled": tool.enabled,
        "timeout": tool.timeout
    }


@router.post("/tools")
async def add_custom_tool(request: ToolCreate):
    """
    Add a custom tool (configuration-based).
    The tool will call an external API with the provided configuration.
    Note: Tools are only registered in memory (not persisted to database).
    """
    import httpx

    registry = get_tool_registry()

    # Check if tool already exists
    if registry.get_tool(request.name):
        raise HTTPException(status_code=400, detail=f"Tool '{request.name}' already exists")

    # Build tool definition
    params = [ToolParameter(**p) for p in request.parameters]
    tool_def = ToolDefinition(
        name=request.name,
        display_name=request.display_name,
        description=request.description,
        parameters=params,
        category=request.category,
        enabled=request.enabled,
        timeout=request.timeout
    )

    # Create executor function for API calls
    async def api_executor(**kwargs):
        headers = request.api_headers or {}

        # Add authentication headers
        if request.auth_type == "bearer" and request.auth_config:
            token = request.auth_config.get("token")
            headers["Authorization"] = f"Bearer {token}"
        elif request.auth_type == "api_key" and request.auth_config:
            key_name = request.auth_config.get("key_name", "X-API-Key")
            api_key = request.auth_config.get("api_key")
            headers[key_name] = api_key

        # Make API call
        async with httpx.AsyncClient(timeout=request.timeout) as client:
            if request.api_method.upper() == "GET":
                response = await client.get(
                    request.api_url,
                    headers=headers,
                    params=kwargs
                )
            else:
                response = await client.request(
                    method=request.api_method,
                    url=request.api_url,
                    headers=headers,
                    json=kwargs
                )

            result = response.json()

            # Extract data from response path if specified
            if request.response_path:
                for key in request.response_path.split("."):
                    if isinstance(result, dict):
                        result = result.get(key, {})
                    else:
                        break

            return result

    # Register the tool
    registry.register(tool_def, api_executor)

    return {
        "success": True,
        "tool": {
            "name": tool_def.name,
            "display_name": tool_def.display_name,
            "description": tool_def.description,
            "category": tool_def.category
        },
        "message": "Tool registered successfully (in-memory only, not persisted)"
    }


@router.post("/tools/{tool_name}/execute")
async def execute_tool_directly(tool_name: str, parameters: Dict):
    """
    Directly execute a tool with given parameters.
    Useful for testing tools or direct API access.
    """
    registry = get_tool_registry()

    # Check if tool exists
    tool = registry.get_tool(tool_name)
    if not tool:
        raise HTTPException(status_code=404, detail=f"Tool '{tool_name}' not found")

    # Execute tool
    call = ToolCall(
        tool_name=tool_name,
        parameters=parameters,
        call_id=str(uuid.uuid4())
    )

    result = await registry.execute(call)

    return {
        "call_id": result.call_id,
        "tool_name": result.tool_name,
        "success": result.success,
        "result": result.result,
        "error": result.error,
        "execution_time": result.execution_time
    }


@router.delete("/tools/{tool_name}")
async def delete_custom_tool(tool_name: str):
    """Delete a custom tool (only custom tools can be deleted)"""
    registry = get_tool_registry()

    # Check if it's a built-in tool
    built_in_tools = ["file_read", "file_write", "web_search"]

    if tool_name in built_in_tools:
        raise HTTPException(status_code=400, detail="Cannot delete built-in tools")

    # Remove from registry
    if not registry.unregister(tool_name):
        raise HTTPException(status_code=404, detail=f"Tool '{tool_name}' not found")

    return {"success": True, "message": f"Tool '{tool_name}' deleted"}


# ============================================
# Tool Categories Endpoint
# ============================================

@router.get("/tools/categories/list")
async def list_tool_categories():
    """List all tool categories"""
    registry = get_tool_registry()
    tools = registry.list_tools()

    categories = {}
    for tool in tools:
        if tool.category not in categories:
            categories[tool.category] = []
        categories[tool.category].append({
            "name": tool.name,
            "display_name": tool.display_name,
            "description": tool.description
        })

    return {"categories": categories}


@router.get("/sessions", response_model=ApiResponse[List[ConversationSession]])
def get_knowledge_base(
    kb_id: int = -1,
    db: Session = Depends(get_db)
):
    """根据知识库ID获取未删除的会话列表"""
    # 获取该知识库下未删除的会话列表
    sessions = conversation_session_service.get_sessions_by_knowledge_base(db, kb_id)
    return ApiResponse(code="200", data=sessions)
