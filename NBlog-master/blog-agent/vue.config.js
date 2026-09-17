'use strict'
const path = require('path')
const defaultSettings = require('./src/settings.js')

function resolve(dir) {
	return path.join(__dirname, dir)
}

const name = defaultSettings.title // page title

// 本项目前端开发端口(后端 blog-agent 聊天服务运行在 8088)
const port = process.env.port || process.env.npm_config_port || 8089

module.exports = {
	publicPath: '/',
	outputDir: 'dist',
	assetsDir: 'static',
	lintOnSave: process.env.NODE_ENV === 'development',
	productionSourceMap: false,
	devServer: {
		port: port,
		open: true,
		overlay: {
			warnings: false,
			errors: true
		},
		// 开发环境下将聊天服务 API 代理到本地 blog-agent 后端(8088)
		proxy: {
			'/api/v1': {
				target: 'http://localhost:8088',
				changeOrigin: true
			}
		}
	},
	configureWebpack: {
		name: name,
		resolve: {
			alias: {
				'@': resolve('src'),
				'assets': '@/assets',
				'components': '@/components',
				'api': '@/api',
				'views': '@/views'
			}
		}
	}
}
