<template>
	<div class="image-manage">
		<!--工具栏-->
		<el-card>
			<el-upload
					:show-file-list="false"
					:http-request="handleUpload"
					accept="image/*"
					multiple
					style="display: inline-block">
				<el-button type="primary" size="small" icon="el-icon-upload2" :loading="uploading">上传图片</el-button>
			</el-upload>
			<span class="tip">支持 jpg / png / gif / webp 等格式，支持多选</span>
		</el-card>

		<!--图片列表-->
		<div v-loading="loading" class="grid-wrap">
			<el-row :gutter="10" v-viewer>
				<el-col v-for="item in images" :key="item.id" :xs="12" :sm="8" :md="6" :lg="4">
					<el-card :body-style="{padding: '0px', width: '100%'}" class="img-card" shadow="hover">
						<div class="thumb-wrap">
							<el-image :src="item.url" fit="cover" class="thumb" lazy>
								<div slot="error" class="thumb-error"><i class="el-icon-picture-outline"></i></div>
							</el-image>
						</div>
						<div class="img-info">
							<div class="name" :title="item.original_name">{{ item.original_name }}</div>
							<div class="meta">{{ formatSize(item.file_size) }} · {{ item.width }} × {{ item.height }}</div>
							<div class="meta">{{ formatDate(item.created_at) }}</div>
							<div class="actions">
								<el-button type="text" size="mini" icon="el-icon-link" @click="copyLink(item)">复制链接</el-button>
								<el-popconfirm title="确定删除这张图片吗？" icon="el-icon-delete" iconColor="red" @onConfirm="deleteImageById(item)">
									<el-button type="text" size="mini" icon="el-icon-delete" class="del" slot="reference">删除</el-button>
								</el-popconfirm>
							</div>
						</div>
					</el-card>
				</el-col>
			</el-row>
			<el-empty v-if="!loading && images.length === 0" description="暂无图片，点击上方按钮上传"></el-empty>
		</div>

		<!--加载更多-->
		<div v-if="hasMore && images.length > 0" class="load-more">
			<el-button :loading="loadingMore" @click="loadMore">加载更多</el-button>
		</div>
	</div>
</template>

<script>
	import moment from 'moment'
	import {getImages, uploadImage, deleteImage} from '@/api/image'

	export default {
		name: "ImageList",
		data() {
			return {
				images: [],
				skip: 0,
				limit: 20,
				hasMore: true,
				loading: false,
				loadingMore: false,
				uploading: false
			}
		},
		created() {
			this.getImages()
		},
		methods: {
			getImages() {
				this.loading = true
				getImages(0, this.limit).then(data => {
					this.images = data || []
					this.skip = this.images.length
					this.hasMore = data.length === this.limit
					this.loading = false
				}).catch(() => {
					this.loading = false
				})
			},
			loadMore() {
				this.loadingMore = true
				getImages(this.skip, this.limit).then(data => {
					this.images = this.images.concat(data || [])
					this.skip = this.images.length
					this.hasMore = data.length === this.limit
					this.loadingMore = false
				}).catch(() => {
					this.loadingMore = false
				})
			},
			handleUpload(option) {
				this.uploading = true
				uploadImage(option.file).then(() => {
					this.msgSuccess('上传成功')
					this.getImages()
					this.uploading = false
				}).catch(() => {
					this.uploading = false
				})
			},
			deleteImageById(item) {
				deleteImage(item.id).then(() => {
					this.msgSuccess('删除成功')
					this.getImages()
				})
			},
			copyLink(item) {
				const textarea = document.createElement('textarea')
				textarea.value = item.url
				textarea.style.position = 'fixed'
				textarea.style.opacity = '0'
				document.body.appendChild(textarea)
				textarea.select()
				try {
					document.execCommand('copy')
					this.msgSuccess('链接已复制')
				} catch (e) {
					this.msgError('复制失败，请手动复制')
				}
				document.body.removeChild(textarea)
			},
			formatSize(size) {
				if (size == null) return '-'
				const units = ['B', 'KB', 'MB', 'GB']
				let value = size
				let index = 0
				while (value >= 1024 && index < units.length - 1) {
					value /= 1024
					index++
				}
				return (index === 0 ? Math.round(value) : value.toFixed(1)) + ' ' + units[index]
			},
			formatDate(value) {
				return value ? moment(value).format('YYYY-MM-DD HH:mm:ss') : '-'
			}
		}
	}
</script>

<style scoped>
	.tip {
		margin-left: 12px;
		font-size: 13px;
		color: #909399;
	}

	.grid-wrap {
		margin-top: 10px;
		min-height: 120px;
	}

	.img-card {
		margin-bottom: 10px;
	}

	.thumb-wrap {
		height: 140px;
		overflow: hidden;
		cursor: zoom-in;
		background: #f5f7fa;
	}

	.thumb {
		width: 100%;
		height: 100%;
		display: block;
	}

	.thumb-error {
		width: 100%;
		height: 100%;
		display: flex;
		align-items: center;
		justify-content: center;
		font-size: 28px;
		color: #c0c4cc;
	}

	.img-info {
		padding: 8px 10px;
	}

	.name {
		font-size: 13px;
		color: #303133;
		white-space: nowrap;
		overflow: hidden;
		text-overflow: ellipsis;
	}

	.meta {
		font-size: 12px;
		color: #909399;
		margin-top: 2px;
	}

	.actions {
		margin-top: 6px;
		display: flex;
		justify-content: space-between;
	}

	.del {
		color: #f56c6c;
	}

	.load-more {
		text-align: center;
		margin: 12px 0;
	}
</style>