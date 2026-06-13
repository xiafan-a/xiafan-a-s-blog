<template>
	<div class="ui container m-top">
		<div class="m-deepseek-container">
			<!-- 右侧对话区域 -->
			<div class="m-deepseek-main">
				<!-- 顶部标签栏 -->
				<div class="m-agent-header" ref="headerRef">
					<span class="m-agent-title">{{ sessionName || '智能体' }}</span>
					<div class="m-agent-actions">
						<button class="m-agent-btn" @click="createNewSession" title="新建对话">
							<span>+</span>
						</button>
						<button class="m-agent-btn" @click="toggleHistoryDialog" title="历史会话">
							<span>📜</span>
						</button>
					</div>
					<!-- 历史会话下拉列表 -->
					<div class="m-history-dropdown" v-if="showHistoryDialog" ref="dropdownRef">
						<div class="m-history-content">
							<div class="m-history-search">
								<input
									type="text"
									v-model="searchKeyword"
									placeholder="搜索会话..."
									class="m-history-search-input"
								/>
							</div>
							<div class="m-history-list">
								<div v-if="filteredHistorySessions.length === 0" class="m-history-empty">
									{{ searchKeyword ? '未找到匹配的会话' : '暂无历史会话' }}
								</div>
								<div
									v-for="session in filteredHistorySessions"
									:key="session.id"
									class="m-history-item"
									:class="{ active: session.id === activeSessionId }"
									@click="selectHistorySession(session)"
								>
									<div class="m-history-item-content">
										<input
											v-if="editingSessionId === session.id"
											type="text"
											v-model="editingSessionName"
											class="m-history-edit-input"
											@blur="saveSessionName(session)"
											@keyup.enter="saveSessionName(session)"
											@click.stop
										/>
										<span v-else class="m-history-name">{{ session.name || '新会话' }}</span>
									</div>
									<div class="m-history-item-actions">
										<button
											class="m-history-action-btn"
											@click.stop="startEditSession(session)"
											title="重命名"
										>✏</button>
										<button
											class="m-history-action-btn m-history-delete-btn"
											@click.stop="deleteSession(session)"
											title="删除"
										>×</button>
									</div>
								</div>
							</div>
						</div>
					</div>
				</div>
				<!-- 对话内容 -->
				<div class="m-deepseek-chat-area" ref="chatArea" @scroll="handleChatScroll">
					<div class="m-deepseek-message" v-for="(message, msgIndex) in messages" :key="msgIndex">
						<div class="m-deepseek-message-content" :class="{user: message.isUser}">
							<div class="m-deepseek-message-header" v-if="message.isUser">
								<span>我</span>
							</div>
							<div class="m-deepseek-message-header" v-else>
								<span>智能体</span>
							</div>
							<!-- 用户消息直接显示内容 -->
							<div class="m-deepseek-message-body" v-if="message.isUser">
								<span v-html="message.content"></span>
							</div>
							<!-- AI消息：先显示思考动画，再显示步骤，最后显示最终内容 -->
							<div v-else>
								<!-- 思考中动画 -->
								<div class="m-deepseek-message-body m-deepseek-thinking" v-if="!message.content && !message.done">
									<span class="m-thinking-text">思考中</span>
									<span class="m-thinking-dots">
										<span class="m-dot"></span>
										<span class="m-dot"></span>
										<span class="m-dot"></span>
									</span>
								</div>
								<!-- 步骤时间线 -->
								<div class="m-step-timeline" v-if="message.steps && message.steps.length > 0">
									<div v-for="(step, index) in message.steps" :key="step.id" class="m-step-item">
										<!-- 左侧编号 -->
										<div class="m-step-indicator">
											<div class="m-step-circle">
												<span class="m-step-number">{{ step.id }}</span>
											</div>
											<div class="m-step-line" v-if="index < message.steps.length - 1"></div>
										</div>
										<!-- 步骤卡片 -->
										<div class="m-step-card" :class="`m-step-card-${step.type}`">
											<!-- 步骤状态标签 -->
											<div class="m-step-status">
												<span class="m-step-status-text">{{ getStepStatus(step.type) }}</span>
											</div>
											<!-- 步骤内容 -->
											<div class="m-step-card-content">
												<div class="m-step-type-label">{{ getStepLabel(step.type) }}</div>
												<div class="m-step-detail" v-html="parseMarkdown(step.content)"></div>
												<!-- 可展开的详细数据 -->
												<div v-if="step.expandableData" class="m-expandable-container">
													<div class="m-expandable-header" @click="toggleExpand(step)">
														<span class="m-expandable-icon">{{ step._expanded ? '▼' : '▶' }}</span>
														<span class="m-expandable-title">查看详情 ({{ step.expandableData.results ? step.expandableData.results.length + ' 条结果' : '数据' }})</span>
													</div>
													<div v-if="step._expanded" class="m-expandable-content">
														<template v-if="step.expandableData.results">
															<div v-for="(item, idx) in step.expandableData.results" :key="idx" class="m-result-item">
																<div class="m-result-title"><a :href="item.url" target="_blank">{{ item.title }}</a></div>
																<div class="m-result-snippet">{{ item.snippet }}</div>
															</div>
														</template>
														<pre v-else>{{ JSON.stringify(step.expandableData, null, 2) }}</pre>
													</div>
												</div>
											</div>
										</div>
									</div>
								</div>
								<!-- 最终内容 -->
								<div class="m-deepseek-message-body" v-if="message.content">
									<span v-html="parseMarkdown(message.content)"></span>
								</div>
							</div>
						</div>
					</div>
				</div>

				<!-- 输入框 -->
				<div class="m-deepseek-input-area" v-if="activeSession">
					<div class="m-deepseek-input-container">
						<div class="m-deepseek-input-wrapper">
							<textarea
								v-model="inputMessage"
								placeholder="输入问题..."
								@keydown.ctrl.enter.prevent="sendMessage"
								@keydown.meta.enter.prevent="sendMessage"
								class="m-deepseek-input"
								rows="3"
								:disabled="isGenerating"
								ref="messageInput"
							></textarea>
						</div>
						<button class="m-deepseek-send-btn" @click="sendMessage" :disabled="isGenerating">
							<span class="m-deepseek-send-btn-text">↑</span>
						</button>
					</div>
				</div>
			</div>
		</div>

		<!-- 删除确认对话框 -->
		<div class="m-delete-dialog-overlay" v-if="showDeleteDialog" @click.self="cancelDelete">
			<div class="m-delete-dialog">
				<div class="m-delete-dialog-header">
					<span>确认删除</span>
				</div>
				<div class="m-delete-dialog-body">
					<p>确定要删除会话 "<strong>{{ sessionToDelete ? sessionToDelete.name : '新会话' }}</strong>" 吗？</p>
					<p class="m-delete-dialog-tip">此操作不可恢复</p>
				</div>
				<div class="m-delete-dialog-footer">
					<button class="m-delete-dialog-btn m-delete-dialog-btn-cancel" @click="cancelDelete">取消</button>
					<button class="m-delete-dialog-btn m-delete-dialog-btn-confirm" @click="confirmDelete">删除</button>
				</div>
			</div>
		</div>
	</div>
