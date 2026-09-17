<template>
	<div class="app-wrapper">
		<sidebar class="sidebar-container" :opened="sidebarOpen"/>
		<div class="main-container">
			<!-- 移动端顶栏 -->
			<div class="mobile-header">
				<button class="hamburger-btn" @click="sidebarOpen = !sidebarOpen" aria-label="菜单">
					<span class="hamburger-line"></span>
					<span class="hamburger-line"></span>
					<span class="hamburger-line"></span>
				</button>
				<span class="mobile-title">Blog Agent</span>
			</div>
			<!-- 移动端遮罩 -->
			<div class="drawer-mask" v-if="sidebarOpen" @click="sidebarOpen = false"></div>
			<!-- 页面内容 -->
			<div class="app-main">
				<router-view/>
			</div>
		</div>
	</div>
</template>

<script>
	import Sidebar from './components/Sidebar'

	export default {
		name: 'Layout',
		components: {Sidebar},
		data() {
			return {
				sidebarOpen: false // 移动端侧边栏展开状态(桌面端始终展示)
			}
		},
		watch: {
			//路由切换时收起移动端侧边栏
			'$route.path'() {
				this.sidebarOpen = false
			}
		}
	}
</script>

<style scoped>
	.app-wrapper {
		display: flex;
		height: 100%;
		width: 100%;
		overflow: hidden;
	}

	.sidebar-container {
		flex-shrink: 0;
	}

	.main-container {
		position: relative;
		flex: 1;
		min-width: 0;
		display: flex;
		flex-direction: column;
	}

	.app-main {
		flex: 1;
		min-height: 0;
		overflow: auto;
		padding: 16px 24px 24px;
	}

	.mobile-header {
		display: none;
	}

	/* 移动端适配:侧边栏默认收起,通过汉堡按钮展开 */
	@media (max-width: 768px) {
		.app-main {
			padding: 12px;
		}

		.mobile-header {
			display: flex;
			align-items: center;
			gap: 12px;
			height: 48px;
			padding: 0 12px;
			background-color: #202123;
			color: #ececf1;
			flex-shrink: 0;
		}

		.mobile-title {
			font-size: 15px;
			font-weight: 600;
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
			background-color: #ececf1;
			border-radius: 1px;
		}

		.drawer-mask {
			position: fixed;
			top: 0;
			left: 0;
			right: 0;
			bottom: 0;
			z-index: 998;
			background: rgba(0, 0, 0, 0.4);
		}
	}
</style>

<style>
	/* 移动端侧边栏滑出(非 scoped,配合 Sidebar 内部样式) */
	@media (max-width: 768px) {
		.sidebar-container {
			position: fixed;
			top: 0;
			left: 0;
			bottom: 0;
			z-index: 999;
			transform: translateX(-100%);
			transition: transform 0.25s ease;
		}

		.sidebar-container.sidebar-open {
			transform: translateX(0);
		}
	}
</style>
