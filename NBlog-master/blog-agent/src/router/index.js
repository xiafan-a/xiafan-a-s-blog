import Vue from 'vue'
import VueRouter from 'vue-router'
import Layout from '@/layout'
import getPageTitle from '@/util/get-page-title'

Vue.use(VueRouter)

const routes = [
	{
		path: '/',
		component: Layout,
		redirect: '/agent',
		children: [
			{
				path: 'qa',
				name: 'qa',
				component: () => import('@/views/qa/Qa'),
				meta: {title: '知识问答'}
			},
			{
				path: 'agent',
				name: 'agent',
				component: () => import('@/views/agent/Agent'),
				meta: {title: '智能体'}
			}
		]
	}
]

const router = new VueRouter({
	mode: 'history',
	base: process.env.BASE_URL,
	routes
})

//挂载路由守卫
router.beforeEach((to, from, next) => {
	document.title = getPageTitle(to.meta.title)
	next()
})

export default router
