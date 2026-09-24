import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'

/**
 * 这个测试跑在 Node 环境里，没有浏览器，所以先给 localStorage 打个桩。
 * 注意：必须在 import store 之前 stub，因为 store 模块顶层就会读 localStorage。
 */
const memory = new Map()
vi.stubGlobal('localStorage', {
  getItem: (k) => (memory.has(k) ? memory.get(k) : null),
  setItem: (k, v) => memory.set(k, String(v)),
  removeItem: (k) => memory.delete(k),
  clear: () => memory.clear()
})

// 后端接口打桩：登录相关的方法返回假数据，避免测试真的发请求
vi.mock('../api', () => {
  const api = {
    login: vi.fn(async () => ({ token: 'demo-token', user: { id: 90001, name: '演示账号' } })),
    me: vi.fn(async () => ({})),
    logout: vi.fn(async () => ({ success: true })),
    updateProfile: vi.fn(async () => ({ user: { name: '新名字' }, message: 'ok' })),
    checkin: vi.fn(async () => ({ success: true, user: { coins: 6 }, message: '签到成功' })),
    auth: {
      status: vi.fn(async () => ({ mysqlMode: true, devMode: true })),
      loginPassword: vi.fn(async () => ({ success: true, token: 'pw-token', user: { id: 1, name: '密码登录用户' } })),
      loginEmail: vi.fn(async () => ({ success: true, token: 'mail-token', user: { id: 2, name: '邮箱登录用户' } })),
      logout: vi.fn(async () => ({ success: true }))
    }
  }
  return {
    api,
    getToken: () => memory.get('bili-token') || '',
    setToken: (t) => (t ? memory.set('bili-token', t) : memory.delete('bili-token')),
    default: {}
  }
})

const { useUserStore } = await import('./user')

