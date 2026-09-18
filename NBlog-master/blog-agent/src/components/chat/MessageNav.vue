<template>
	<div class="msg-nav" v-if="questions.length > 0">
		<div class="msg-nav-list">
			<div
				v-for="q in questions"
				:key="q.index"
				class="msg-nav-bar"
				:class="{active: q.index === activeIndex}"
				@click="$emit('locate', q.index)"
				@mouseenter="onBarEnter(q, $event)"
				@mouseleave="onBarLeave"
			></div>
		</div>
		<!-- 悬停弹框:预览问题内容(过长截断);挂在列表外层,避免被滚动容器裁剪 -->
		<div class="msg-nav-pop" v-if="hoverText" :style="popStyle">{{ hoverText }}</div>
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
		data() {
			return {
				hoverIndex: -1,
				popTop: 0
			}
		},
		computed: {
			// 提取所有用户提问生成锚点;文本去除 HTML 标签并截断,过长不全文展示
			questions() {
				const result = []
				this.messages.forEach((m, index) => {
					if (!m.isUser) return
					const raw = String(m.content || '')
						.replace(/<[^>]*>/g, ' ')
						.replace(/\s+/g, ' ')
						.trim()
					const text = raw.length > 80 ? raw.slice(0, 80) + '…' : raw
					result.push({index, text: text || '(空内容)'})
				})
				return result
			},
			// 悬停横杠对应的问题摘要
			hoverText() {
				const q = this.questions.find(item => item.index === this.hoverIndex)
				return q ? q.text : ''
			},
			// 弹框垂直位置:对准悬停的横杠
			popStyle() {
				return {top: this.popTop + 'px'}
			}
		},
		methods: {
			// 悬停横杠:记录索引并计算弹框位置
			onBarEnter(q, event) {
				this.hoverIndex = q.index
				const bar = event.currentTarget
				const barRect = bar.getBoundingClientRect()
				const navRect = this.$el.getBoundingClientRect()
				// 弹框中心对准横杠中心,并防止超出视口顶部
				this.popTop = Math.max(barRect.top + barRect.height / 2 - navRect.top, 50)
			},
			// 离开横杠:隐藏弹框
			onBarLeave() {
				this.hoverIndex = -1
			}
		}
	}
</script>

<style scoped>
	/* DeepSeek 风格:右侧垂直居中的横杠锚点列(容器不裁剪,弹框可溢出显示) */
	.msg-nav {
		position: absolute;
		right: 8px;
		top: 50%;
		transform: translateY(-50%);
		display: flex;
		flex-direction: column;
		align-items: flex-end;
		z-index: 24;
	}

	/* 横杠列表:提问过多时限高滚动 */
	.msg-nav-list {
		display: flex;
		flex-direction: column;
		align-items: flex-end;
		gap: 8px;
		max-height: 60vh;
		overflow-y: auto;
		padding: 4px 2px;
	}

	.msg-nav-list::-webkit-scrollbar {
		width: 0;
	}

	/* 横杠锚点:细横杠本体 + 更大的点击热区 */
	.msg-nav-bar {
		position: relative;
		width: 16px;
		height: 14px;
		display: flex;
		align-items: center;
		justify-content: flex-end;
		cursor: pointer;
		flex-shrink: 0;
	}

	.msg-nav-bar::before {
		content: '';
		display: block;
		width: 16px;
		height: 3px;
		border-radius: 2px;
		background-color: #d5d5d5;
		transition: width 0.2s ease, background-color 0.2s ease;
	}

	.msg-nav-bar:hover::before {
		background-color: #9a9a9a;
	}

	/* 当前定位:高亮且加长 */
	.msg-nav-bar.active::before {
		width: 28px;
		background-color: #10a37f;
	}

	/* 悬停弹框:显示问题内容摘要,位于横杠左侧 */
	.msg-nav-pop {
		position: absolute;
		right: calc(100% + 10px);
		transform: translateY(-50%);
		width: max-content;
		max-width: 280px;
		padding: 10px 12px;
		background: #ffffff;
		color: #35363a;
		border: 1px solid #ececf1;
		border-radius: 10px;
		box-shadow: 0 8px 24px rgba(0, 0, 0, 0.12);
		font-size: 12.5px;
		line-height: 1.6;
		display: -webkit-box;
		-webkit-line-clamp: 4;
		-webkit-box-orient: vertical;
		overflow: hidden;
		pointer-events: none;
		z-index: 40;
	}

	/* 移动端隐藏锚点列(与 DeepSeek 一致) */
	@media (max-width: 768px) {
		.msg-nav {
			display: none;
		}
	}
</style>
