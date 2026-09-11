/**
 * 一个极简的 Chrome DevTools Protocol 客户端，给验收脚本用。
 * 只依赖 Node 自带的 fetch / WebSocket，不需要装 puppeteer。
 */
import { spawn } from 'node:child_process'

const CHROME = 'C:/Program Files/Google/Chrome/Application/chrome.exe'

export const sleep = (ms) => new Promise((r) => setTimeout(r, ms))

export class CDP {
  constructor(ws) {
    this.ws = ws
    this.id = 0
    this.pending = new Map()
    this.handlers = []
    ws.addEventListener('message', (ev) => {
      const msg = JSON.parse(ev.data)
      if (msg.id && this.pending.has(msg.id)) {
        const { resolve, reject } = this.pending.get(msg.id)
        this.pending.delete(msg.id)
        msg.error ? reject(new Error(JSON.stringify(msg.error))) : resolve(msg.result)
      } else {
        this.handlers.forEach((h) => h(msg))
      }
    })
  }

  send(method, params = {}, sessionId) {
    const id = ++this.id
    const payload = { id, method, params }
    if (sessionId) payload.sessionId = sessionId
    this.ws.send(JSON.stringify(payload))
    return new Promise((resolve, reject) => {
      this.pending.set(id, { resolve, reject })
      setTimeout(() => {
        if (this.pending.has(id)) {
          this.pending.delete(id)
          reject(new Error(`CDP 超时: ${method}`))
        }
      }, 30000)
    })
  }

  on(fn) {
    this.handlers.push(fn)
  }
}

/** 启动无头 Chrome 并打开一个页面，返回一组好用的封装方法 */
export async function openBrowser({ port = 9224, profile, width = 1680, height = 1050 } = {}) {
  const chrome = spawn(
    CHROME,
    [
      '--headless=new',
      '--disable-gpu',
      '--no-first-run',
      '--no-default-browser-check',
      '--hide-scrollbars',
      `--remote-debugging-port=${port}`,
      `--user-data-dir=${profile}`,
      `--window-size=${width},${height}`,
      'about:blank'
    ],
    { stdio: 'ignore' }
  )

  let version = null
  for (let i = 0; i < 60; i++) {
    try {
      const res = await fetch(`http://127.0.0.1:${port}/json/version`)
      if (res.ok) {
        version = await res.json()
        break
      }
    } catch (e) {
      /* 还没起来 */
    }
    await sleep(500)
  }
  if (!version) throw new Error('Chrome 启动失败')

  const ws = new WebSocket(version.webSocketDebuggerUrl)
  await new Promise((r) => ws.addEventListener('open', r, { once: true }))
  const cdp = new CDP(ws)

  const { targetId } = await cdp.send('Target.createTarget', { url: 'about:blank' })
  const { sessionId } = await cdp.send('Target.attachToTarget', { targetId, flatten: true })

  const errors = []
  cdp.on((msg) => {
    if (msg.method === 'Runtime.exceptionThrown') {
      errors.push('JS异常: ' + (msg.params?.exceptionDetails?.exception?.description || '').slice(0, 300))
    }
    if (msg.method === 'Runtime.consoleAPICalled' && msg.params.type === 'error') {
      errors.push('console.error: ' + JSON.stringify(msg.params.args?.map((a) => a.value || a.description)).slice(0, 300))
    }
    if (msg.method === 'Log.entryAdded' && msg.params.entry.level === 'error') {
      errors.push('日志错误: ' + msg.params.entry.text.slice(0, 300))
    }
  })

  await cdp.send('Page.enable', {}, sessionId)
  await cdp.send('Runtime.enable', {}, sessionId)
  await cdp.send('Log.enable', {}, sessionId)
  await cdp.send('Network.enable', {}, sessionId)

  const api = {
    cdp,
    sessionId,
    errors,

    async eval(expression) {
      const { result, exceptionDetails } = await cdp.send(
        'Runtime.evaluate',
        { expression, returnByValue: true, awaitPromise: true },
        sessionId
      )
      if (exceptionDetails) {
        throw new Error('页面执行出错: ' + JSON.stringify(exceptionDetails).slice(0, 300))
      }
      return result.value
    },

    /** 点击某个元素（用 querySelector 找到就点） */
    async click(selector) {
      const ok = await api.eval(`(() => {
        const el = document.querySelector(${JSON.stringify(selector)});
        if (!el) return false;
        el.click();
        return true;
      })()`)
      if (!ok) throw new Error(`找不到元素: ${selector}`)
      return ok
    },

    /** 往输入框里填内容（触发 Vue 的 v-model） */
    async type(selector, text) {
      const ok = await api.eval(`(() => {
        const el = document.querySelector(${JSON.stringify(selector)});
        if (!el) return false;
        const setter = Object.getOwnPropertyDescriptor(el.constructor.prototype, 'value').set;
        setter.call(el, ${JSON.stringify(text)});
        el.dispatchEvent(new Event('input', { bubbles: true }));
        return true;
      })()`)
      if (!ok) throw new Error(`找不到输入框: ${selector}`)
      return ok
    },

    /** 回车 */
    async enter(selector) {
      await api.eval(`(() => {
        const el = document.querySelector(${JSON.stringify(selector)});
        if (!el) return false;
        el.dispatchEvent(new KeyboardEvent('keyup', { key: 'Enter', code: 'Enter', bubbles: true }));
        return true;
      })()`)
    },

    async goto(url, waitMs = 2000) {
      await cdp.send('Page.navigate', { url }, sessionId)
      await sleep(waitMs)
    },

    /**
     * 强制整页刷新。
     * 注意：只改 URL 里的 #hash 时 Chrome 不会重新加载页面，
     * Vue 里的内存状态（比如登录态）还是旧的，所以清 localStorage 之后要调这个。
     */
    async reload(waitMs = 2500) {
      await cdp.send('Page.reload', { ignoreCache: true }, sessionId)
      await sleep(waitMs)
    },

    async waitFor(fn, { timeout = 8000, interval = 250 } = {}) {
      const start = Date.now()
      while (Date.now() - start < timeout) {
        try {
          const v = await fn()
          if (v) return v
        } catch (e) {
          /* 继续等 */
        }
        await sleep(interval)
      }
      return null
    },

    close() {
      try {
        chrome.kill()
      } catch (e) {}
    }
  }

  return api
}
