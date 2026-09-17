<template>
	<div class="sidebar" :class="{'sidebar-open': opened}">
		<!-- Logo -->
		<div class="sidebar-logo">
			<span class="logo-badge">
				<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
					<path d="M21 11.5a8.38 8.38 0 0 1-.9 3.8 8.5 8.5 0 0 1-7.6 4.7 8.38 8.38 0 0 1-3.8-.9L3 21l1.9-5.7a8.38 8.38 0 0 1-.9-3.8 8.5 8.5 0 0 1 4.7-7.6 8.38 8.38 0 0 1 3.8-.9h.5a8.48 8.48 0 0 1 8 8v.5z"/>
				</svg>
			</span>
			<span class="logo-text">{{ title }}</span>
		</div>

		<!-- 新对话 -->
		<button class="new-chat-btn" @click="newChat">
			<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
				<path d="M12 5v14M5 12h14"/>
			</svg>
			<span>新对话</span>
		</button>

		<!-- 搜索 -->
		<div class="sidebar-search">
			<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
				<circle cx="11" cy="11" r="8"/>
				<line x1="21" y1="21" x2="16.65" y2="16.65"/>
			</svg>
			<input v-model="keyword" placeholder="搜索会话"/>
		</div>

		<!-- 历史会话 -->
		<div class="history-scroll">
			<!-- 智能体:平铺会话列表 -->
			<template v-if="isAgent">
				<div class="group-label">会话历史</div>
				<div class="history-empty" v-if="filteredAgentSessions.length === 0">暂无会话</div>
				<div
					v-for="session in filteredAgentSessions"
					:key="session.id"
					class="history-item"
					:class="{active: session.id === activeAgentId}"
				>
					<template v-if="isRenaming('agent', session.id)">
						<input
							ref="renameInput"
							class="rename-input"
							v-model="renamingName"
							@keyup.enter="submitRename"
							@keyup.esc="cancelRename"
							@blur="submitRename"
							@click.stop
						/>
					</template>
					<template v-else>
						<span class="history-name" :title="session.name" @click="selectAgent(session)">{{ session.name || '新会话' }}</span>
						<span class="history-actions">
							<button title="重命名" @click.stop="startRename({type: 'agent', session})">
								<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
									<path d="M17 3a2.828 2.828 0 1 1 4 4L7.5 20.5 2 22l1.5-5.5L17 3z"/>
								</svg>
							</button>
							<button title="删除" @click.stop="removeAgent(session)">
								<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
									<polyline points="3 6 5 6 21 6"/>
									<path d="M19 6v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6m3 0V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2"/>
								</svg>
							</button>
						</span>
					</template>
				</div>
			</template>

			<!-- 知识问答:按知识库分组 -->
			<template v-else>
				<div class="group-head">
					<span class="group-label">知识库</span>
					<button class="group-add" title="新建知识库" @click="newKb">
						<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
							<path d="M12 5v14M5 12h14"/>
						</svg>
					</button>
				</div>
				<div class="history-empty" v-if="filteredKbs.length === 0">暂无知识库,点击右上角 + 新建</div>
				<div v-for="kb in filteredKbs" :key="kb.id" class="kb-group">
					<!-- 知识库行 -->
					<div class="kb-head" @click="toggleKb(kb.id)">
						<span class="kb-chevron" :class="{open: isKbOpen(kb.id)}">
							<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
								<polyline points="9 18 15 12 9 6"/>
							</svg>
						</span>
						<span class="kb-name" :title="kb.name">{{ kb.name }}</span>
						<span class="history-actions">
							<button title="新建会话" @click.stop="addSession(kb)">
								<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
									<path d="M12 5v14M5 12h14"/>
								</svg>
							</button>
							<button title="上传文档" @click.stop="uploadDoc(kb)">
								<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
									<path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4"/>
									<polyline points="17 8 12 3 7 8"/>
									<line x1="12" y1="3" x2="12" y2="15"/>
								</svg>
							</button>
							<button title="编辑知识库" @click.stop="editKb(kb)">
								<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
									<path d="M17 3a2.828 2.828 0 1 1 4 4L7.5 20.5 2 22l1.5-5.5L17 3z"/>
								</svg>
							</button>
							<button title="删除知识库" @click.stop="deleteKb(kb)">
								<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
									<polyline points="3 6 5 6 21 6"/>
									<path d="M19 6v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6m3 0V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2"/>
								</svg>
							</button>
						</span>
					</div>
					<!-- 会话列表 -->
					<div class="kb-sessions" v-show="isKbOpen(kb.id)">
						<div class="history-empty small" v-if="!kb.sessions || kb.sessions.length === 0">暂无会话</div>
						<div
							v-for="(session, idx) in (kb.sessions || [])"
							:key="session.id"
							class="history-item sub"
							:class="{active: session.id === activeQaSession}"
						>
							<template v-if="isRenaming('qa', session.id)">
								<input
									ref="renameInput"
									class="rename-input"
									v-model="renamingName"
									@keyup.enter="submitRename"
									@keyup.esc="cancelRename"
									@blur="submitRename"
									@click.stop
								/>
							</template>
							<template v-else>
								<span class="history-name" :title="session.name" @click="selectQa(session)">{{ session.name || ('会话 ' + (idx + 1)) }}</span>
								<span class="history-actions">
									<button title="重命名" @click.stop="startRename({type: 'qa', session, kb})">
										<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
											<path d="M17 3a2.828 2.828 0 1 1 4 4L7.5 20.5 2 22l1.5-5.5L17 3z"/>
										</svg>
									</button>
									<button title="删除会话" @click.stop="deleteQaSession(kb, session)">
										<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
											<polyline points="3 6 5 6 21 6"/>
											<path d="M19 6v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6m3 0V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2"/>
										</svg>
									</button>
								</span>
							</template>
						</div>
					</div>
				</div>
			</template>
		</div>

		<!-- 底部提示 -->
		<div class="sidebar-footer">AI 生成内容仅供参考</div>
	</div>
