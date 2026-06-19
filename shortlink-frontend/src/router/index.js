import { createRouter, createWebHistory } from 'vue-router'

const routes = [
  {
    path: '/',
    name: 'Home',
    component: () => import('../views/Home.vue'),
    meta: { title: '创建短链' }
  },
  {
    path: '/list',
    name: 'List',
    component: () => import('../views/List.vue'),
    meta: { title: '短链列表' }
  },
  {
    path: '/stats/:shortCode',
    name: 'Stats',
    component: () => import('../views/Stats.vue'),
    meta: { title: '统计详情' }
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

// 路由守卫 - 设置页面标题
router.beforeEach((to, from, next) => {
  document.title = '短链接服务'
  next()
})

export default router
