/**
 * 轻量全局提示（替代 Element Plus 的 ElMessage）。
 *
 * 设计目标：一个 20 行的响应式队列就够了，不需要把整个 message 组件库打进来。
 * 视觉上沿用品牌语言：品牌渐变图标 + 毛玻璃胶囊，从顶部居中落下。
 *
 *   import { toast } from '../ui/toast'
 *   toast('已收藏')                          // 默认 info
 *   toast('保存失败', { type: 'error' })
 *   toast('登录成功', { type: 'success', duration: 3000 })
 *
 * 相同文案在 1.2s 内重复触发时只保留一条（grouping），避免连点刷屏。
 */

import { reactive, readonly } from 'vue'

const DURATION = 2200
const GROUP_WINDOW = 1200

let seed = 0

/** 当前展示中的提示列表（只读，供 <AiToast> 渲染） */
const state = reactive({ list: [] })

export const toasts = readonly(state)

/**
 * 弹出一条提示
 * @param {string} message 文案
 * @param {{ type?: 'info'|'success'|'error'|'warn', duration?: number }} [options]
 */
export function toast(message, options = {}) {
  const text = String(message ?? '').trim()
  if (!text) return

  const type = options.type || 'info'
  const duration = Number(options.duration) || DURATION

  // 合并：同文案 + 同类型的提示还在展示时，重置它的倒计时而不是新增一条
  const exist = state.list.find((t) => t.text === text && t.type === type)
  if (exist && Date.now() - exist.at < GROUP_WINDOW) {
    exist.at = Date.now()
    clearTimeout(exist.timer)
    exist.timer = setTimeout(() => remove(exist.id), duration)
    return
  }

  const item = {
    id: ++seed,
    text,
    type,
    at: Date.now(),
    timer: null
  }
  state.list.push(item)
  // 最多同时存在 4 条，多了就把最早的挤掉
  if (state.list.length > 4) remove(state.list[0].id)
  item.timer = setTimeout(() => remove(item.id), duration)
}

function remove(id) {
  const i = state.list.findIndex((t) => t.id === id)
  if (i === -1) return
  clearTimeout(state.list[i].timer)
  state.list.splice(i, 1)
}

/** 手动清空（路由切换时可用） */
export function clearToasts() {
  state.list.forEach((t) => clearTimeout(t.timer))
  state.list.splice(0, state.list.length)
}

/** 快捷方式 */
toast.success = (msg, opts) => toast(msg, { ...opts, type: 'success' })
toast.error = (msg, opts) => toast(msg, { ...opts, type: 'error' })
toast.warn = (msg, opts) => toast(msg, { ...opts, type: 'warn' })