</template>

<script>
	import { bus, chatState } from '@/util/chat-bus'
	import defaultSettings from '@/settings'

	export default {
		name: 'Sidebar',
		props: {
			opened: {
				type: Boolean,
				default: false
			}
		},
		data() {
			return {
				keyword: '',
				renaming: null, // {type: 'agent'|'qa', session, kb?}
				renamingName: '',
				collapsedKbs: [] // 收起的知识库(默认全部展开)
			}
		},
		computed: {
			title() {
				return defaultSettings.title
			},
			isAgent() {
				return this.$route.name === 'agent'
			},
			activeAgentId() {
				return chatState.agent.activeId
			},
			activeQaSession() {
				return chatState.qa.activeSession
			},
			agentSessions() {
				return chatState.agent.sessions || []
			},
			knowledgeBases() {
				return chatState.qa.knowledgeBases || []
			},
			filteredAgentSessions() {
				const kw = this.keyword.trim().toLowerCase()
				if (!kw) return this.agentSessions
				return this.agentSessions.filter(s => (s.name || '').toLowerCase().includes(kw))
			},
			filteredKbs() {
				const kw = this.keyword.trim().toLowerCase()
				if (!kw) return this.knowledgeBases
				return this.knowledgeBases
					.map(kb => {
						const kbMatch = (kb.name || '').toLowerCase().includes(kw)
						const sessions = (kb.sessions || []).filter(s => (s.name || '').toLowerCase().includes(kw))
						if (kbMatch || sessions.length > 0) {
							return Object.assign({}, kb, {sessions: kbMatch ? (kb.sessions || []) : sessions})
						}
						return null
					})
					.filter(Boolean)
			}
		},
		methods: {
			newChat() {
				bus.$emit(this.isAgent ? 'agent:new' : 'qa:new-session')
			},
			newKb() {
				bus.$emit('qa:new-kb')
			},
			selectAgent(session) {
				if (this.renaming) return
				bus.$emit('agent:select', session)
			},
			selectQa(session) {
				if (this.renaming) return
				bus.$emit('qa:select', session.id)
			},
			removeAgent(session) {
				bus.$emit('agent:delete', session)
			},
			addSession(kb) {
				bus.$emit('qa:add-session', kb.id)
			},
			uploadDoc(kb) {
				bus.$emit('qa:upload', kb.id)
			},
			editKb(kb) {
				bus.$emit('qa:edit-kb', kb)
			},
			deleteKb(kb) {
				bus.$emit('qa:delete-kb', kb.id, kb.name)
			},
			deleteQaSession(kb, session) {
				const idx = (kb.sessions || []).indexOf(session) + 1
				bus.$emit('qa:delete-session', kb.id, session.id, session.name || idx)
			},
			isKbOpen(id) {
				return this.keyword.trim() !== '' || !this.collapsedKbs.includes(id)
			},
			toggleKb(id) {
				const i = this.collapsedKbs.indexOf(id)
				if (i > -1) {
					this.collapsedKbs.splice(i, 1)
				} else {
					this.collapsedKbs.push(id)
				}
			},
			isRenaming(type, id) {
				return !!this.renaming && this.renaming.type === type && this.renaming.session.id === id
			},
			startRename(payload) {
				this.renaming = payload
				this.renamingName = payload.session.name || ''
				this.$nextTick(() => {
					if (this.$refs.renameInput) {
						const el = Array.isArray(this.$refs.renameInput) ? this.$refs.renameInput[0] : this.$refs.renameInput
						el && el.focus()
					}
				})
			},
			submitRename() {
				if (!this.renaming) return
				const {type, session, kb} = this.renaming
				const name = this.renamingName.trim()
				this.renaming = null
				if (type === 'agent') {
					bus.$emit('agent:rename-save', session, name || '新会话')
				} else {
					bus.$emit('qa:rename-session', kb.id, session.id, name)
				}
			},
			cancelRename() {
				this.renaming = null
			}
		}
	}
