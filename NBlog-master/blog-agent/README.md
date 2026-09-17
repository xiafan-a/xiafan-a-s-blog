# blog-agent

NBlog 的 AI 助手独立前端项目,包含两个核心功能(自 `blog-view` 迁移而来):

- **知识问答(Qa)**:知识库管理 + RAG 问答。支持创建/编辑/删除知识库、上传知识文档(txt/md/pdf/doc/docx)、按知识库维护会话、流式回答、Markdown/KaTeX 渲染。
- **智能体(Agent)**:Agent 对话。支持多会话、会话重命名/搜索、Skill 选择与绑定(mcp-skill-service 经后端 blog-agent 转发)、步骤思考过程折叠展示、SSE 流式响应。

## 技术栈

Vue 2 + Vue Router + Element UI + Axios + markdown-it(@iktakahiro/markdown-it-katex)

布局为独立的左侧深色导航栏(OpenAI/DeepSeek 风格),不再依赖 blog-view 的 semantic-ui 布局。

## 目录结构

```
blog-agent/
├── public/                 # index.html、favicon
└── src/
    ├── api/                # qa.js(知识库/会话/文件)、agent.js(Agent 会话/聊天流)
    ├── assets/css/         # base.css、阿里 iconfont
    ├── components/qa/      # KnowledgeBaseItem、SessionItem
    ├── layout/             # 左侧导航布局(Sidebar + 内容区)
    ├── router/             # /qa 知识问答、/agent 智能体
    ├── util/               # get-page-title
    └── views/              # qa/Qa.vue、agent/Agent.vue
```

## 开发

```bash
npm install
npm run serve   # 默认端口 8089
```

开发环境下 `/api/v1` 请求会被代理到本机 `http://localhost:8088`(后端 blog-agent 聊天服务),可在 `vue.config.js` 的 `devServer.proxy` 中调整。

## 构建

```bash
npm run build   # 产物输出到 dist/
```

## 部署

- 构建产物可独立部署(nginx 静态托管),将 `/api/v1` 反向代理到后端 blog-agent 服务(默认 8088)。
- 侧边栏「返回博客」链接在 `src/settings.js` 的 `BLOG_URL` 中配置,请按实际部署地址修改。
- 如需部署在子路径下,同步修改 `vue.config.js` 的 `publicPath` 与路由 `base`。
