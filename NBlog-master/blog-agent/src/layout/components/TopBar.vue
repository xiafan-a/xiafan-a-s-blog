<template>
	<div class="top-bar">
		<!-- 桌面端:侧边栏收缩/展开按钮 -->
		<button
			class="sidebar-toggle-btn"
			:class="{collapsed: collapsed}"
			:title="collapsed ? '展开侧边栏' : '收起侧边栏'"
			aria-label="切换侧边栏"
			@click="$emit('toggle-collapse')"
		>
			<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
				<rect x="3" y="3" width="18" height="18" rx="2" ry="2"/>
				<line x1="9" y1="3" x2="9" y2="21"/>
			</svg>
		</button>

		<!-- 移动端汉堡按钮 -->
		<button class="hamburger-btn" @click="$emit('toggle-mobile')" aria-label="菜单">
			<span class="hamburger-line"></span>
			<span class="hamburger-line"></span>
			<span class="hamburger-line"></span>
		</button>

		<!-- 模式(模型)选择器 -->
		<div class="model-picker" ref="pickerRef">
			<button class="picker-btn" @click="pickerOpen = !pickerOpen">
				<span class="picker-label">{{ currentMode }}</span>
				<svg class="picker-chevron" :class="{open: pickerOpen}" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
					<polyline points="6 9 12 15 18 9"/>
				</svg>
			</button>
			<div class="picker-menu" v-if="pickerOpen">
				<router-link to="/qa" class="picker-item" @click.native="closePicker">
					<span class="picker-item-texts">
						<span class="picker-item-name">知识问答</span>
						<span class="picker-item-desc">基于知识库检索回答</span>
					</span>
					<svg v-if="$route.name === 'qa'" class="picker-check" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round">
						<polyline points="20 6 9 17 4 12"/>
					</svg>
				</router-link>
				<router-link to="/agent" class="picker-item" @click.native="closePicker">
					<span class="picker-item-texts">
						<span class="picker-item-name">智能体</span>
						<span class="picker-item-desc">自主规划步骤并调用工具</span>
					</span>
					<svg v-if="$route.name === 'agent'" class="picker-check" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round">
						<polyline points="20 6 9 17 4 12"/>
					</svg>
				</router-link>
			</div>
		</div>
	</div>
</template>

<script>
	export default {
		name: 'TopBar',
		props: {
			// 桌面端侧边栏是否已收起(仅用于按钮状态样式)
			collapsed: {
				type: Boolean,
				default: false
			}
		},
		data() {
			return {
				pickerOpen: false
			}
		},
		computed: {
			currentMode() {
				return this.$route.name === 'qa' ? '知识问答' : '智能体'
			}
		},
		mounted() {
			document.addEventListener('click', this.handleOutside)
		},
		beforeDestroy() {
			document.removeEventListener('click', this.handleOutside)
		},
		methods: {
			closePicker() {
				this.pickerOpen = false
			},
			handleOutside(e) {
				const el = this.$refs.pickerRef
				if (this.pickerOpen && el && !el.contains(e.target)) {
					this.pickerOpen = false
				}
			}
		}
	}
</script>

<style scoped>
	.top-bar {
		height: 52px;
		display: flex;
		align-items: center;
		padding: 0 12px;
		background: #ffffff;
		flex-shrink: 0;
		position: relative;
		z-index: 20;
	}

	.hamburger-btn {
		display: none;
	}

	/* 桌面端:侧边栏收缩/展开按钮 */
	.sidebar-toggle-btn {
		display: inline-flex;
		align-items: center;
		justify-content: center;
		width: 34px;
		height: 34px;
		margin-right: 6px;
		background: none;
		border: none;
		border-radius: 8px;
		color: #0d0d0d;
		cursor: pointer;
		transition: background-color 0.15s ease;
	}

	.sidebar-toggle-btn:hover {
		background-color: #ececec;
	}

	.sidebar-toggle-btn.collapsed {
		color: #6b6b6b;
	}

	.sidebar-toggle-btn svg {
		width: 18px;
		height: 18px;
	}

	.model-picker {
		position: relative;
	}

	.picker-btn {
		display: inline-flex;
		align-items: center;
		gap: 6px;
		padding: 7px 12px;
		background: none;
		border: none;
		border-radius: 10px;
		font-size: 16px;
		font-weight: 600;
		color: #0d0d0d;
		cursor: pointer;
		transition: background-color 0.15s ease;
	}

	.picker-btn:hover {
		background-color: #ececec;
	}

	.picker-chevron {
		width: 16px;
		height: 16px;
		color: #6b6b6b;
		transition: transform 0.2s ease;
	}

	.picker-chevron.open {
		transform: rotate(180deg);
	}

	.picker-menu {
		position: absolute;
		top: 44px;
		left: 0;
		width: 280px;
		background: #ffffff;
		border: 1px solid #ececf1;
		border-radius: 12px;
		box-shadow: 0 8px 30px rgba(0, 0, 0, 0.12);
		padding: 6px;
		z-index: 50;
	}

	.picker-item {
		display: flex;
		align-items: center;
		justify-content: space-between;
		gap: 10px;
		padding: 10px 12px;
		border-radius: 8px;
		text-decoration: none;
		transition: background-color 0.12s ease;
	}

	.picker-item:hover {
		background-color: #f4f4f4;
	}

	.picker-item-texts {
		display: flex;
		flex-direction: column;
		gap: 2px;
	}

	.picker-item-name {
		font-size: 14px;
		font-weight: 500;
		color: #0d0d0d;
	}

	.picker-item-desc {
		font-size: 12px;
		color: #8f8f8f;
	}

	.picker-check {
		width: 16px;
		height: 16px;
		color: #10a37f;
		flex-shrink: 0;
	}

	@media (max-width: 768px) {
		.sidebar-toggle-btn {
			display: none;
		}

		.hamburger-btn {
			display: inline-flex;
			flex-direction: column;
			justify-content: center;
			gap: 4px;
			width: 32px;
			height: 32px;
			padding: 6px;
			background: none;
			border: none;
			cursor: pointer;
		}

		.hamburger-line {
			display: block;
			height: 2px;
			width: 100%;
			background-color: #0d0d0d;
			border-radius: 1px;
		}
	}
</style>
