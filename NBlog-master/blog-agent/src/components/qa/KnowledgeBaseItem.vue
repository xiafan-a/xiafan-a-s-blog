<template>
	<div class="m-deepseek-section">
		<div class="m-deepseek-section-header" @click="handleKbClick">
			<span class="m-deepseek-section-title">{{ kb.name }}</span>
			<div class="m-deepseek-section-actions">
				<span class="m-deepseek-toggle-icon" :class="{ 'm-deepseek-toggle-icon-open': isExpanded }">▼</span>
				<button class="m-deepseek-btn m-deepseek-action-btn" @click.stop="handleAddSession" title="添加会话">
					<span class="m-deepseek-btn-text">+</span>
				</button>
				<button class="m-deepseek-btn m-deepseek-action-btn" @click.stop="handleEditKb" title="修改知识库">
					<span class="m-deepseek-btn-text">✏</span>
				</button>
				<button class="m-deepseek-btn m-deepseek-action-btn" @click.stop="handleUploadDocument" title="上传文档">
					<span class="m-deepseek-btn-text">📁</span>
				</button>
				<button class="m-deepseek-btn m-deepseek-action-btn" @click.stop="handleDeleteKb" title="删除知识库">
					<span class="m-deepseek-btn-text">×</span>
				</button>
			</div>
		</div>
		<slot v-if="isExpanded"></slot>
	</div>
</template>

<script>
export default {
	name: "KnowledgeBaseItem",
	props: {
		kb: {
			type: Object,
			required: true
		}
	},
	data() {
		return {
			isExpanded: false
		};
	},
	methods: {
		handleAddSession() {
			// 展开知识库
			if (!this.isExpanded) {
				this.isExpanded = true;
				this.$emit('load-sessions', this.kb.id);
			}
			this.$emit('add-session', this.kb.id);
		},
		handleEditKb() {
			this.$emit('edit-kb', this.kb);
		},
		handleUploadDocument() {
			this.$emit('upload-document', this.kb.id);
		},
		handleDeleteKb() {
			this.$emit('delete-kb', this.kb.id, this.kb.name);
		},
		handleKbClick() {
			this.isExpanded = !this.isExpanded;
			if (this.isExpanded) {
				this.$emit('load-sessions', this.kb.id);
			}
		},
		// 暴露给父组件的方法
		expand() {
			if (!this.isExpanded) {
				this.isExpanded = true;
				this.$emit('load-sessions', this.kb.id);
			}
		}
	}
}
</script>

<style scoped>
.m-deepseek-section {
	margin-bottom: 20px;
}

.m-deepseek-section-header {
			display: flex;
			justify-content: space-between;
			align-items: center;
			padding: 10px 15px;
			background-color: #ffffff;
			border-radius: 6px;
			margin-bottom: 8px;
			box-shadow: 0 1px 3px rgba(0, 0, 0, 0.05);
			cursor: pointer;
			transition: background-color 0.2s ease;
		}

		.m-deepseek-section-header:hover {
			background-color: #f8f9fa;
		}

		.m-deepseek-section-title {
			font-weight: 500;
			color: #333;
		}

.m-deepseek-section-actions {
	display: flex;
	gap: 5px;
	align-items: center;
}

.m-deepseek-toggle-icon {
	font-size: 12px;
	color: #666;
	transition: transform 0.3s ease;
	margin-right: 5px;
}

.m-deepseek-toggle-icon-open {
	transform: rotate(180deg);
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