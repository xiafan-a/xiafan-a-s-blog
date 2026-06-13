import axios from 'axios'

const CHAT_API_BASE = '/api/v1'

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

// 获取Agent会话列表
export function getAgentSessions(id) {
	return axios.get(`${CHAT_API_BASE}/agent/sessions`)
}

// 获取会话消息
export function getSessionMessages(sessionId) {
	return axios.get(`${CHAT_API_BASE}/sessions/${sessionId}/messages`)
}

// 创建消息
export function createMessage(data) {
	return axios.post(`${CHAT_API_BASE}/messages`, data)
}

// Agent 聊天流式响应
export function agentChatStream(message, sessionId, conversationHistory, availableTools, onMessage) {
	return new Promise(async (resolve, reject) => {
		try {
			const identification = window.localStorage.getItem('identification')
			const headers = {
				'Content-Type': 'application/json'
			}
			if (identification) {
				headers.identification = identification
			}
			const response = await fetch(`${CHAT_API_BASE}/agent/chat/stream`, {
				method: 'POST',
				headers: headers,
				body: JSON.stringify({
					message: message,
					session_id: sessionId,
					conversation_history: conversationHistory,
					available_tools: availableTools,
					max_iterations: 5,
					enable_thought: true
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
							console.log('[agent.js SSE] Parsed data:', data)
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
