"""
Tool entity definitions for MCP tool system.
"""
from pydantic import BaseModel
from typing import Dict, Any, Optional, List
from datetime import datetime


# ReAct Stream Response Types
RESPONSE_TYPE_THOUGHT = "thought"          # Agent thinking/reasoning
RESPONSE_TYPE_ACTION = "action"            # Tool execution request
RESPONSE_TYPE_OBSERVATION = "observation"  # Tool execution result (ReAct style)
RESPONSE_TYPE_STEP_DONE = "step_done"      # Single ReAct step completed
RESPONSE_TYPE_SUMMARY = "summary"          # Task summary after completion


class ToolParameter(BaseModel):
    """Tool parameter definition"""
    name: str
    type: str  # string, number, boolean, array, object
    description: str
    required: bool = True
    default: Optional[Any] = None
    enum: Optional[List[str]] = None  # Enum values


class ToolDefinition(BaseModel):
    """Tool definition"""
    id: Optional[int] = None
    name: str  # Unique identifier, e.g. "document_summarize"
    display_name: str  # Display name, e.g. "Document Summary"
    description: str  # Tool description, LLM uses this to select tools
    parameters: List[ToolParameter] = []  # Parameter list
    category: str = "general"  # Category: document, database, web, system
    enabled: bool = True
    requires_auth: bool = False
    timeout: int = 60  # Timeout in seconds
    created_at: Optional[datetime] = None
    updated_at: Optional[datetime] = None

    class Config:
        from_attributes = True


class ToolCall(BaseModel):
    """Tool call request"""
    tool_name: str
    parameters: Dict[str, Any]
    call_id: str  # Call ID for tracking


class ToolResult(BaseModel):
    """Tool execution result"""
    call_id: str
    tool_name: str
    success: bool
    result: Any
    error: Optional[str] = None
    execution_time: float  # Execution time in seconds


class ToolCreate(BaseModel):
    """Request model for creating a tool"""
    name: str
    display_name: str
    description: str
    parameters: List[Dict[str, Any]] = []
    category: str = "custom"
    executor_type: str = "api"  # api, webhook
    # API execution config
    api_url: Optional[str] = None
    api_method: str = "POST"
    api_headers: Optional[Dict[str, str]] = None
    auth_type: str = "none"  # none, api_key, bearer
    auth_config: Optional[Dict[str, Any]] = None
    response_path: Optional[str] = None
    # General config
    timeout: int = 30
    enabled: bool = True


class ToolUpdate(BaseModel):
    """Request model for updating a tool"""
    display_name: Optional[str] = None
    description: Optional[str] = None
    parameters: Optional[List[Dict[str, Any]]] = None
    category: Optional[str] = None
    api_url: Optional[str] = None
    api_method: Optional[str] = None
    api_headers: Optional[Dict[str, str]] = None
    auth_type: Optional[str] = None
    auth_config: Optional[Dict[str, Any]] = None
    response_path: Optional[str] = None
    timeout: Optional[int] = None
    enabled: Optional[bool] = None