</template>

<script>
import { agentChatStream, createSession, updateSession, deleteSession, getAgentSessions, getSessionMessages, createMessage } from '@/api/agent';
import MarkdownIt from 'markdown-it';

export default {
	name: "Agent",
	data() {
		return {
			md: new MarkdownIt(),
			activeSession: true,
			activeSessionId: null,
			sessionName: '新会话',
			messages: [],
			inputMessage: "",
			isGenerating: false,
			userHasScrolledUp: false,
			showHistoryDialog: false,
			historySessions: [],
			searchKeyword: '',
			editingSessionId: null,
			editingSessionName: '',
			showDeleteDialog: false,
			sessionToDelete: null
		}
	},
	computed: {
		filteredHistorySessions() {
			if (!this.searchKeyword) {
				return this.historySessions;
			}
			const keyword = this.searchKeyword.toLowerCase();
			return this.historySessions.filter(session =>
				(session.name || '').toLowerCase().includes(keyword)
			);
		}
	},
	mounted() {
		this.loadHistorySessions();
		document.addEventListener('click', this.handleClickOutside);
	},
	beforeDestroy() {
		document.removeEventListener('click', this.handleClickOutside);
	},
	methods: {
		handleClickOutside(event) {
			if (!this.showHistoryDialog) return;

			const header = this.$refs.headerRef;
			const dropdown = this.$refs.dropdownRef;

			if (header && dropdown &&
				!header.contains(event.target) &&
				!dropdown.contains(event.target)) {
				this.showHistoryDialog = false;
			}
		},
		scrollToBottom() {
			this.$nextTick(() => {
				if (this.$refs.chatArea) {
					const chatArea = this.$refs.chatArea;
					if (!this.userHasScrolledUp || !this.isGenerating) {
						chatArea.scrollTop = chatArea.scrollHeight;
					}
				}
			});
		},
		handleChatScroll() {
			if (this.$refs.chatArea) {
				const chatArea = this.$refs.chatArea;
				const isAtBottom = chatArea.scrollHeight - chatArea.scrollTop - chatArea.clientHeight < 50;
				this.userHasScrolledUp = !isAtBottom;
			}
		},
		toggleHistoryDialog() {
			this.showHistoryDialog = !this.showHistoryDialog;
			if (this.showHistoryDialog) {
				this.loadHistorySessions();
			}
		},
		createNewSession() {
			this.activeSessionId = null;
			this.sessionName = '新会话';
			this.messages = [];
			this.inputMessage = '';
		},
		async loadHistorySessions() {
			try {
				const response = await getAgentSessions();
				if (response && (response.status >= 200 && response.status < 300 || response.code === '200')) {
					let data = [];
					if (response.data) {
						if (Array.isArray(response.data)) {
							data = response.data;
						} else if (Array.isArray(response.data.data)) {
							data = response.data.data;
						}
					} else if (Array.isArray(response.data)) {
						data = response.data;
					}
					this.historySessions = data.map(item => ({
						id: item.id,
						name: item.title || '新会话',
						create_time: item.created_at,
						update_time: item.updated_at
					}));
					// 如果没有当前选中的会话且有历史会话，自动选择最新的会话
					if (!this.activeSessionId && this.historySessions.length > 0) {
						this.selectHistorySession(this.historySessions[0]);
					}
				}
			} catch (error) {
				console.error('获取历史会话列表失败:', error);
				this.historySessions = [];
			}
		},
		async selectHistorySession(session) {
			if (this.editingSessionId) return;
			this.activeSessionId = session.id;
			this.sessionName = session.name || '新会话';
			this.showHistoryDialog = false;
			try {
				const response = await getSessionMessages(session.id);
				if (response && (response.status >= 200 && response.status < 300 || response.code === '200')) {
					let data = [];
					if (response.data) {
						if (Array.isArray(response.data)) {
							data = response.data;
						} else if (Array.isArray(response.data.data)) {
							data = response.data.data;
						}
					} else if (Array.isArray(response.data)) {
						data = response.data;
					}
					this.messages = data.map(msg => ({
						isUser: msg.role === 'user',
						content: msg.content,
						steps: msg.role === 'assistant' && msg.sources && msg.sources.length > 0
							? msg.sources
							: (msg.role === 'assistant' ? [] : undefined)
					}));
				} else {
					this.messages = [];
				}
			} catch (error) {
				console.error('获取会话消息失败:', error);
				this.messages = [];
			}
			this.$nextTick(() => this.scrollToBottom());
		},
		startEditSession(session) {
			this.editingSessionId = session.id;
			this.editingSessionName = session.name || '';
		},
		async saveSessionName(session) {
			if (this.editingSessionId !== session.id) return;
			const newName = this.editingSessionName.trim();
			session.name = newName || '新会话';
			if (this.activeSessionId === session.id) {
				this.sessionName = session.name;
			}
			this.editingSessionId = null;
			this.editingSessionName = '';
			try {
				await updateSession(session.id, { title: session.name });
			} catch (error) {
				console.error('更新会话名称失败:', error);
			}
		},
		async deleteSession(session) {
			this.sessionToDelete = session;
			this.showDeleteDialog = true;
		},
		cancelDelete() {
			this.showDeleteDialog = false;
			this.sessionToDelete = null;
		},
		async confirmDelete() {
			const session = this.sessionToDelete;
			if (!session) return;
			this.showDeleteDialog = false;
			const wasActive = this.activeSessionId === session.id;
			try {
				await deleteSession(session.id);
			} catch (error) {
				console.error('删除会话失败:', error);
			}
			const index = this.historySessions.findIndex(s => s.id === session.id);
			if (index > -1) {
				this.historySessions.splice(index, 1);
			}
			if (wasActive) {
				if (this.historySessions.length > 0) {
					const latestSession = this.historySessions.reduce((latest, s) => {
						const sTime = s.create_time ? new Date(s.create_time).getTime() : 0;
						const lTime = latest.create_time ? new Date(latest.create_time).getTime() : 0;
						return sTime > lTime ? s : latest;
					});
					await this.selectHistorySession(latestSession);
				} else {
					this.activeSessionId = null;
					this.sessionName = '新会话';
					this.messages = [];
				}
			}
			this.sessionToDelete = null;
		},
		async sendMessage() {
			if (!this.inputMessage.trim()) return;

			const question = this.inputMessage;

			if (this.messages.length === 0) {
				this.sessionName = question.substring(0, 50) + (question.length > 50 ? '...' : '');
				await this.createAgentSession(question);
			}

			const userMessage = {
				isUser: true,
				content: question
			};
			this.messages.push(userMessage);

			if (this.activeSessionId) {
				createMessage({
					knowledge_base_id: -1,
					role: 'user',
					content: question,
					session_id: this.activeSessionId
				}).catch(() => {});
			}

			this.scrollToBottom();

			const aiMessage = {
				isUser: false,
				content: '',
				steps: [],
				done: false
			};
			this.messages.push(aiMessage);

			this.isGenerating = true;
			this.userHasScrolledUp = false;
			this.callChatApi(question, aiMessage);

			this.inputMessage = "";
		},
		async createAgentSession(firstQuestion) {
			try {
				const response = await createSession({
					knowledge_base_id: -1,
					title: firstQuestion.substring(0, 50) + (firstQuestion.length > 50 ? '...' : '')
				});
				if (response && response.status >= 200 && response.status < 300) {
					const data = response.data?.data || response.data;
					const newSession = {
						id: data.id,
						name: data.title || '新会话',
						create_time: data.created_at
					};
					this.activeSessionId = data.id;
					this.historySessions.unshift(newSession);
				}
			} catch (error) {
				console.error('创建会话失败:', error);
			}
		},
		async callChatApi(question, aiMessage) {
			try {
				let conversationHistory = [];
				for (const m of this.messages) {
					if (m === aiMessage) continue;
					if (!m.content) continue;
					conversationHistory.push({
						role: m.isUser ? "user" : "assistant",
						content: m.content
					});
				}
				conversationHistory = conversationHistory.slice(conversationHistory.length - 100, conversationHistory.length);

				await agentChatStream(question, this.activeSessionId, conversationHistory, null, (data) => {
					if (data && data.type) {
						switch (data.type) {
							case 'thought':
								if (data.content) {
									aiMessage.steps = aiMessage.steps || [];
									aiMessage.steps.push({
										id: aiMessage.steps.length + 1,
										type: 'thought',
										content: data.content
									});
								}
								break;
							case 'action':
								if (data.tool_name || data.content) {
									aiMessage.steps = aiMessage.steps || [];
									let actionContent = data.content || '';
									if (data.tool_name) {
										const params = data.parameters || data.tool_input;
										let paramsStr = '';
										if (params) {
											if (typeof params === 'object') {
												const paramEntries = Object.entries(params).map(([k, v]) => `${k}: ${typeof v === 'string' ? v : JSON.stringify(v)}`);
												paramsStr = `（${paramEntries.join(', ')}）`;
											} else {
												paramsStr = `（${params}）`;
											}
										}
										actionContent = `[调用工具: ${data.tool_name}${paramsStr}]`;
									}
									aiMessage.steps.push({
										id: aiMessage.steps.length + 1,
										type: 'action',
										content: actionContent
									});
								}
								break;
							case 'observation':
								if (data.content !== undefined || data.result !== undefined || data.error !== undefined) {
									aiMessage.steps = aiMessage.steps || [];
									let obsContent = '';
									let expandableData = null;
									if (data.error) {
										obsContent = `[执行失败]\n错误: ${data.error}`;
									} else {
										const result = data.result !== undefined ? data.result : data.content;
										if (typeof result === 'object' && result !== null) {
											obsContent = `[执行成功]\n结果: ${result.query || '[对象]'}`;
											expandableData = result;
										} else {
											obsContent = `[执行成功]\n结果: ${result}`;
										}
									}
									aiMessage.steps.push({
										id: aiMessage.steps.length + 1,
										type: 'observation',
										content: obsContent,
										expandableData: expandableData
									});
								}
								break;
							case 'tool':
							case 'tool_result':
								if (data.content) {
									aiMessage.steps = aiMessage.steps || [];
									aiMessage.steps.push({
										id: aiMessage.steps.length + 1,
										type: data.type === 'tool' ? 'action' : 'observation',
										content: data.content
									});
								}
								break;
							case 'step_done':
								break;
							case 'summary':
								if (data.content) {
									aiMessage.steps = aiMessage.steps || [];
									aiMessage.steps.push({
										id: aiMessage.steps.length + 1,
										type: 'summary',
										content: data.content
									});
								}
								break;
							case 'text':
								if (data.content) {
									let content = data.content;
									content = content.replace(/`(https?:\/\/[^`]+)`/g, '<a href="$1" target="_blank" rel="noopener noreferrer">$1</a>');
									aiMessage.content = content;
								}
								break;
							case 'error':
								if (data.error) {
									aiMessage.content += data.error;
								}
								if (data.done) {
									aiMessage.done = true;
									this.isGenerating = false;
								}
								break;
						}

						this.$forceUpdate();
						this.scrollToBottom();
						return;
					}

					// 旧格式兼容
					let remaining = String(data);
					aiMessage.content += remaining;

					this.$forceUpdate();
					this.scrollToBottom();
				});

				if (this.activeSessionId) {
					createMessage({
						knowledge_base_id: -1,
						role: 'assistant',
						content: aiMessage.content,
						sources: aiMessage.steps && aiMessage.steps.length > 0 ? aiMessage.steps : null,
						session_id: this.activeSessionId
					}).catch(() => {});
				}

				aiMessage.done = true;
				this.isGenerating = false;
				this.$nextTick(() => {
					this.$refs.messageInput?.focus();
				});
			} catch (error) {
				console.error('调用API失败:', error);
				aiMessage.content = '抱歉，发生了错误，请稍后再试。';
				aiMessage.done = true;
				this.$forceUpdate();
				this.scrollToBottom();
				this.isGenerating = false;
			}
		},
		parseMarkdown(text) {
			return this.md.render(text);
		},
		getStepLabel(type) {
			const labels = {
				'thought': '思考内容',
				'action': '调用工具',
				'observation': '执行结果',
				'step_done': '完成',
				'think': '思考',
				'step': '步骤',
				'tool': '工具',
				'output': '输出',
				'summary': '总结'
			};
			return labels[type] || '步骤';
		},
		getStepStatus(type) {
			const status = {
				'thought': '思考中',
				'action': '执行中',
				'observation': '已完成',
				'step_done': '完成',
				'think': '思考中',
				'step': '步骤',
				'tool': '工具',
				'output': '输出',
				'summary': '总结'
			};
			return status[type] || '步骤';
		},
		toggleExpand(step) {
			this.$set(step, '_expanded', !step._expanded);
		},
		formatDate(dateStr) {
			if (!dateStr) return '';
			const date = new Date(dateStr);
			const year = date.getFullYear();
			const month = String(date.getMonth() + 1).padStart(2, '0');
			const day = String(date.getDate()).padStart(2, '0');
			const hour = String(date.getHours()).padStart(2, '0');
			const minute = String(date.getMinutes()).padStart(2, '0');
			return `${year}-${month}-${day} ${hour}:${minute}`;
		}
	}
}
</script>

<style scoped>
.m-top {
	margin-top: 20px;
	margin-bottom: 20px;
}

.m-deepseek-container {
	display: flex;
	height: 85vh;
	border: 1px solid #e1e5e9;
	border-radius: 8px;
	overflow: hidden;
	box-shadow: 0 2px 10px rgba(0, 0, 0, 0.1);
}

.m-deepseek-main {
	flex: 1;
	display: flex;
	flex-direction: column;
	background-color: #ffffff;
	position: relative;
}

.m-agent-header {
	display: flex;
	justify-content: space-between;
	align-items: center;
	padding: 12px 20px;
	border-bottom: 1px solid #e1e5e9;
	background-color: #f8f9fa;
	position: relative;
}

.m-agent-title {
	font-size: 16px;
	font-weight: 600;
	color: #333;
}

.m-agent-actions {
	display: flex;
	gap: 8px;
}

.m-agent-btn {
	background: none;
	border: 1px solid #e1e5e9;
	border-radius: 4px;
	padding: 6px 10px;
	cursor: pointer;
	font-size: 16px;
	transition: all 0.2s ease;
}

.m-agent-btn:hover {
	background-color: #e9ecef;
	border-color: #d0d7de;
}

.m-deepseek-chat-area {
	flex: 1;
	overflow-y: auto;
	padding: 20px;
}

.m-deepseek-message {
	margin-bottom: 20px;
}

.m-deepseek-message-content {
	max-width: 80%;
}

.m-deepseek-message-content.user {
	margin-left: auto;
}

.m-deepseek-message-header {
	font-size: 12px;
	color: #666;
	margin-bottom: 6px;
}

.m-deepseek-message-body {
	padding: 12px 16px;
	border-radius: 8px;
	line-height: 1.5;
}

.m-deepseek-message-content:not(.user) .m-deepseek-message-body {
	background-color: #f1f3f4;
	color: #333;
	border-bottom-left-radius: 2px;
}

.m-deepseek-message-content.user .m-deepseek-message-body {
	background-color: #2196f3;
	color: #ffffff;
	border-bottom-right-radius: 2px;
}

.m-deepseek-thinking {
	display: flex;
	align-items: center;
	gap: 4px;
}

.m-thinking-text {
	color: #666;
}

.m-thinking-dots {
	display: inline-flex;
	gap: 3px;
}

.m-dot {
	width: 6px;
	height: 6px;
	background-color: #666;
	border-radius: 50%;
	animation: m-dot-bounce 1.4s infinite ease-in-out both;
}

.m-dot:nth-child(1) { animation-delay: -0.32s; }
.m-dot:nth-child(2) { animation-delay: -0.16s; }
.m-dot:nth-child(3) { animation-delay: 0s; }

@keyframes m-dot-bounce {
	0%, 80%, 100% { transform: scale(0.6); opacity: 0.4; }
	40% { transform: scale(1); opacity: 1; }
}

/* 步骤时间线样式 */
.m-step-timeline {
	padding: 12px 0;
}

.m-step-item {
	display: flex;
	margin-bottom: 16px;
}

.m-step-indicator {
	display: flex;
	flex-direction: column;
	align-items: center;
	margin-right: 16px;
	flex-shrink: 0;
}

.m-step-circle {
	width: 32px;
	height: 32px;
	border-radius: 50%;
	background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
	display: flex;
	align-items: center;
	justify-content: center;
	box-shadow: 0 2px 8px rgba(102, 126, 234, 0.3);
}

.m-step-number {
	color: white;
	font-size: 14px;
	font-weight: 600;
}

.m-step-line {
	width: 2px;
	flex: 1;
	background: linear-gradient(180deg, #667eea 0%, #e0e0e0 100%);
	min-height: 30px;
	margin-top: 4px;
}

.m-step-card {
	flex: 1;
	border-radius: 12px;
	border: 1px solid #e8ecf4;
	background: #ffffff;
	overflow: hidden;
	box-shadow: 0 2px 8px rgba(0, 0, 0, 0.06);
	transition: all 0.3s ease;
}

.m-step-card:hover {
	box-shadow: 0 4px 16px rgba(0, 0, 0, 0.1);
	transform: translateY(-2px);
}

.m-step-status {
	padding: 8px 16px;
	background: #f8f9fa;
	border-bottom: 1px solid #e8ecf4;
}

.m-step-status-text {
	font-size: 12px;
	font-weight: 500;
	letter-spacing: 0.5px;
}

.m-step-card-thought .m-step-status {
	background: linear-gradient(135deg, #fff3e0 0%, #ffe0b2 100%);
	color: #e65100;
}

.m-step-card-action .m-step-status {
	background: linear-gradient(135deg, #e3f2fd 0%, #90caf9 100%);
	color: #1565c0;
}

.m-step-card-observation .m-step-status {
	background: linear-gradient(135deg, #e8f5e9 0%, #a5d6a7 100%);
	color: #2e7d32;
}

.m-step-card-tool .m-step-status {
	background: linear-gradient(135deg, #f3e5f5 0%, #ce93d8 100%);
	color: #6a1b9a;
}

.m-step-card-summary .m-step-status {
	background: linear-gradient(135deg, #e3f2fd 0%, #64b5f6 100%);
	color: #1565c0;
}

.m-step-card-content {
	padding: 12px 16px;
}

.m-step-type-label {
	font-size: 11px;
	font-weight: 600;
	color: #94a3b8;
	text-transform: uppercase;
	letter-spacing: 0.5px;
	margin-bottom: 8px;
}

.m-step-detail {
	font-size: 14px;
	line-height: 1.6;
	color: #4a5568;
	word-break: break-word;
}

.m-step-detail pre {
	background: #f7fafc;
	padding: 10px;
	border-radius: 6px;
	margin: 8px 0;
	overflow-x: auto;
	font-size: 12px;
}

.m-step-detail code {
	background: #f7fafc;
	padding: 2px 6px;
	border-radius: 4px;
	font-size: 12px;
	font-family: 'Courier New', Courier, monospace;
}

.m-expandable-container {
	margin-top: 12px;
	border: 1px solid #e8ecf4;
	border-radius: 8px;
	overflow: hidden;
}

.m-expandable-header {
	display: flex;
	align-items: center;
	gap: 8px;
	padding: 10px 14px;
	background: #f8f9fa;
	cursor: pointer;
	user-select: none;
	font-size: 13px;
	color: #1565c0;
}

.m-expandable-header:hover {
	background: #e3f2fd;
}

.m-expandable-icon {
	font-size: 10px;
}

.m-expandable-title {
	font-weight: 500;
}

.m-expandable-content {
	padding: 12px;
	background: #ffffff;
	max-height: 300px;
	overflow-y: auto;
}

.m-result-item {
	padding: 10px 0;
	border-bottom: 1px solid #f0f0f0;
}

.m-result-item:last-child {
	border-bottom: none;
}

.m-result-title {
	font-size: 13px;
	font-weight: 500;
	margin-bottom: 4px;
}

.m-result-title a {
	color: #1565c0;
	text-decoration: none;
}

.m-result-title a:hover {
	text-decoration: underline;
}

.m-result-snippet {
	font-size: 12px;
	color: #666;
	line-height: 1.5;
}

.m-deepseek-message-body h1, .m-deepseek-message-body h2, .m-deepseek-message-body h3,
.m-deepseek-message-body h4, .m-deepseek-message-body h5, .m-deepseek-message-body h6 {
	margin: 10px 0;
	font-weight: 600;
}

.m-deepseek-message-body h1 { font-size: 20px; }
.m-deepseek-message-body h2 { font-size: 18px; }
.m-deepseek-message-body h3 { font-size: 16px; }

.m-deepseek-message-body ul, .m-deepseek-message-body ol {
	margin: 10px 0;
	padding-left: 20px;
}

.m-deepseek-message-body li { margin: 5px 0; }

.m-deepseek-message-body blockquote {
	border-left: 4px solid #2196f3;
	padding-left: 10px;
	margin: 10px 0;
	color: #666;
}

.m-deepseek-message-body a {
	color: #2196f3;
	text-decoration: none;
}

.m-deepseek-message-body a:hover {
	text-decoration: underline;
}

.m-deepseek-input-area {
	padding: 20px;
	border-top: 1px solid #e1e5e9;
	background-color: #f8f9fa;
}

.m-deepseek-input-container {
	display: flex;
	gap: 10px;
	align-items: flex-end;
}

.m-deepseek-input-wrapper {
	flex: 1;
	border: 1px solid #e1e5e9;
	border-radius: 8px;
	background-color: #ffffff;
	overflow: hidden;
}

.m-deepseek-input {
	width: 100%;
	padding: 12px 16px;
	border: none;
	resize: none;
	font-size: 14px;
	line-height: 1.5;
	background-color: #ffffff;
	min-height: 80px;
	max-height: 200px;
	overflow-y: auto;
}

.m-deepseek-input:focus {
	outline: none;
}

.m-deepseek-send-btn {
	background-color: #2196f3;
	color: #ffffff;
	border: none;
	border-radius: 50%;
	width: 40px;
	height: 40px;
	cursor: pointer;
	display: flex;
	align-items: center;
	justify-content: center;
	transition: background-color 0.2s ease;
	flex-shrink: 0;
}

.m-deepseek-send-btn:hover {
	background-color: #1976d2;
}

.m-deepseek-send-btn:disabled {
	background-color: #bdbdbd;
	cursor: not-allowed;
}

.m-deepseek-send-btn-text {
	font-size: 16px;
	font-weight: bold;
}

.m-deepseek-chat-area::-webkit-scrollbar,
.m-deepseek-input::-webkit-scrollbar {
	width: 6px;
}

.m-deepseek-chat-area::-webkit-scrollbar-track,
.m-deepseek-input::-webkit-scrollbar-track {
	background: #f1f1f1;
	border-radius: 3px;
}

.m-deepseek-chat-area::-webkit-scrollbar-thumb,
.m-deepseek-input::-webkit-scrollbar-thumb {
	background: #c1c1c1;
	border-radius: 3px;
}

.m-deepseek-chat-area::-webkit-scrollbar-thumb:hover,
.m-deepseek-input::-webkit-scrollbar-thumb:hover {
	background: #a8a8a8;
}

.m-history-dropdown {
	position: absolute;
	top: 100%;
	right: 20px;
	z-index: 100;
	background-color: #ffffff;
	border-radius: 8px;
	width: 320px;
	max-height: 400px;
	overflow: hidden;
	display: flex;
	flex-direction: column;
	box-shadow: 0 4px 20px rgba(0, 0, 0, 0.15);
	border: 1px solid #e1e5e9;
}

.m-history-search {
	padding: 12px;
	border-bottom: 1px solid #e1e5e9;
}

.m-history-search-input {
	width: 100%;
	padding: 8px 12px;
	border: 1px solid #e1e5e9;
	border-radius: 4px;
	font-size: 14px;
	box-sizing: border-box;
}

.m-history-search-input:focus {
	outline: none;
	border-color: #2196f3;
}

.m-history-list {
	flex: 1;
	overflow-y: auto;
}

.m-history-empty {
	text-align: center;
	padding: 20px;
	color: #999;
	font-size: 14px;
}

.m-history-item {
	display: flex;
	justify-content: space-between;
	align-items: center;
	padding: 10px 12px;
	border-bottom: 1px solid #e1e5e9;
	cursor: pointer;
	transition: background-color 0.2s ease;
}

.m-history-item:hover {
	background-color: #f8f9fa;
}

.m-history-item.active {
	background-color: #e3f2fd;
}

.m-history-item-content {
	flex: 1;
	overflow: hidden;
}

.m-history-name {
	font-size: 14px;
	color: #333;
	display: block;
	overflow: hidden;
	text-overflow: ellipsis;
	white-space: nowrap;
}

.m-history-edit-input {
	width: 100%;
	padding: 4px 8px;
	border: 1px solid #2196f3;
	border-radius: 4px;
	font-size: 14px;
	outline: none;
}

.m-history-item-actions {
	display: flex;
	gap: 4px;
	opacity: 0;
	transition: opacity 0.2s ease;
}

.m-history-item:hover .m-history-item-actions {
	opacity: 1;
}

.m-history-action-btn {
	background: none;
	border: none;
	padding: 4px 6px;
	border-radius: 4px;
	cursor: pointer;
	font-size: 12px;
	color: #666;
}

.m-history-action-btn:hover {
	background-color: #d0d7de;
}

.m-history-delete-btn:hover {
	background-color: #f8d7da;
	color: #dc3545;
}

.m-delete-dialog-overlay {
	position: fixed;
	top: 0;
	left: 0;
	right: 0;
	bottom: 0;
	background-color: rgba(0, 0, 0, 0.5);
	display: flex;
	align-items: center;
	justify-content: center;
	z-index: 1000;
}

.m-delete-dialog {
	background-color: #ffffff;
	border-radius: 8px;
	width: 360px;
	box-shadow: 0 4px 20px rgba(0, 0, 0, 0.2);
	overflow: hidden;
}

.m-delete-dialog-header {
	padding: 16px 20px;
	font-size: 16px;
	font-weight: 600;
	color: #333;
	border-bottom: 1px solid #e1e5e9;
}

.m-delete-dialog-body {
	padding: 20px;
}

.m-delete-dialog-body p {
	margin: 0 0 8px 0;
	font-size: 14px;
	color: #333;
}

.m-delete-dialog-tip {
	color: #dc3545;
	font-size: 12px !important;
}

.m-delete-dialog-footer {
	padding: 12px 20px;
	display: flex;
	justify-content: flex-end;
	gap: 10px;
	border-top: 1px solid #e1e5e9;
	background-color: #f8f9fa;
}

.m-delete-dialog-btn {
	padding: 8px 16px;
	border-radius: 4px;
	font-size: 14px;
	cursor: pointer;
	transition: all 0.2s ease;
}

.m-delete-dialog-btn-cancel {
	background-color: #ffffff;
	border: 1px solid #e1e5e9;
	color: #333;
}

.m-delete-dialog-btn-cancel:hover {
	background-color: #f8f9fa;
}

.m-delete-dialog-btn-confirm {
	background-color: #dc3545;
	border: 1px solid #dc3545;
	color: #ffffff;
}

.m-delete-dialog-btn-confirm:hover {
	background-color: #c82333;
	border-color: #c82333;
}

@media (max-width: 768px) {
	.m-deepseek-container {
		height: 90vh;
	}

	.m-deepseek-message-content {
		max-width: 90%;
	}

	.m-history-dropdown {
		width: 280px;
		right: 10px;
	}
}
</style>
