import { createRouter, createWebHashHistory } from 'vue-router'

/**
 * 路由表。
 * 这里用的是 hash 模式（地址栏里带 #），好处是不需要服务器做额外配置，刷新页面不会 404。
 */
const routes = [
  { path: '/', name: 'home', component: () => import('../views/HomeView.vue'), meta: { title: '首页' } },
  { path: '/video/:id', name: 'video', component: () => import('../views/VideoView.vue'), meta: { title: '视频播放' } },
  { path: '/ranking', name: 'ranking', component: () => import('../views/RankingView.vue'), meta: { title: '排行榜' } },
  { path: '/bangumi', name: 'bangumi', component: () => import('../views/BangumiView.vue'), meta: { title: '番剧' } },
  { path: '/space/:id', name: 'space', component: () => import('../views/SpaceView.vue'), meta: { title: '个人空间' } },
  { path: '/dynamic', name: 'dynamic', component: () => import('../views/DynamicView.vue'), meta: { title: '动态' } },
  { path: '/search', name: 'search', component: () => import('../views/SearchView.vue'), meta: { title: '搜索' } },
  { path: '/:pathMatch(.*)*', redirect: '/' }
]

const router = createRouter({
  history: createWebHashHistory(),
  routes,
  scrollBehavior() {
    return { top: 0 }
  }
})

router.afterEach((to) => {
  document.title = to.meta?.title
    ? `${to.meta.title} - 哔哩哔哩 (゜-゜)つロ 干杯~`
    : '哔哩哔哩 (゜-゜)つロ 干杯~-bilibili'
})

export default router
