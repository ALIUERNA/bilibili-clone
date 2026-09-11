import { defineStore } from 'pinia'
import { api } from '../api'

/**
 * 说明：下面用到的 ElMessage 由 unplugin-auto-import 按需自动引入
 * （见 vite.config.js），这样打包时只带上消息组件，不会把整个 Element Plus 拉进来。
 */

/**
 * 登录用户状态（Pinia）。
 *
 * - 昵称 / 头像 / 等级 / 经验 / 硬币 / 签到 都由后端保存（uploads/profile.json），
 *   刷新页面、重启服务都不会丢。
 * - 这里额外维护「经验 +N」的飘字动画队列和升级特效。
 */
export const useUserStore = defineStore('user', {
  state: () => ({
    user: JSON.parse(localStorage.getItem('bili-user') || 'null'),
    token: localStorage.getItem('bili-token') || '',
    loginVisible: false,
    profileVisible: false,
    toast: '',
    expFloats: [],   // [{ id, text, kind }]
    levelUp: false   // 升级特效开关
  }),

  getters: {
    isLogin: (state) => !!state.user,

    /** 经验条百分比 */
    expPercent: (state) => {
      const u = state.user
      if (!u || !u.expMax) return 0
      return Math.min(100, Math.round((u.exp / u.expMax) * 1000) / 10)
    },

    /** 距离升级还差多少经验 */
    expLeft: (state) => {
      const u = state.user
      if (!u) return 0
      return Math.max(0, (u.expMax || 0) - (u.exp || 0))
    }
  },

  actions: {
    openLogin() {
      this.loginVisible = true
    },

    openProfile() {
      if (!this.isLogin) {
        this.openLogin()
        this.showToast('请先登录哦~')
        return
      }
      this.profileVisible = true
    },

    closeProfile() {
      this.profileVisible = false
    },

    requireLogin() {
      if (this.isLogin) return true
      this.openLogin()
      this.showToast('请先登录哦~')
      return false
    },

    async login() {
      const res = await api.login()
      this.setUser(res.user)
      this.token = res.token
      localStorage.setItem('bili-token', res.token)
      this.loginVisible = false
      this.showToast(`欢迎回来，${res.user.name}！`)
      return res.user
    },

    logout() {
      this.user = null
      this.token = ''
      localStorage.removeItem('bili-user')
      localStorage.removeItem('bili-token')
      this.showToast('已退出登录')
    },

    setUser(user) {
      if (!user) return
      this.user = { ...(this.user || {}), ...user }
      localStorage.setItem('bili-user', JSON.stringify(this.user))
    },

    /** 从后端拉一次最新资料（等级经验可能有变化） */
    async refresh() {
      if (!this.isLogin) return
      try {
        const res = await api.me()
        if (res?.user) this.setUser(res.user)
      } catch (e) {
        /* 后端没起来就先用本地缓存 */
      }
    },

    /**
     * 把后端返回的经验变动应用到本地，并弹一个「+N 经验」飘字。
     * payload 形如 { expGain: 5, level: 5, exp: 3865, expMax: 4800, levelUp: false }
     */
    applyExp(payload) {
      if (!payload || !this.user) return
      const before = this.user.level
      this.setUser({
        level: payload.level ?? this.user.level,
        exp: payload.exp ?? this.user.exp,
        expMax: payload.expMax ?? this.user.expMax,
        coins: payload.coins ?? this.user.coins
      })
      if (payload.expGain > 0) {
        this.pushExpFloat(`+${payload.expGain} 经验`)
      }
      if (payload.levelUp || (payload.level && before && payload.level > before)) {
        this.playLevelUp(payload.level || this.user.level)
      }
    },

    /** 屏幕上飘一个「+5 经验」 */
    pushExpFloat(text, kind = 'exp') {
      const id = Date.now() + Math.random()
      this.expFloats.push({ id, text, kind })
      setTimeout(() => {
        this.expFloats = this.expFloats.filter((f) => f.id !== id)
      }, 1600)
    },

    /** 升级特效 */
    playLevelUp(level) {
      this.levelUp = true
      this.showToast(`🎉 恭喜升级！你现在是 Lv${level} 啦`)
      setTimeout(() => {
        this.levelUp = false
      }, 2600)
    },

    /** 修改昵称 / 签名 */
    async updateProfile({ name, sign }) {
      const res = await api.updateProfile({ name, sign })
      if (res?.user) this.setUser(res.user)
      this.showToast(res?.message || '资料已更新')
      return res
    },

    /** 上传头像（选文件后立刻上传，页面马上就能看到新头像） */
    async uploadAvatar(file) {
      if (!file) return
      const res = await api.uploadAvatar(file)
      if (res?.faceUrl) {
        this.setUser({ faceUrl: res.faceUrl })
      }
      this.showToast(res?.message || '头像已更新')
      return res
    },

    /** 改回 emoji 头像 */
    async clearAvatar() {
      const res = await api.clearAvatar()
      if (res?.user) this.setUser(res.user)
      this.showToast(res?.message || '已改回表情头像')
      return res
    },

    /** 每日签到 */
    async checkin() {
      const res = await api.checkin()
      if (res?.user) this.setUser(res.user)
      if (res?.success) {
        this.pushExpFloat('+10 经验')
        if (res.levelUp) this.playLevelUp(res.user?.level)
      }
      this.showToast(res?.message || '签到完成')
      return res
    },

    /** 全局提示：用 Element Plus 的 Message，统一风格（粉色边框呼应 B 站） */
    showToast(text) {
      this.toast = text
      ElMessage({
        message: text,
        duration: 2200,
        grouping: true,
        customClass: 'bili-message'
      })
    },
  }
})
