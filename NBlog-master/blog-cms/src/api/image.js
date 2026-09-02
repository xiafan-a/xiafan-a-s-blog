import axios from 'axios'
import NProgress from 'nprogress'
import 'nprogress/nprogress.css'
import {Message} from 'element-ui'

const request = axios.create({
	baseURL: '/api/v1/images',
	timeout: 30000 //上传图片可能需要更长的超时时间
})

request.interceptors.request.use(config => {
		NProgress.start()
		return config
	},
	error => {
		console.info(error)
		return Promise.reject(error)
	}
)

request.interceptors.response.use(response => {
		NProgress.done()
		return response.data
	},
	error => {
		console.info(error)
		const detail = error.response && error.response.data && error.response.data.detail
		Message.error(detail || error.message)
		return Promise.reject(error)
	}
)

//获取图片列表，skip/limit 分页，返回 Image 数组
export function getImages(skip, limit) {
	return request({
		url: '',
		method: 'get',
		params: {skip, limit}
	}).then(res => res.data)
}

//上传图片，返回上传成功后的 Image
export function uploadImage(file) {
	const formData = new FormData()
	formData.append('file', file)
	return request({
		url: '',
		method: 'post',
		data: formData,
		headers: {'Content-Type': 'multipart/form-data'}
	}).then(res => res.data)
}

//按id查询图片
export function getImageById(id) {
	return request({
		url: `${id}`,
		method: 'get'
	}).then(res => res.data)
}

//按imageName(哈希文件名)查询图片
export function getImageByName(imageName) {
	return request({
		url: `name/${imageName}`,
		method: 'get'
	}).then(res => res.data)
}

//删除图片
export function deleteImage(id) {
	return request({
		url: `${id}`,
		method: 'delete'
	})
}