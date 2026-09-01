<template>
	<div>
		<el-form :model="form" label-position="top">
			<el-form-item label="动态内容" prop="content">
				<mavon-editor v-model="form.content" :toolbars="toolbars" ref="editor">
					<template slot="left-toolbar-after">
						<button type="button" class="editor-image-btn fa fa-mavon-picture-o"
						        title="插入图片：从图片库选择或上传" @click="openImagePicker"></button>
					</template>
				</mavon-editor>
			</el-form-item>

			<el-form-item label="点赞数" prop="likes" style="width: 50%">
				<el-input v-model="form.likes" type="number" placeholder="可选，默认为 0"></el-input>
			</el-form-item>

			<el-form-item label="创建时间" prop="createTime">
				<el-date-picker v-model="form.createTime" type="datetime" placeholder="可选，默认此刻" :editable="false"></el-date-picker>
			</el-form-item>

			<el-form-item style="text-align: right;">
				<el-button type="info" @click="submit(false)">仅自己可见</el-button>
				<el-button type="primary" @click="submit(true)">发布动态</el-button>
			</el-form-item>
		</el-form>

		<!--图片选择/上传-->
		<image-picker v-model="pickerVisible" @insert="insertImage"/>
	</div>
</template>

<script>
	import Breadcrumb from "@/components/Breadcrumb";
	import ImagePicker from "@/components/ImagePicker";
	import toolbars from "@/util/editorToolbars";
	import {getMomentById, saveMoment, updateMoment} from "@/api/moment";

	export default {
		name: "WriteMoment",
		components: {Breadcrumb, ImagePicker},
		data() {
			return {
				toolbars: toolbars,
				pickerVisible: false,
				form: {
					content: '',
					createTime: null,
					likes: 0,
					published: false
				},
			}
		},
		created() {
			if (this.$route.params.id) {
				this.getMoment(this.$route.params.id)
			}
		},
		methods: {
			getMoment(id) {
				getMomentById(id).then(res => {
					this.form = res.data
				})
			},
			submit(published) {
				this.form.published = published
				if (this.$route.params.id) {
					updateMoment(this.form).then(res => {
						this.msgSuccess(res.msg)
						this.$router.push('/blog/moment/list')
					})
				} else {
					saveMoment(this.form).then(res => {
						this.msgSuccess(res.msg)
						this.$router.push('/blog/moment/list')
					})
				}
			},
			openImagePicker() {
				this.pickerVisible = true
			},
			insertImage(url, name) {
				const editor = this.$refs.editor
				if (editor) {
					editor.insertText(editor.getTextareaDom(), {
						prefix: '![',
						str: `${name}](${url})`,
						subfix: ''
					})
				}
			}
		}
	}
</script>

<style scoped>
	.editor-image-btn {
		background: transparent;
		border: none;
		cursor: pointer;
		padding: 0 4px;
		font-size: 16px;
		line-height: 1;
		color: #606266;
	}

	.editor-image-btn:hover {
		color: #409EFF;
	}
</style>