import axios from 'axios'

/**
 * 统一的接口请求封装。
 * 开发环境走 vite 代理（见 vite.config.js），生产环境前后端在同一个域名下，所以 baseURL 用 /api 就行。
 */
const http = axios.create({
  baseURL: '/api',
  timeout: 15000
})

// 响应拦截：直接把后端返回的 data 拿出来，页面里就不用每次写 res.data 了
http.interceptors.response.use(
  (response) => response.data,
  (error) => {
    const url = error?.config?.url || ''
    // 把后端的错误提示（比如「图片太大了」）挂到 error 上，方便页面直接显示
    error.friendlyMessage = error?.response?.data?.message || ''
    if (error?.response?.status === 404) {
      console.warn('[接口 404]', url)
    } else {
      console.warn('[接口异常]', url, error?.message)
    }
    return Promise.reject(error)
  }
)

export const api = {
  // 首页
  home: () => http.get('/home'),
  videos: (params) => http.get('/videos', { params }),
  rankings: (params) => http.get('/rankings', { params }),

  // 视频
  video: (id) => http.get(`/videos/${id}`),
  related: (id, limit = 12) => http.get(`/videos/${id}/related`, { params: { limit } }),
  addView: (id) => http.post(`/videos/${id}/view`),
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

  // 用户
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
  }
}

export default http
