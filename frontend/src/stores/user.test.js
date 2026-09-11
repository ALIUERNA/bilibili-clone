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

  it('登录后能拿到用户信息并写入 localStorage', () => {
    const store = useUserStore()
    store.setUser({ id: 1, name: '测试用户', level: 3, exp: 100, expMax: 200 })
    expect(store.isLogin).toBe(true)
    expect(store.user.name).toBe('测试用户')
    expect(JSON.parse(memory.get('bili-user')).name).toBe('测试用户')
  })

  it('经验百分比计算正确', () => {
    const store = useUserStore()
    store.setUser({ exp: 1200, expMax: 4800 })
    expect(store.expPercent).toBe(25)
    expect(store.expLeft).toBe(3600)
  })

  it('经验算满时百分比不会超过 100', () => {
    const store = useUserStore()
    store.setUser({ exp: 9999, expMax: 4800 })
    expect(store.expPercent).toBe(100)
    expect(store.expLeft).toBe(0)
  })

  it('applyExp 会把后端返回的最新经验同步到本地', () => {
    const store = useUserStore()
    store.setUser({ level: 5, exp: 100, expMax: 4800, coins: 10 })
    store.applyExp({ expGain: 5, level: 5, exp: 105, expMax: 4800, coins: 10 })
    expect(store.user.exp).toBe(105)
    expect(store.expFloats.length).toBe(1)
    expect(store.expFloats[0].text).toBe('+5 经验')
  })

  it('升级时会触发升级特效', () => {
    const store = useUserStore()
    store.setUser({ level: 4, exp: 10799, expMax: 10800 })
    store.applyExp({ expGain: 10, level: 5, exp: 9, expMax: 28800, levelUp: true })
    expect(store.user.level).toBe(5)
    expect(store.levelUp).toBe(true)
  })

  it('退出登录会清掉本地缓存', () => {
    const store = useUserStore()
    store.setUser({ name: '要被清掉的人' })
    store.logout()
    expect(store.isLogin).toBe(false)
    expect(memory.has('bili-user')).toBe(false)
  })
})
