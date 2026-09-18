<template>
	<div class="msg-nav">
		<div class="msg-nav-header">
			<span class="msg-nav-title">问题定位</span>
			<button class="msg-nav-close" @click="$emit('close')" aria-label="关闭">
				<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
					<line x1="18" y1="6" x2="6" y2="18"/>
					<line x1="6" y1="6" x2="18" y2="18"/>
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
</template>

<script>
	export default {
		name: 'MessageNav',
		props: {
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
	.msg-nav {
		position: absolute;
		top: 10px;
		right: 16px;
		bottom: 110px;
		width: 264px;
		display: flex;
		flex-direction: column;
		background: #ffffff;
		border: 1px solid #ececf1;
		border-radius: 12px;
		box-shadow: 0 8px 30px rgba(0, 0, 0, 0.1);
		z-index: 30;
		overflow: hidden;
		animation: msg-nav-in 0.18s ease;
	}

	@keyframes msg-nav-in {
		from {
			opacity: 0;
			transform: translateX(8px);
		}
		to {
			opacity: 1;
			transform: translateX(0);
		}
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
</style>
