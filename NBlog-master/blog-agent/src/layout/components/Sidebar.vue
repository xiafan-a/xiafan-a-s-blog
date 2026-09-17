<template>
	<div class="sidebar" :class="{'sidebar-open': opened}">
		<!-- Logo -->
		<div class="sidebar-logo">
			<span class="logo-badge">
				<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
					<path d="M21 11.5a8.38 8.38 0 0 1-.9 3.8 8.5 8.5 0 0 1-7.6 4.7 8.38 8.38 0 0 1-3.8-.9L3 21l1.9-5.7a8.38 8.38 0 0 1-.9-3.8 8.5 8.5 0 0 1 4.7-7.6 8.38 8.38 0 0 1 3.8-.9h.5a8.48 8.48 0 0 1 8 8v.5z"/>
				</svg>
			</span>
			<span class="logo-text">Blog Agent</span>
		</div>

		<!-- 导航 -->
		<nav class="sidebar-nav">
			<router-link to="/qa" class="nav-item" :class="{active: $route.name === 'qa'}">
				<span class="nav-icon">
					<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
						<path d="M4 19.5A2.5 2.5 0 0 1 6.5 17H20"/>
						<path d="M6.5 2H20v20H6.5A2.5 2.5 0 0 1 4 19.5v-15A2.5 2.5 0 0 1 6.5 2z"/>
					</svg>
				</span>
				<span class="nav-text">知识问答</span>
			</router-link>
			<router-link to="/agent" class="nav-item" :class="{active: $route.name === 'agent'}">
				<span class="nav-icon">
					<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
						<rect x="4" y="7" width="16" height="13" rx="2"/>
						<path d="M12 7V4"/>
						<path d="M9 4h6"/>
						<line x1="9" y1="12" x2="9" y2="12.01"/>
						<line x1="15" y1="12" x2="15" y2="12.01"/>
						<path d="M9 16h6"/>
					</svg>
				</span>
				<span class="nav-text">智能体</span>
			</router-link>
		</nav>

		<!-- 底部:返回博客 -->
		<div class="sidebar-footer">
			<a class="nav-item" :href="blogUrl" target="_blank" rel="noopener">
				<span class="nav-icon">
					<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
						<path d="M3 9l9-7 9 7v11a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2z"/>
						<polyline points="9 22 9 12 15 12 15 22"/>
					</svg>
				</span>
				<span class="nav-text">返回博客</span>
			</a>
		</div>
	</div>
</template>

<script>
	import defaultSettings from '@/settings'

	export default {
		name: 'Sidebar',
		props: {
			opened: {
				type: Boolean,
				default: false
			}
		},
		computed: {
			blogUrl() {
				return defaultSettings.BLOG_URL
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
		padding: 18px 16px;
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
		font-size: 16px;
		font-weight: 600;
		letter-spacing: 0.3px;
	}

	.sidebar-nav {
		flex: 1;
		min-height: 0;
		overflow-y: auto;
		padding: 4px 10px;
	}

	.sidebar-footer {
		padding: 10px;
		border-top: 1px solid rgba(255, 255, 255, 0.08);
	}

	.nav-item {
		display: flex;
		align-items: center;
		gap: 12px;
		padding: 10px 12px;
		margin-bottom: 2px;
		border-radius: 8px;
		color: #ececf1;
		text-decoration: none;
		font-size: 14px;
		cursor: pointer;
		transition: background-color 0.15s ease;
	}

	.nav-item:hover {
		background-color: #2a2b32;
	}

	.nav-item.active {
		background-color: #343541;
	}

	.nav-icon {
		display: inline-flex;
		align-items: center;
		justify-content: center;
		width: 20px;
		height: 20px;
		flex-shrink: 0;
		color: #b4b4b8;
	}

	.nav-item.active .nav-icon {
		color: #10a37f;
	}

	.nav-icon svg {
		width: 18px;
		height: 18px;
	}

	.nav-text {
		overflow: hidden;
		text-overflow: ellipsis;
		white-space: nowrap;
	}
</style>