</script>

<style scoped>
	.sidebar {
		display: flex;
		flex-direction: column;
		width: 240px;
		height: 100%;
		background-color: #202123;
		color: #ececf1;
		user-select: none;
	}

	.sidebar-logo {
		display: flex;
		align-items: center;
		gap: 10px;
		padding: 16px 16px 12px;
	}

	.logo-badge {
		display: inline-flex;
		align-items: center;
		justify-content: center;
		width: 30px;
		height: 30px;
		border-radius: 8px;
		background: linear-gradient(135deg, #10a37f, #0b7a61);
		color: #ffffff;
		flex-shrink: 0;
	}

	.logo-badge svg {
		width: 17px;
		height: 17px;
	}

	.logo-text {
		font-size: 15px;
		font-weight: 600;
		letter-spacing: 0.3px;
	}

	.new-chat-btn {
		display: flex;
		align-items: center;
		gap: 9px;
		margin: 2px 10px 8px;
		padding: 9px 12px;
		background: none;
		border: 1px solid rgba(255, 255, 255, 0.18);
		border-radius: 10px;
		color: #ececf1;
		font-size: 14px;
		cursor: pointer;
		transition: background-color 0.15s ease;
	}

	.new-chat-btn:hover {
		background-color: #2a2b32;
	}

	.new-chat-btn svg {
		width: 16px;
		height: 16px;
		flex-shrink: 0;
	}

	.sidebar-search {
		display: flex;
		align-items: center;
		gap: 8px;
		margin: 0 10px 8px;
		padding: 7px 10px;
		background-color: #2a2b32;
		border-radius: 10px;
	}

	.sidebar-search svg {
		width: 14px;
		height: 14px;
		color: #8e8ea0;
		flex-shrink: 0;
	}

	.sidebar-search input {
		flex: 1;
		min-width: 0;
		background: transparent;
		border: none;
		outline: none;
		color: #ececf1;
		font-size: 13px;
	}

	.sidebar-search input::placeholder {
		color: #8e8ea0;
	}

	.history-scroll {
		flex: 1;
		min-height: 0;
		overflow-y: auto;
		padding: 0 10px 10px;
	}

	.history-scroll::-webkit-scrollbar {
		width: 5px;
	}

	.history-scroll::-webkit-scrollbar-thumb {
		background-color: rgba(255, 255, 255, 0.18);
		border-radius: 3px;
	}

	.history-scroll::-webkit-scrollbar-track {
		background: transparent;
	}

	.group-head {
		display: flex;
		align-items: center;
		justify-content: space-between;
		margin: 8px 4px 4px;
	}

	.group-label {
		font-size: 12px;
		color: #8e8ea0;
		padding: 0 4px;
	}

	.group-add {
		display: inline-flex;
		align-items: center;
		justify-content: center;
		width: 22px;
		height: 22px;
		background: none;
		border: none;
		border-radius: 6px;
		color: #b4b4b8;
		cursor: pointer;
	}

	.group-add:hover {
		background-color: #2a2b32;
		color: #ffffff;
	}

	.group-add svg {
		width: 14px;
		height: 14px;
	}

	/* 知识库分组行 */
	.kb-group {
		margin-bottom: 2px;
	}

	.kb-head {
		display: flex;
		align-items: center;
		gap: 6px;
		padding: 7px 8px;
		border-radius: 8px;
		cursor: pointer;
		transition: background-color 0.12s ease;
	}

	.kb-head:hover {
		background-color: #2a2b32;
	}

	.kb-chevron {
		display: inline-flex;
		align-items: center;
		justify-content: center;
		width: 14px;
		height: 14px;
		flex-shrink: 0;
		color: #8e8ea0;
		transition: transform 0.2s ease;
	}

	.kb-chevron.open {
		transform: rotate(90deg);
	}

	.kb-chevron svg {
		width: 12px;
		height: 12px;
	}

	.kb-name {
		flex: 1;
		min-width: 0;
		font-size: 13px;
		color: #c5c5c8;
		overflow: hidden;
		text-overflow: ellipsis;
		white-space: nowrap;
	}

	.kb-sessions {
		margin-left: 14px;
		padding-left: 6px;
		border-left: 1px solid rgba(255, 255, 255, 0.08);
	}

	/* 会话条目 */
	.history-item {
		display: flex;
		align-items: center;
		gap: 6px;
		padding: 8px 10px;
		border-radius: 8px;
		cursor: pointer;
		transition: background-color 0.12s ease;
	}

	.history-item:hover {
		background-color: #2a2b32;
	}

	.history-item.active {
		background-color: #343541;
	}

	.history-item.sub {
		margin-bottom: 1px;
	}

	.history-name {
		flex: 1;
		min-width: 0;
		font-size: 13.5px;
		color: #ececf1;
		overflow: hidden;
		text-overflow: ellipsis;
		white-space: nowrap;
	}

	.history-actions {
		display: flex;
		gap: 2px;
		opacity: 0;
		transition: opacity 0.15s ease;
		flex-shrink: 0;
	}

	.history-item:hover .history-actions,
	.kb-head:hover .history-actions {
		opacity: 1;
	}

	.history-actions button {
		display: inline-flex;
		align-items: center;
		justify-content: center;
		width: 22px;
		height: 22px;
		background: none;
		border: none;
		border-radius: 6px;
		color: #b4b4b8;
		cursor: pointer;
	}

	.history-actions button:hover {
		background-color: #3a3b42;
		color: #ffffff;
	}

	.history-actions button svg {
		width: 13px;
		height: 13px;
	}

	.rename-input {
		flex: 1;
		min-width: 0;
		background-color: #2a2b32;
		border: 1px solid #10a37f;
		border-radius: 6px;
		color: #ececf1;
		font-size: 13px;
		padding: 3px 8px;
		outline: none;
	}

	.history-empty {
		padding: 10px;
		font-size: 12.5px;
		color: #8e8ea0;
	}

	.history-empty.small {
		padding: 6px 10px;
		font-size: 12px;
	}

	.sidebar-footer {
		padding: 12px 16px;
		border-top: 1px solid rgba(255, 255, 255, 0.08);
		font-size: 11.5px;
		color: #8e8ea0;
	}
</style>
