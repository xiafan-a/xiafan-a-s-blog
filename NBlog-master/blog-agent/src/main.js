import Vue from 'vue'
import App from './App.vue'
import router from './router'
//element-ui
import Element from 'element-ui'
import 'element-ui/lib/theme-chalk/index.css'
//自定义css
import './assets/css/base.css'
//ChatGPT 风格共享聊天样式
import './assets/css/chat.css'
//阿里icon
import './assets/css/icon/iconfont.css'

Vue.use(Element)

Vue.prototype.msgSuccess = function (msg) {
	this.$message.success(msg)
}

Vue.prototype.msgError = function (msg) {
	this.$message.error(msg)
}

Vue.prototype.msgInfo = function (msg) {
	this.$message.info(msg)
}

Vue.config.productionTip = false

new Vue({
	router,
	render: h => h(App)
}).$mount('#app')
