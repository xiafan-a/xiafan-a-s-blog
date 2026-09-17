# mcp-skill-service

Java 17 + Spring Boot 4 + Spring AI 2.0 capability service for blog-agent conversations. It owns the tool registry, skill registry, MCP metadata, persistence, audit logs, and tool execution that previously lived inside blog-agent.

## Features

- Hosts `SKILL.md` skills from `classpath*:skills` or an absolute `SKILL_ROOT`.
- Manages built-in tools, persisted custom API tools, REST execution, and SSE execution.
- Implements `web_search` in `McpWebSearchClient` with the same `bing-cn-mcp` server used by the old blog-agent flow.
- Runs `web_search` against Tavily (`TavilySearchClient`) by default, with the `npx`-launched `bing-cn-mcp` MCP source kept as a fallback.
- Implements `tavily_search` in `TavilySearchClient` against the Tavily REST API, as a second web search source.
- Implements `tavily_extract` in `TavilyExtractClient` to read the full content of up to 20 URLs per call.
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
- Moved blog-agent tools: `file_read`, `file_write`, `knowledge_retrieve`, `web_search`, `tavily_search`, `tavily_extract`
- Browser-style tools: `web_open`, `web_scrape`, `web_click`, `web_input`, `web_scroll`
- Current date: `get_Date`

`web_search` is the single search entry point. It calls **Tavily** by default and only falls back to the
`npx`-launched `bing-cn-mcp` MCP server (`McpWebSearchClient`, configured under `app.mcp.servers.bing-search`)
when Tavily has no API key, times out, or returns an error. The result always reports which source actually
served it, so a fallback is visible instead of silent:

```json
{
  "query": "2026 年新能源汽车销量",
  "provider": "bing-mcp",
  "fallback_from": "tavily",
  "fallback_reason": "Tavily API returned HTTP 429: ...",
  "results": [{"title": "...", "url": "...", "content": "..."}],
  "result_count": 5
}
```

Routing is configured with:

```yaml
app:
  search:
    provider: tavily          # tavily (default) or bing for the primary source
    fallback-enabled: true    # switch to the other source when the primary fails
```

A single call can override the configured order with `provider`: `tavily`, `bing`, or `auto` (the default,
which uses the configured order). `web_search` also accepts the Tavily options below (`topic`, `search_depth`,
`time_range`, `include_domains`, `exclude_domains`), but they only apply when Tavily serves the call; when the
bing fallback serves it, `query` and `num_results` are the only ones that take effect. Rows from the bing
fallback are normalized to the same `title`/`url`/`content` shape as Tavily's.

Both the primary and the fallback can fail. In that case the tool keeps the old contract: it returns
`"results": []` plus an `error` field, and lists every attempt under `attempts`.

`tavily_search` remains available for callers that want to pin the Tavily options explicitly; since
`web_search` already defaults to Tavily, prefer `web_search` unless you need that. It is implemented by
`TavilySearchClient` (`POST {app.tavily.api-url}/search`, `Authorization: Bearer`).
It is registered only when `app.tavily.enabled=true` and an API key is present; otherwise the tool is absent from the
registry so agents are never handed a tool that is guaranteed to fail.

```yaml
app:
  tavily:
    enabled: true
    api-key: ${TAVILY_API_KEY:}     # tvly-xxxx, or set the TAVILY_API_KEY environment variable
    api-url: https://api.tavily.com
    search-depth: basic             # basic/fast/ultra-fast = 1 credit, advanced = 2 credits
    max-results: 5
    topic: general                  # general / news / finance
    include-answer: false
    time-range: ""
    content-max-length: 500
    timeout-seconds: 30
```

```bash
curl -X POST http://localhost:8200/api/v1/tools/tavily_search/execute \
  -H 'Content-Type: application/json' \
  -d '{"query":"2026 年新能源汽车销量","topic":"news","time_range":"week","num_results":3}'
```

Parameters: `query` (required), `num_results`, `topic`, `search_depth`, `time_range`, `include_domains`,
`exclude_domains`; `num_results`/`max_results` are accepted as aliases and every omitted value falls back to the
config above. Result content is truncated to `content-max-length`. Failures are reported as an `error` field with an
empty `results` list, matching `web_search`.

`tavily_extract` shares the same configuration and key. It reads the readable content of one URL or a batch of
up to 20 URLs per call (`POST {app.tavily.api-url}/extract`). Parameters: `urls` (required; one URL or several
separated by commas/newlines), `query` (reranks and trims chunks by relevance), `extract_depth`, `format`,
`chunks_per_source` (1-5, only with `query`), `include_images`, `timeout`.

HTTP 200 can carry partial success, so always inspect `results` **and** `failed_results`. Each success returns
`url`, `title` and `raw_content` truncated to `extract-max-content-length`, plus `content_length` and a `truncated`
flag; each failure returns `url` and `error`. Try it with:

```bash
curl -X POST http://localhost:8200/api/v1/tools/tavily_extract/execute \
  -H "Content-Type: application/json" \
  -d "{""urls"":""https://en.wikipedia.org/wiki/Artificial_intelligence"",""query"":""applications""}"
```

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
