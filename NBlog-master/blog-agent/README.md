# blog-agent

独立的 AI 对话前端项目,包含两个核心功能(自 `blog-view` 迁移并重构):

- **知识问答(Qa)**:知识库管理 + RAG 问答。支持创建/编辑/删除知识库、上传知识文档(txt/md/pdf/doc/docx)、按知识库维护会话、SSE 流式回答、Markdown/KaTeX 渲染。
- **智能体(Agent)**:Agent 对话。支持多会话、会话重命名/搜索/删除、Skill 选择与绑定(mcp-skill-service 经后端转发)、步骤思考过程折叠展示、SSE 流式响应。

## 界面

ChatGPT 网页风格:

- **左侧深色侧边栏**:「新对话」按钮、会话搜索、历史会话列表(知识问答模式下按知识库分组,悬停可新建/上传/编辑/删除;行内重命名/删除会话)
- **顶部模式选择器**:下拉切换「知识问答」/「智能体」两种模式
- **消息区**:无边框通栏布局,用户消息右侧灰色气泡,助手消息带头像并以 Markdown 渲染(暗色代码块、表格、KaTeX 公式),思考中动画
- **底部输入框**:居中悬浮圆角输入框,自动增高,Skill 选择按钮内嵌,Ctrl + Enter 发送
- **流式输出**:SSE 流式渲染回答;移动端侧边栏可折叠

## 技术栈

Vue 2 + Vue Router + Element UI + Axios + markdown-it(@iktakahiro/markdown-it-katex)

## 目录结构

```
blog-agent/
├── public/                 # index.html、favicon
└── src/
    ├── api/                # qa.js(知识库/会话/文件)、agent.js(Agent 会话/聊天流)
    ├── assets/css/         # base.css、chat.css(共享聊天样式)、阿里 iconfont
    ├── components/         # 通用组件
    ├── layout/             # 布局:Sidebar(历史会话)+ TopBar(模式选择)+ 内容区
    ├── router/             # /qa 知识问答、/agent 智能体
    ├── util/               # chat-bus(侧边栏与视图的共享状态/事件)、get-page-title
    └── views/              # qa/Qa.vue、agent/Agent.vue
```

侧边栏与聊天视图之间通过 `src/util/chat-bus.js`(事件总线 + `Vue.observable` 共享状态)通信:视图持有会话数据并写入 `chatState`,侧边栏渲染历史会话并通过 `bus` 派发动作(新建/选择/重命名/删除等),由视图调用 API 完成。

## 开发

```bash
npm install
npm run serve   # 默认端口 8089
```

开发环境下 `/api/v1` 请求会被代理到本机 `http://localhost:8088`(后端聊天服务),可在 `vue.config.js` 的 `devServer.proxy` 中调整。

## 构建

```bash
npm run build   # 产物输出到 dist/
```

## 部署

- 构建产物可独立部署(nginx 静态托管),将 `/api/v1` 反向代理到后端聊天服务(默认 8088)。
- 如需部署在子路径下,同步修改 `vue.config.js` 的 `publicPath` 与路由 `base`。
