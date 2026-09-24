import axios from 'axios'

/**
 * 统一的接口请求封装。
 * 开发环境走 vite 代理（见 vite.config.js），生产环境前后端同域，baseURL 用 /api 即可。
 */
const http = axios.create({
  baseURL: '/api',
  timeout: 20000
})

/** 登录令牌（和后端 user_tokens 表对应），放在 localStorage 里 */
export function getToken() {
  try {
    return localStorage.getItem('bili-token') || ''
  } catch (e) {
    return ''
  }
}

export function setToken(token) {
  try {
    if (token) localStorage.setItem('bili-token', token)
    else localStorage.removeItem('bili-token')
  } catch (e) {
    /* 隐私模式下 localStorage 可能不可用 */
  }
}

// 请求拦截：自动带上 Authorization: Bearer <token>
http.interceptors.request.use((config) => {
  const token = getToken()
  if (token) {
    config.headers = config.headers || {}
    config.headers.Authorization = 'Bearer ' + token
  }
  return config
})

// 响应拦截：直接把后端返回的 data 拿出来；401 时通知全局（清掉本地登录态）
http.interceptors.response.use(
  (response) => response.data,
  (error) => {
    const url = error?.config?.url || ''
    const status = error?.response?.status
    error.friendlyMessage = error?.response?.data?.message || ''
    if (status === 401) {
      // 带上触发 401 的 token，App 只清理「同一个 token」的会话，
      // 避免登录前的游客请求/旧请求把刚建立的登录态误杀
      const raw = error?.config?.headers?.Authorization || error?.config?.headers?.authorization || ''
      const token = typeof raw === 'string' && raw.startsWith('Bearer ') ? raw.slice(7) : ''
      window.dispatchEvent(new window.CustomEvent('bili-unauthorized', { detail: { token } }))
    } else if (status === 404) {
      console.warn('[接口 404]', url)
    } else {
      console.warn('[接口异常]', url, error?.message)
    }
    return Promise.reject(error)
  }
)