describe('用户状态 store', () => {
  beforeEach(() => {
    memory.clear()
    setActivePinia(createPinia())
  })

  it('未登录时 isLogin 为 false，需要登录的操作会被拦下', () => {
    const store = useUserStore()
    expect(store.isLogin).toBe(false)
    expect(store.requireLogin()).toBe(false)
    expect(store.loginVisible).toBe(true)
  })

  it('登录成功后能拿到用户信息、token 并写入 localStorage', () => {
    const store = useUserStore()
    store.setSession({ token: 'abc123', user: { id: 1, name: '测试用户', level: 3 } })
    expect(store.isLogin).toBe(true)
    expect(store.token).toBe('abc123')
    expect(memory.get('bili-token')).toBe('abc123')
    expect(JSON.parse(memory.get('bili-user')).name).toBe('测试用户')
  })

  it('setUser 只更新资料，不会把游客变成登录状态', () => {
    const store = useUserStore()
    // 游客态：不允许把演示账号写进本地用户
    store.setUser({ id: 1, name: '游客资料' })
    expect(store.user).toBe(null)
    expect(store.isLogin).toBe(false)

    // 登录后才允许更新资料
    store.setSession({ token: 't', user: { id: 2, name: '真实用户', level: 3 } })
    store.setUser({ name: '新昵称' })
    expect(store.user.name).toBe('新昵称')
    expect(store.user.id).toBe(2)
    expect(store.isLogin).toBe(true)
  })

  it('经验百分比计算正确', () => {
    const store = useUserStore()
    store.setSession({ token: 't', user: { exp: 1200, expMax: 4800 } })
    expect(store.expPercent).toBe(25)
    expect(store.expLeft).toBe(3600)
  })

  it('经验算满时百分比不会超过 100', () => {
    const store = useUserStore()
    store.setSession({ token: 't', user: { exp: 9999, expMax: 4800 } })
    expect(store.expPercent).toBe(100)
    expect(store.expLeft).toBe(0)
  })

  it('applyExp 会把后端返回的最新经验同步到本地', () => {
    const store = useUserStore()
    store.setSession({ token: 't', user: { level: 5, exp: 100, expMax: 4800, coins: 10 } })
    store.applyExp({ expGain: 5, level: 5, exp: 105, expMax: 4800, coins: 10 })
    expect(store.user.exp).toBe(105)
    expect(store.expFloats.length).toBe(1)
    expect(store.expFloats[0].text).toBe('+5 经验')
  })

  it('升级时会触发升级特效', () => {
    const store = useUserStore()
    store.setSession({ token: 't', user: { level: 4, exp: 10799, expMax: 10800 } })
    store.applyExp({ expGain: 10, level: 5, exp: 9, expMax: 28800, levelUp: true })
    expect(store.user.level).toBe(5)
    expect(store.levelUp).toBe(true)
  })

  it('退出登录会清掉本地缓存和 token', async () => {
    const store = useUserStore()
    store.setSession({ token: 'abc', user: { name: '要被清掉的人' } })
    expect(store.isLogin).toBe(true)
    await store.logout()
    expect(store.isLogin).toBe(false)
    expect(memory.has('bili-user')).toBe(false)
    expect(memory.has('bili-token')).toBe(false)
  })

  // ---------------- 三种登录方式 ----------------

  it('账号密码 + 图形验证码登录成功后写入会话', async () => {
    const store = useUserStore()
    const res = await store.loginWithPassword({
      account: 'tester',
      password: 'abc123',
      captchaKey: 'key',
      captchaCode: 'AB12'
    })
    expect(res.success).toBe(true)
    expect(store.isLogin).toBe(true)
    expect(store.user.name).toBe('密码登录用户')
    expect(memory.get('bili-token')).toBe('pw-token')
  })

  it('登录失败时不会写入登录态', async () => {
    const { api } = await import('../api')
    api.auth.loginPassword.mockResolvedValueOnce({ success: false, message: '账号或密码错误' })
    const store = useUserStore()
    const res = await store.loginWithPassword({ account: 'x', password: 'y', captchaKey: 'k', captchaCode: 'c' })
    expect(res.success).toBe(false)
    expect(res.message).toBe('账号或密码错误')
    expect(store.isLogin).toBe(false)
  })

  it('邮箱验证码登录成功（首次会自动注册）', async () => {
    const store = useUserStore()
    const res = await store.loginWithEmail({ email: 'a@b.com', code: '123456' })
    expect(res.success).toBe(true)
    expect(store.isLogin).toBe(true)
    expect(store.user.name).toBe('邮箱登录用户')
  })

  it('二维码确认后可以用拿到的 token 建立会话', async () => {
    const store = useUserStore()
    await store.loginWithQr({ token: 'qr-token', user: { id: 3, name: '扫码用户' } })
    expect(store.isLogin).toBe(true)
    expect(store.token).toBe('qr-token')
    expect(store.user.name).toBe('扫码用户')
    expect(memory.get('bili-token')).toBe('qr-token')
  })

  it('每日签到会加经验飘字', async () => {
    const store = useUserStore()
    store.setSession({ token: 't', user: { id: 1, name: 'u', exp: 0, expMax: 200, coins: 1 } })
    const res = await store.checkin()
    expect(res.success).toBe(true)
    expect(store.expFloats.length).toBe(1)
    expect(store.user.coins).toBe(6)
  })

  // ---------------- 登录态防覆盖 ----------------

  it('refresh 遇到游客响应不会把演示账号合并进本地用户', async () => {
    const { api } = await import('../api')
    api.me.mockResolvedValueOnce({ login: false, user: { id: 90001, name: 'a哩a哩萌新' } })
    const store = useUserStore()
    store.setSession({ token: 'real-token', user: { id: 2, name: '真实用户' } })

    await store.refresh()

    expect(store.isLogin).toBe(false)
    expect(store.user).toBe(null)
    expect(store.token).toBe('')
    expect(memory.has('bili-user')).toBe(false)
    expect(memory.has('bili-token')).toBe(false)
  })

  it('refresh 在过期响应返回时不会覆盖新会话（sessionSeq）', async () => {
    const { api } = await import('../api')
    let resolveMe
    api.me.mockImplementationOnce(
      () => new Promise((resolve) => { resolveMe = resolve })
    )
    const store = useUserStore()
    store.setSession({ token: 'old-token', user: { id: 1, name: '旧用户' } })

    const pending = store.refresh()
    // 刷新还没回来就重新登录
    store.setSession({ token: 'new-token', user: { id: 2, name: '新用户' } })
    resolveMe({ login: false, user: { id: 90001, name: 'a哩a哩萌新' } })
    await pending

    expect(store.isLogin).toBe(true)
    expect(store.user.name).toBe('新用户')
    expect(memory.get('bili-token')).toBe('new-token')
  })
})
