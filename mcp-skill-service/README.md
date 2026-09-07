# mcp-skill-service

Java 17 + Spring Boot 4 + Spring AI 2.0 capability service for blog-agent conversations. It owns the tool registry, skill registry, MCP metadata, persistence, audit logs, and tool execution that previously lived inside blog-agent.

## Features

- Hosts `SKILL.md` skills from `classpath*:skills` or an absolute `SKILL_ROOT`.
- Manages built-in tools, persisted custom API tools, REST execution, and SSE execution.
- Implements `web_search` in `McpWebSearchClient` with the same `bing-cn-mcp` server used by the old blog-agent flow.
- Exposes tool and skill capabilities over Streamable HTTP MCP at `/mcp`.
- Persists skill/tool/MCP metadata and audit records in PostgreSQL.
- Keeps the old blog-agent tool API available at `/api/v1/agent/tools`.

## Run

```bash
mvnw.cmd -f pom.xml test
mvnw.cmd -f pom.xml package
java -jar target/mcp-skill-service-0.0.1-SNAPSHOT.jar
```

Default connection values use the existing blog PostgreSQL URL. Override them with:

```bash
SPRING_DATASOURCE_URL=jdbc:postgresql://host:5432/nblog
SPRING_DATASOURCE_USERNAME=postgres
SPRING_DATASOURCE_PASSWORD=secret
SKILL_ROOT=/app/skills
SERVER_PORT=8200
```

## REST Endpoints

| Method | Path | Purpose |
|---|---|---|
| GET | `/api/v1/tools` | List tools, optionally by `category` |
| GET | `/api/v1/tools/{toolName}` | Get tool definition |
| POST | `/api/v1/tools` | Register and persist a custom API tool |
| DELETE | `/api/v1/tools/{toolName}` | Delete a custom tool |
| POST | `/api/v1/tools/{toolName}/execute` | Execute a tool |
| POST | `/api/v1/tools/stream` | Execute by SSE |
| POST | `/api/v1/tools/{toolName}/execute/stream` | Execute one tool by SSE |
| GET | `/api/v1/tools/categories/list` | List tool categories |
| GET | `/api/v1/agent/tools...` | Legacy blog-agent tool API alias |
| GET | `/api/v1/skills` | List skills |
| GET | `/api/v1/skills/{name}` | Get skill detail |
| POST | `/api/v1/skills/{name}/apply` | Prepare system prompt and messages |
| GET | `/api/v1/records/tool-usage` | Query tool audit records |
| GET | `/api/v1/records/skill-usage` | Query skill audit records |
| GET | `/api/v1/records/mcp-calls` | Query MCP call audit records |

## MCP Endpoint

```text
POST http://mcp-skill-service:8200/mcp
```

The server advertises `blog-agent-capabilities` version `1.0.0` and exposes these MCP tools:

- Skills: `list_skills`, `get_skill`, `apply_skill`
- Tool management: `list_tools`, `get_tool`, `execute_tool`
- Moved blog-agent tools: `file_read`, `file_write`, `knowledge_retrieve`, `web_search`
- Browser-style tools: `web_open`, `web_scrape`, `web_click`, `web_input`, `web_scroll`
- Current date: `get_Date`

`web_search` is supported. It is implemented centrally by `McpWebSearchClient`, which connects to the MCP server configured under `app.mcp.servers.bing-search` (`bing-cn-mcp` by default) and returns normalized search results.

## Tool Execution

Blocking REST execute:

```json
POST /api/v1/tools/web_search/execute
{
  "query": "latest Java web development",
  "num_results": 5
}
```

SSE execute:

```json
POST /api/v1/tools/get_Date/execute/stream
{}
```

The SSE stream emits a `started` event followed by a `result` event containing `success`, `result`, `error`, and `execution_time`.

## Skill Apply Request

```json
POST /api/v1/skills/chinese-blog-writer/apply
{
  "userMessage": "Write a blog post about Spring AI",
  "conversationHistory": [
    {"role": "user", "content": "previous message"}
  ]
}
```

Response contains `skill`, `system_prompt`, and `prepared_messages` for blog-agent or another caller to inject into the conversation model call.

## Blog-Agent Integration

Blog-agent now caches tool definitions from this service and forwards every tool call over HTTP. Point it at this service:

```bash
CAPABILITY_SERVICE_BASE_URL=http://mcp-skill-service:8200
CAPABILITY_SERVICE_ENABLED=true
CAPABILITY_SERVICE_TIMEOUT_SECONDS=30
```

It also exposes skill queries through `/api/v1/agent/skills` on the blog-agent side while delegating to this service.
