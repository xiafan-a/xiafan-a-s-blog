<template>
	<aside class="msg-nav" :class="{expanded: expanded}">
		<!-- 收起状态:右侧细条,点击展开 -->
		<button class="msg-nav-rail-btn" @click="$emit('toggle')" title="展开问题定位" aria-label="展开问题定位">
			<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
				<line x1="8" y1="6" x2="21" y2="6"/>
				<line x1="8" y1="12" x2="21" y2="12"/>
				<line x1="8" y1="18" x2="21" y2="18"/>
				<line x1="3" y1="6" x2="3.01" y2="6"/>
				<line x1="3" y1="12" x2="3.01" y2="12"/>
				<line x1="3" y1="18" x2="3.01" y2="18"/>
			</svg>
		</button>
		<!-- 展开状态:面板主体 -->
		<div class="msg-nav-body">
			<div class="msg-nav-header">
				<span class="msg-nav-title">问题定位</span>
				<button class="msg-nav-close" @click="$emit('toggle')" title="收起" aria-label="收起">
					<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
						<polyline points="13 17 18 12 13 7"/>
						<polyline points="6 17 11 12 6 7"/>
					</svg>
				</button>
			</div>
			<div class="msg-nav-list">
				<div class="msg-nav-empty" v-if="questions.length === 0">暂无提问</div>
				<div
					v-for="q in questions"
					:key="q.index"
					class="msg-nav-item"
					:class="{active: q.index === activeIndex}"
					:title="q.text"
					@click="$emit('locate', q.index)"
				>
					<span class="msg-nav-seq">{{ String(q.seq).padStart(2, '0') }}</span>
					<span class="msg-nav-text">{{ q.text }}</span>
				</div>
			</div>
		</div>
	</aside>
</template>

<script>
	export default {
		name: 'MessageNav',
		props: {
			// 是否展开(展开=停靠面板,收起=右侧细条)
			expanded: {
				type: Boolean,
				default: false
			},
			// 消息列表(含 isUser 标记)
			messages: {
				type: Array,
				default: () => []
			},
			// 当前滚动位置对应的用户消息索引
			activeIndex: {
				type: Number,
				default: -1
			}
		},
		computed: {
			// 提取所有用户提问:序号 + 去除 HTML 标签后的摘要文本
			questions() {
				const result = []
				let seq = 0
				this.messages.forEach((m, index) => {
					if (!m.isUser) return
					seq++
					const text = String(m.content || '')
						.replace(/<[^>]*>/g, ' ')
						.replace(/\s+/g, ' ')
						.trim()
						.slice(0, 60)
					result.push({index, seq, text: text || '(空内容)'})
				})
				return result
			}
		}
	}
</script>

<style scoped>
	/* 停靠在聊天页右侧的常驻面板:收起=36px 细条,展开=264px 面板 */
	.msg-nav {
		flex-shrink: 0;
		width: 36px;
		display: flex;
		flex-direction: column;
		background: #fafafa;
		border-left: 1px solid #ececf1;
		overflow: hidden;
		transition: width 0.25s ease, background-color 0.25s ease;
	}

	.msg-nav.expanded {
		width: 264px;
		background: #ffffff;
	}

	/* 收起状态细条上的展开按钮 */
	.msg-nav-rail-btn {
		flex-shrink: 0;
		display: inline-flex;
		align-items: center;
		justify-content: center;
		width: 36px;
		height: 36px;
		margin-top: 10px;
		background: none;
		border: none;
		border-radius: 8px;
		color: #8f8f8f;
		cursor: pointer;
		transition: background-color 0.15s ease, color 0.15s ease;
	}

	.msg-nav-rail-btn:hover {
		background-color: #ececec;
		color: #0b7a61;
	}

	.msg-nav-rail-btn svg {
		width: 16px;
		height: 16px;
	}

	.msg-nav.expanded .msg-nav-rail-btn {
		display: none;
	}

	/* 面板主体:固定 264px 宽,收起时被容器裁剪,避免内容随宽度挤压 */
	.msg-nav-body {
		width: 264px;
		flex: 1;
		min-height: 0;
		display: flex;
		flex-direction: column;
	}

	.msg-nav-header {
		display: flex;
		align-items: center;
		justify-content: space-between;
		padding: 10px 12px;
		border-bottom: 1px solid #f0f0f0;
		flex-shrink: 0;
	}

	.msg-nav-title {
		font-size: 13px;
		font-weight: 600;
		color: #0d0d0d;
	}

	.msg-nav-close {
		display: inline-flex;
		align-items: center;
		justify-content: center;
		width: 24px;
		height: 24px;
		background: none;
		border: none;
		border-radius: 6px;
		color: #8f8f8f;
		cursor: pointer;
	}

	.msg-nav-close:hover {
		background-color: #f0f0f0;
		color: #0d0d0d;
	}

	.msg-nav-close svg {
		width: 14px;
		height: 14px;
	}

	.msg-nav-list {
		flex: 1;
		min-height: 0;
		overflow-y: auto;
		padding: 6px;
	}

	.msg-nav-empty {
		padding: 16px 10px;
		font-size: 12.5px;
		color: #9a9a9a;
		text-align: center;
	}

	.msg-nav-item {
		display: flex;
		align-items: flex-start;
		gap: 8px;
		padding: 8px 10px;
		border-radius: 8px;
		cursor: pointer;
		transition: background-color 0.12s ease;
	}

	.msg-nav-item:hover {
		background-color: #f4f4f4;
	}

	.msg-nav-item.active {
		background-color: rgba(16, 163, 127, 0.08);
	}

	.msg-nav-item.active .msg-nav-text {
		color: #0b7a61;
		font-weight: 500;
	}

	.msg-nav-seq {
		flex-shrink: 0;
		font-size: 11px;
		color: #9a9a9a;
		font-variant-numeric: tabular-nums;
		padding-top: 2px;
	}

	.msg-nav-item.active .msg-nav-seq {
		color: #10a37f;
	}

	.msg-nav-text {
		font-size: 13px;
		line-height: 1.5;
		color: #35363a;
		display: -webkit-box;
		-webkit-line-clamp: 2;
		-webkit-box-orient: vertical;
		overflow: hidden;
		word-break: break-word;
	}

	/* 移动端:细条隐藏,面板变为抽屉式覆盖层 */
	@media (max-width: 768px) {
		.msg-nav {
			position: absolute;
			top: 0;
			right: 0;
			bottom: 0;
			width: 0;
			background: transparent;
			border-left: none;
		}

		.msg-nav.expanded {
			width: 300px;
			max-width: 85vw;
			background: #ffffff;
			border-left: 1px solid #ececf1;
			box-shadow: -8px 0 24px rgba(0, 0, 0, 0.12);
		}

		.msg-nav-rail-btn {
			display: none;
		}
	}
</style>
