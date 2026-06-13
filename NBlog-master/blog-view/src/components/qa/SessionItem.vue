<template>
	<div
		class="m-deepseek-session-item"
		:class="{active: activeSession === session.id}"
		@click="handleSelectSession"
	>
		<span class="m-session-name">{{ session.name || `会话 ${sessionIndex + 1}` }}</span>
		<div class="m-session-actions">
			<button class="m-deepseek-btn m-deepseek-session-edit m-deepseek-action-btn" @click.stop="handleStartEdit" title="编辑会话名称">
				<span class="m-deepseek-btn-text">✏</span>
			</button>
			<button class="m-deepseek-btn m-deepseek-session-delete m-deepseek-action-btn" @click.stop="handleDeleteSession">
				<span class="m-deepseek-btn-text">×</span>
			</button>
		</div>
	</div>
</template>

<script>
export default {
	name: "SessionItem",
	props: {
		session: {
			type: Object,
			required: true
		},
		sessionIndex: {
			type: Number,
			required: true
		},
		kbId: {
			type: Number,
			required: true
		},
		activeSession: {
			type: Number,
			default: null
		}
	},
	methods: {
		handleSelectSession() {
			this.$emit('select-session', this.session.id);
		},
		handleStartEdit() {
			this.$emit('start-edit', this.kbId, this.session.id);
		},
		handleDeleteSession() {
			this.$emit('delete-session', this.kbId, this.session.id, this.session.name || this.sessionIndex + 1);
		}
	}
}
</script>

<style scoped>
.m-deepseek-session-item {
	display: flex;
	justify-content: space-between;
	align-items: center;
	padding: 10px 15px;
	margin-bottom: 4px;
	border-radius: 6px;
	cursor: pointer;
	transition: all 0.2s ease;
	background-color: #ffffff;
}

.m-deepseek-session-item:hover {
	background-color: #e9ecef;
}

.m-deepseek-session-item.active {
	background-color: #e3f2fd;
	border-left: 3px solid #2196f3;
	font-weight: 500;
}

.m-session-actions {
	display: flex;
	gap: 5px;
	opacity: 0;
	transition: opacity 0.2s ease;
}

.m-deepseek-session-item:hover .m-session-actions {
	opacity: 1;
}

.m-deepseek-session-edit {
	margin-right: 5px;
}

.m-session-name {
	flex: 1;
	overflow: hidden;
	text-overflow: ellipsis;
	white-space: nowrap;
}

.m-deepseek-btn {
	background: none;
	border: none;
	cursor: pointer;
	padding: 4px 8px;
	border-radius: 4px;
	display: flex;
	align-items: center;
	justify-content: center;
	color: #666;
	font-size: 14px;
	transition: all 0.2s ease;
}

.m-deepseek-btn:hover {
	background-color: #e9ecef;
	color: #333;
}

.m-deepseek-action-btn {
	width: 24px;
	height: 24px;
	padding: 0;
	background-color: #ffffff;
	border: 1px solid #e1e5e9;
	border-radius: 4px;
	font-size: 16px;
	font-weight: bold;
}

.m-deepseek-action-btn:hover {
	background-color: #f1f3f4;
	border-color: #d0d7de;
}

.m-deepseek-btn-text {
	line-height: 1;
}
</style>