<template>
	<el-dialog title="插入图片" class="image-picker" :visible.sync="dialogVisible" width="760px" append-to-body>
		<div v-loading="listLoading" class="picker-body">
			<!--上传：与图片管理同一接口，上传成功后自动插入链接并刷新列表-->
			<el-upload
					drag
					:show-file-list="false"
					:http-request="doUpload"
					accept="image/*"
					multiple
					style="width: 100%">
				<i class="el-icon-upload"></i>
				<div class="el-upload__text">将图片拖到此处，或<em>点击上传</em></div>
				<div slot="tip" class="el-upload__tip">支持 jpg / png / gif / webp 等格式，上传完成后会自动把链接插入编辑器</div>
			</el-upload>

			<!--已有图片：按创建时间倒序展示原始文件名，点击插入实际存储地址-->
			<div class="list-title">图片库</div>
			<ul class="picker-list">
				<li v-for="item in images" :key="item.id" :title="item.url" @click="choose(item)">
					<i class="el-icon-picture-outline p-ico"></i>
					<div class="p-name">{{ item.original_name }}</div>
				</li>
			</ul>
			<el-empty v-if="!listLoading && images.length === 0" description="图片库为空，请先上传"></el-empty>
			<div v-if="hasMore" class="p-more">
				<el-button size="mini" :loading="loadingMore" @click="loadMore">加载更多</el-button>
			</div>
		</div>
	</el-dialog>
</template>

<script>
	import {getImages, uploadImage} from '@/api/image'

	export default {
		name: "ImagePicker",
		props: {
			value: {
				type: Boolean,
				default: false
			}
		},
		data() {
			return {
				images: [],
				skip: 0,
				limit: 50,
				hasMore: true,
				listLoading: false,
				loadingMore: false
			}
		},
		computed: {
			dialogVisible: {
				get() {
					return this.value
				},
				set(v) {
					this.$emit('input', v)
				}
			}
		},
		watch: {
			value(v) {
				if (v) {
					this.getImages()
				}
			}
		},
		methods: {
			getImages() {
				this.listLoading = true
				getImages(0, this.limit).then(list => {
					this.images = list || []
					this.skip = this.images.length
					this.hasMore = (list || []).length === this.limit
					this.listLoading = false
				}).catch(() => {
					this.listLoading = false
				})
			},
			loadMore() {
				this.loadingMore = true
				getImages(this.skip, this.limit).then(list => {
					this.images = this.images.concat(list || [])
					this.skip = this.images.length
					this.hasMore = (list || []).length === this.limit
					this.loadingMore = false
				}).catch(() => {
					this.loadingMore = false
				})
			},
			doUpload(option) {
				uploadImage(option.file).then(img => {
					this.msgSuccess('上传成功，已插入链接')
					this.$emit('insert', img.url, img.original_name)
					this.getImages()
				})
			},
			choose(item) {
				this.$emit('insert', item.url, item.original_name)
				this.dialogVisible = false
			}
		}
	}
</script>

<style scoped>
	.picker-body {
		max-height: 60vh;
		overflow: auto;
	}

	.list-title {
		margin-top: 12px;
		margin-bottom: 6px;
		font-size: 14px;
		color: #606266;
		font-weight: 600;
	}

	.picker-list {
		list-style: none;
		margin: 0;
		padding: 0;
	}

	.picker-list li {
		display: flex;
		align-items: center;
		padding: 8px 6px;
		border-radius: 4px;
		cursor: pointer;
	}

	.picker-list li:hover {
		background: #f5f7fa;
	}

	.p-ico {
		font-size: 18px;
		color: #909399;
		flex-shrink: 0;
	}

	.p-name {
		margin-left: 10px;
		font-size: 13px;
		color: #303133;
		white-space: nowrap;
		overflow: hidden;
		text-overflow: ellipsis;
	}

	.p-more {
		text-align: center;
		margin-top: 8px;
	}
</style>