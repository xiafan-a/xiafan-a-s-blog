import Vue from 'vue'

// 轻量事件总线:侧边栏(历史会话)与聊天视图之间的动作分发
export const bus = new Vue()

// 侧边栏共享状态:由聊天视图(Qa/Agent)写入,侧边栏读取
export const chatState = Vue.observable({
	// 知识问答:知识库(含各自 sessions)与当前会话
	qa: {
		knowledgeBases: [],
		activeSession: null
	},
	// 智能体:会话列表与当前会话
	agent: {
		sessions: [],
		activeId: null
	}
})
