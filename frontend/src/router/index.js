import { createRouter, createWebHashHistory } from 'vue-router'

/**
 * 路由表（hash 模式，服务器不用做额外配置，刷新不会 404）。
 *
 * 内容节点统一走 /node/:code（视频 / 游戏 / 直播 / 番剧 / 动画 / 音乐 …），
 * 预留节点会由 NodeView 渲染成「占位页 + 入口」，不会出现空白或 404。
 */
const routes = [
  { path: '/', name: 'home', component: () => import('../views/HomeView.vue'), meta: { title: '首页' } },

  // 内容节点：分区页 / 预留节点占位页
  { path: '/node/:code', name: 'node', component: () => import('../views/NodeView.vue'), meta: { title: '内容节点' } },
  { path: '/category/:code', redirect: (to) => `/node/${to.params.code}` },

  // 直播 / 专栏 / 活动 / 社区中心（预留节点，复用同一个占位页组件）
  { path: '/live', name: 'live', component: () => import('../views/NodeView.vue'), props: { code: 'live' }, meta: { title: '直播' } },
  { path: '/column', name: 'column', component: () => import('../views/NodeView.vue'), props: { code: 'column' }, meta: { title: '专栏' } },
  { path: '/activity', name: 'activity', component: () => import('../views/NodeView.vue'), props: { code: 'activity' }, meta: { title: '活动' } },
  { path: '/community', name: 'community', component: () => import('../views/NodeView.vue'), props: { code: 'community' }, meta: { title: '社区中心' } },

  // 视频
  { path: '/video/:id', name: 'video', component: () => import('../views/VideoView.vue'), meta: { title: '视频播放' } },
  { path: '/rank', redirect: '/ranking' },
  { path: '/ranking', name: 'ranking', component: () => import('../views/RankingView.vue'), meta: { title: '排行榜' } },
  { path: '/bangumi', name: 'bangumi', component: () => import('../views/BangumiView.vue'), meta: { title: '番剧' } },
  { path: '/space/:id', name: 'space', component: () => import('../views/SpaceView.vue'), meta: { title: '个人空间' } },
  { path: '/dynamic', name: 'dynamic', component: () => import('../views/DynamicView.vue'), meta: { title: '动态' } },
  { path: '/search', name: 'search', component: () => import('../views/SearchView.vue'), meta: { title: '搜索' } },

  // 登录 / 注册 / 找回密码 / 扫码确认
  { path: '/login', name: 'login', component: () => import('../views/LoginView.vue'), meta: { title: '登录' } },
  { path: '/register', name: 'register', component: () => import('../views/RegisterView.vue'), meta: { title: '注册' } },
  { path: '/forgot', name: 'forgot', component: () => import('../views/ForgotView.vue'), meta: { title: '找回密码' } },
  { path: '/qr/:qrId', name: 'qrConfirm', component: () => import('../views/QrConfirmView.vue'), meta: { title: '扫码确认登录' } },

  // 个人中心：主页 / 历史 / 收藏 / 关注 / 消息 / 设置 / 投稿
  {
    path: '/user/:tab?',
    name: 'user',
    component: () => import('../views/UserCenterView.vue'),
    meta: { title: '个人中心', requiresAuth: true }
  },
  { path: '/upload', name: 'upload', component: () => import('../views/UploadView.vue'), meta: { title: '投稿', requiresAuth: true } },

  // 404
  { path: '/:pathMatch(.*)*', name: 'notFound', component: () => import('../views/NotFoundView.vue'), meta: { title: '页面不存在' } }
]

const router = createRouter({
  history: createWebHashHistory(),
  routes,
  scrollBehavior(to, from, savedPosition) {
    if (savedPosition) return savedPosition
    if (to.hash) return { el: to.hash, top: 80 }
    return { top: 0 }
  }
})

router.beforeEach((to) => {
  if (!to.meta?.requiresAuth) return true
  let token
  try {
    token = localStorage.getItem('bili-token') || ''
  } catch (e) {
    token = ''
  }
  if (token) return true
  return { name: 'login', query: { redirect: to.fullPath } }
})

router.afterEach((to) => {
  document.title = to.meta?.title
    ? `${to.meta.title} - a哩a哩 (゜-゜)つロ 干杯~`
    : 'a哩a哩 (゜-゜)つロ 干杯~'
})

export default router
