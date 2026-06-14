import axios from 'axios'

const CHAT_API_BASE = '/api/v1'
// const CHAT_API_BASE = 'http://localhost:8100/api/v1'
// 普通问答
export function chat(message, knowledgeBaseId, history) {
	return axios.post(`${CHAT_API_BASE}/chat`, {
		message,
		knowledge_base_id: knowledgeBaseId,
		history
	})
}

// 获取知识库列表
export function getKnowledgeBases() {
	return axios.get(`${CHAT_API_BASE}/knowledge-bases`)
}

// 创建知识库
export function createKnowledgeBase(data) {
	return axios.post(`${CHAT_API_BASE}/create/knowledge-base`, data)
}

// 更新知识库
export function updateKnowledgeBase(id, data) {
	return axios.post(`${CHAT_API_BASE}/knowledge-bases/${id}`, data)
}

// 删除知识库
export function deleteKnowledgeBase(id) {
	return axios.delete(`${CHAT_API_BASE}/knowledge-bases/${id}`)
}

// 获取知识库详情
export function getKnowledgeBaseSessions(id) {
	return axios.get(`${CHAT_API_BASE}/knowledge-bases/${id}`)
}

// 上传知识文件
export function uploadKnowledgeFile(knowledgeBaseId, file) {
	const formData = new FormData()
	formData.append('knowledge_base_id', knowledgeBaseId)
	formData.append('file', file)
	return axios.post(`${CHAT_API_BASE}/knowledge-files`, formData, {
		headers: {
			'Content-Type': 'multipart/form-data'
		}
	})
}

// 获取知识库文件列表
export function getKnowledgeFiles(knowledgeBaseId) {
	return axios.get(`${CHAT_API_BASE}/knowledge-bases/${knowledgeBaseId}/files`)
}

// 删除知识文件
export function deleteKnowledgeFile(fileId) {
	return axios.delete(`${CHAT_API_BASE}/knowledge-files/${fileId}`)
}

// 获取会话列表
export function getSessions(knowledgeBaseId) {
	return axios.get(`${CHAT_API_BASE}/sessions`, {
		params: {
			knowledge_base_id: knowledgeBaseId
		}
	})
}
//保存消息
export function createMessage(data) {
	return axios.post(`${CHAT_API_BASE}/messages`, data)
}

// 获取会话消息
export function getSessionMessages(sessionId) {
	return axios.get(`${CHAT_API_BASE}/sessions/${sessionId}/messages`)
}

// 创建会话
export function createSession(data) {
	return axios.post(`${CHAT_API_BASE}/sessions`, data)
}

// 更新会话
export function updateSession(sessionId, data) {
	return axios.put(`${CHAT_API_BASE}/sessions/${sessionId}`, data)
}

// 删除会话
export function deleteSession(sessionId) {
	return axios.delete(`${CHAT_API_BASE}/sessions/${sessionId}`)
}

// 聊天流式响应 (for Qa.vue)
export function chatStream(message, knowledgeBaseId, conversationHistory, onMessage) {
	return new Promise(async (resolve, reject) => {
		try {
			const identification = window.localStorage.getItem('identification')
			const headers = {
				'Content-Type': 'application/json'
			}
			if (identification) {
				headers.identification = identification
			}
			const response = await fetch(`${CHAT_API_BASE}/chat/rag/stream`, {
				method: 'POST',
				headers: headers,
				body: JSON.stringify({
					message: message,
					knowledge_base_id: knowledgeBaseId,
					conversation_history: conversationHistory
				})
			})

			if (!response.ok) {
				throw new Error('API请求失败')
			}

			const reader = response.body.getReader()
			const decoder = new TextDecoder()
			let buffer = ''

			while (true) {
				const { done, value } = await reader.read()
				if (done) break

				buffer += decoder.decode(value, { stream: true })

				const lines = buffer.split('\n')
				buffer = lines.pop() || ''

				for (const line of lines) {
					if (line.trim()) {
						try {
							let jsonStr = line
							if (line.startsWith('data: ')) {
								jsonStr = line.substring(6)
							}
							const data = JSON.parse(jsonStr)
							if (onMessage) {
								onMessage(data)
							}
						} catch (e) {
							// 忽略解析错误
						}
					}
				}
			}
			resolve()
		} catch (error) {
			reject(error)
		}
	})
}
