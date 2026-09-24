import { defineStore } from 'pinia'
import { api, setToken, getToken } from '../api'
import { toast } from '../ui/toast'

/**
 * 登录用户状态（Pinia）。
 *
 * - 真正的账号体系在后端：账号密码（图形验证码）/ 邮箱验证码 / 二维码 三种登录方式；
 *   登录成功后拿到 token，存在 localStorage，所有请求自动带上 Authorization 头。
 * - 昵称 / 头像 / 等级 / 经验 / 硬币 / 签到 都存在 MySQL（users 表）。
 * - 这里额外维护「经验 +N」的飘字动画队列和升级特效。
 */
export const useUserStore = defineStore('user', {
  state: () => ({
    user: JSON.parse(localStorage.getItem('bili-user') || 'null'),
    token: getToken(),
    /** 是否真正登录过（/user/me 返回 login=false 说明当前只是游客用的演示账号） */
    loggedIn: !!getToken(),
    /** 会话版本号：登录/退出/失效时 +1，用于丢弃过期的 /user/me 响应 */
    sessionSeq: 0,
    loginVisible: false,
    profileVisible: false,
    loginRedirect: '',
    toast: '',
    expFloats: [], // [{ id, text, kind }]
    levelUp: false, // 升级特效开关
    authStatus: { mysqlMode: true, devMode: true }
  }),

  getters: {
    isLogin: (state) => !!state.loggedIn && !!state.user,

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
    // ---------------- 弹窗控制 ----------------
    openLogin(redirect = '') {
      this.loginRedirect = redirect || ''
      this.loginVisible = true
    },

    closeLogin() {
      this.loginVisible = false
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

    requireLogin(redirect = '') {
      if (this.isLogin) return true
      this.openLogin(redirect)
      this.showToast('请先登录哦~')
      return false
    },

    // ---------------- 会话 ----------------
    /** 登录成功：把后端返回的 token + user 存下来 */
    setSession(res) {
      if (!res || !res.user) return
      this.sessionSeq += 1
      this.user = res.user
      this.loggedIn = true
      if (res.token) {
        this.token = res.token
        setToken(res.token)
      }
      this.persistUser()
    },

    /** 清空本地会话（退出登录 / token 失效时用） */
    clearSession() {
      this.sessionSeq += 1
      this.user = null
      this.token = ''
      this.loggedIn = false
      setToken('')
      try {
        localStorage.removeItem('bili-user')
      } catch (e) {
        /* ignore */
      }
    },

    persistUser() {
      try {
        localStorage.setItem('bili-user', JSON.stringify(this.user))
      } catch (e) {
        /* ignore */
      }
    },

    /** 读取后端登录环境（数据库是否可用、是否开发模式） */
    async loadAuthStatus() {
      try {
        const res = await api.auth.status()
        this.authStatus = { ...this.authStatus, ...res }
      } catch (e) {
        /* 后端没起来就不管 */
      }
      return this.authStatus
    },

    /** 账号密码 + 图形验证码登录 */
    async loginWithPassword({ account, password, captchaKey, captchaCode }) {
      const res = await api.auth.loginPassword({ account, password, captchaKey, captchaCode })
      if (!res.success) {
        return res
      }
      this.setSession(res)
      this.loginVisible = false
      this.showToast(`欢迎回来，${res.user.name}！`)
      await this.afterLogin()
      return res
    },

    /** 邮箱验证码登录（邮箱没注册过会自动创建账号） */
    async loginWithEmail({ email, code }) {
      const res = await api.auth.loginEmail({ email, code })
      if (!res.success) {
        return res
      }
      this.setSession(res)
      this.loginVisible = false
      this.showToast(res.message || `欢迎回来，${res.user.name}！`)
      await this.afterLogin()
      return res
    },

    /** 二维码确认后，前端轮询拿到 token 时调用 */
    async loginWithQr(payload) {
      this.setSession(payload)
      this.loginVisible = false
      this.showToast('扫码登录成功，欢迎回来！')
      await this.afterLogin()
    },

    async afterLogin() {
      const redirect = this.loginRedirect
      this.loginRedirect = ''
      // 等 /user/me 回来再放行跳转，避免游客响应/竞态把刚建立的会话冲掉
      await this.refresh()
      return redirect
    },

    /** 演示账号一键登录（保底入口：数据库还没准备好时也能体验） */
    async login() {
      const res = await api.login()
      this.setSession(res)
      this.loginVisible = false
      this.showToast(`欢迎回来，${res.user?.name || '同学'}！`)
      return res.user
    },

    async logout() {
      try {
        await api.auth.logout()
      } catch (e) {
        /* 忽略网络错误 */
      }
      this.clearSession()
      this.showToast('已退出登录')
    },

    setUser(user) {
      if (!user || !this.loggedIn) return
      this.user = { ...(this.user || {}), ...user }
      this.persistUser()
    },

    /** 从后端拉一次最新资料（等级经验可能有变化） */
    async refresh() {
      const seq = this.sessionSeq
      try {
        const res = await api.me()
        // 期间又登录/退出过，这次响应已经过期，直接丢弃
        if (seq !== this.sessionSeq) return
        if (res?.login === true && res.user && this.token) {
          this.user = res.user
          this.loggedIn = true
          this.persistUser()
          return
        }
        // 后端明确表示当前是游客/令牌失效：清掉本地登录态，但绝不把演示账号写进本地
        if (res && res.login === false) {
          this.clearSession()
        }
      } catch (e) {
        /* 网络异常时保留本地会话，等下一次刷新 */
      }
    },

    // ---------------- 经验 / 等级 ----------------
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

    pushExpFloat(text, kind = 'exp') {
      const id = Date.now() + Math.random()
      this.expFloats.push({ id, text, kind })
      setTimeout(() => {
        this.expFloats = this.expFloats.filter((f) => f.id !== id)
      }, 1600)
    },

    playLevelUp(level) {
      this.levelUp = true
      this.showToast(`恭喜升级！你现在是 Lv${level} 啦`)
      setTimeout(() => {
        this.levelUp = false
      }, 2600)
    },

    // ---------------- 资料 ----------------
    async updateProfile({ name, sign }) {
      const res = await api.updateProfile({ name, sign })
      if (res?.user) this.setUser(res.user)
      this.showToast(res?.message || '资料已更新')
      return res
    },

    async uploadAvatar(file) {
      if (!file) return
      const res = await api.uploadAvatar(file)
      if (res?.faceUrl) {
        this.setUser({ faceUrl: res.faceUrl })
      }
      this.showToast(res?.message || '头像已更新')
      return res
    },

    async clearAvatar() {
      const res = await api.clearAvatar()
      if (res?.user) this.setUser(res.user)
      this.showToast(res?.message || '已改回表情头像')
      return res
    },

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

    /** 全局提示：走 a哩a哩 自己的轻量 toast（见 src/ui/toast.js） */
    showToast(text, type = 'info') {
      this.toast = text
      toast(text, { type })
    }
  }
})