export const api = {
  // ---------------- 首页 / 内容节点 ----------------
  home: () => http.get('/home'),
  videos: (params) => http.get('/videos', { params }),
  rankings: (params) => http.get('/rankings', { params }),
  categories: () => http.get('/categories'),
  nodes: () => http.get('/nodes'),
  node: (code) => http.get(`/nodes/${encodeURIComponent(code)}`),

  // ---------------- 视频 ----------------
  video: (id) => http.get(`/videos/${id}`),
  related: (id, limit = 12) => http.get(`/videos/${id}/related`, { params: { limit } }),
  addView: (id, progress = 0) => http.post(`/videos/${id}/view`, null, { params: { progress } }),
  reportProgress: (id, progress) => http.post(`/videos/${id}/progress`, { progress }),
  action: (id, type) => http.post(`/videos/${id}/action`, null, { params: { type } }),

  // 弹幕
  danmaku: (id) => http.get(`/videos/${id}/danmaku`),
  sendDanmaku: (id, data) => http.post(`/videos/${id}/danmaku`, data),

  // 评论
  comments: (id, params) => http.get(`/videos/${id}/comments`, { params }),
  addComment: (id, data) => http.post(`/videos/${id}/comments`, data),

  // 番剧
  bangumi: (params) => http.get('/bangumi', { params }),
  followBangumi: (id) => http.post(`/bangumi/${id}/follow`),

  // 个人空间 / 动态
  space: (upId) => http.get(`/spaces/${upId}`),
  dynamics: (params) => http.get('/dynamics', { params }),

  // 搜索
  search: (params) => http.get('/search', { params }),
  suggest: (keyword) => http.get('/search/suggest', { params: { keyword } }),

  // 媒体（FFmpeg 扫描 / 封面）
  mediaStatus: () => http.get('/media/status'),
  mediaScan: () => http.post('/media/scan'),
  mediaSamples: () => http.post('/media/samples'),
  videoSources: (id) => http.get(`/media/sources/${id}`),

  // ---------------- 登录认证（/api/auth/**） ----------------
  auth: {
    status: () => http.get('/auth/status'),
    /** 图形验证码：purpose = LOGIN / REGISTER / RESET */
    captcha: (purpose = 'LOGIN') => http.get('/auth/captcha', { params: { purpose } }),
    /** 邮箱验证码 */
    sendEmailCode: (email, purpose = 'LOGIN') => http.post('/auth/email/send', { email, purpose }),
    register: (data) => http.post('/auth/register', data),
    loginPassword: (data) => http.post('/auth/login/password', data),
    loginEmail: (data) => http.post('/auth/login/email', data),
    resetPassword: (data) => http.post('/auth/password/reset', data),
    changePassword: (data) => http.post('/auth/password/change', data),
    bindEmail: (data) => http.post('/auth/email/bind', data),
    logout: () => http.post('/auth/logout'),

    // 二维码登录
    qrCreate: () => http.post('/auth/qr/create'),
    qrPoll: (qrId) => http.get('/auth/qr/poll', { params: { qrId } }),
    qrScan: (qrId) => http.post('/auth/qr/scan', { qrId }),
    qrConfirm: (data) => http.post('/auth/qr/confirm', data),
    qrCancel: (qrId) => http.post('/auth/qr/cancel', { qrId })
  },

  // ---------------- 用户 ----------------
  login: () => http.post('/user/login'),
  logout: () => http.post('/user/logout'),
  me: () => http.get('/user/me'),
  updateProfile: (data) => http.post('/user/profile', data),
  checkin: () => http.post('/user/checkin'),
  clearAvatar: () => http.delete('/user/avatar'),

  /** 上传头像：用 FormData 传文件 */
  uploadAvatar: (file) => {
    const form = new FormData()
    form.append('file', file)
    return http.post('/user/avatar', form, {
      headers: { 'Content-Type': 'multipart/form-data' },
      timeout: 30000
    })
  },

  // ---------------- 个人中心（需要登录） ----------------
  uc: {
    overview: () => http.get('/uc/overview'),
    history: (limit = 100) => http.get('/uc/history', { params: { limit } }),
    deleteHistory: (videoId) => http.delete(`/uc/history/${videoId}`),
    clearHistory: () => http.delete('/uc/history'),

    favorites: (params) => http.get('/uc/favorites', { params }),
    removeFavorite: (videoId) => http.delete(`/uc/favorites/${videoId}`),
    folders: () => http.get('/uc/folders'),
    createFolder: (data) => http.post('/uc/folders', data),
    deleteFolder: (folderId) => http.delete(`/uc/folders/${folderId}`),

    actions: (type = 'LIKE', limit = 100) => http.get('/uc/actions', { params: { type, limit } }),
    following: () => http.get('/uc/following'),
    follow: (upId) => http.post(`/uc/follow/${upId}`),

    messages: (type = 'ALL') => http.get('/uc/messages', { params: { type } }),
    readMessages: (id = 0) => http.post('/uc/messages/read', { id }),

    settings: () => http.get('/uc/settings'),
    saveSettings: (data) => http.post('/uc/settings', data),

    uploads: () => http.get('/uc/uploads'),
    deleteUpload: (id) => http.delete(`/uc/uploads/${id}`),
    /** 投稿：上传视频文件（后端会用 FFmpeg 自动截帧生成封面） */
    uploadVideo: (file, { title, node, description } = {}, onProgress) => {
      const form = new FormData()
      form.append('file', file)
      form.append('title', title || file.name)
      if (node) form.append('node', node)
      if (description) form.append('description', description)
      return http.post('/uc/uploads', form, {
        headers: { 'Content-Type': 'multipart/form-data' },
        timeout: 10 * 60 * 1000,
        onUploadProgress: onProgress
      })
    },

    searchHistory: () => http.get('/uc/search-history'),
    clearSearchHistory: () => http.delete('/uc/search-history')
  }
}

export default http
