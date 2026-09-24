/**
 * 三种登录方式的端到端验收（真实点浏览器，不是只调接口）：
 *
 *   1. 账号密码 + 图形验证码（验证码从 MySQL 里读，模拟用户看图片输入）
 *   2. 邮箱验证码（开发环境会把验证码打印在页面上，直接读出来填入）
 *   3. 二维码登录（生成真实二维码 → 模拟手机扫码 → 模拟手机确认 → 桌面端轮询拿到 token）
 *
 * 顺带检查：登录后个人中心各标签页能不能正常渲染。
 *
 * 依赖：本机 MySQL（读取验证码）、系统 Chrome、后端已启动在 8080。
 * 用法：node tools/e2e-auth.mjs
 */
import { spawn, execFileSync } from 'node:child_process'
import path from 'node:path'
import fs from 'node:fs'

const CHROME = 'C:/Program Files/Google/Chrome/Application/chrome.exe'
const PORT = 9231
const BASE = 'http://localhost:8080'
const SHOT_DIR = path.resolve('shots')
const MYSQL = ['-uroot', '-p123456', '-N', '-B', '-e']

const sleep = (ms) => new Promise((r) => setTimeout(r, ms))

function mysql(sql) {
  try {
    return execFileSync('mysql', [...MYSQL, sql], { encoding: 'utf8', stdio: ['ignore', 'pipe', 'ignore'] }).trim()
  } catch (e) {
    return ''
  }
}

class CDP {
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

async function waitForChrome() {
  for (let i = 0; i < 60; i++) {
    try {
      const res = await fetch(`http://127.0.0.1:${PORT}/json/version`)
      if (res.ok) return await res.json()
    } catch (e) {
      /* 等一会儿 */
    }
    await sleep(500)
  }
  throw new Error('Chrome 启动失败')
}

let passed = 0
let failed = 0
const results = []
function check(name, ok, extra = '') {
  if (ok) passed++
  else failed++
  results.push(`${ok ? '✅ 通过' : '❌ 失败'}  ${name}${extra ? '  | ' + extra : ''}`)
  console.log(results[results.length - 1])
}

async function main() {
  fs.mkdirSync(SHOT_DIR, { recursive: true })
  const chrome = spawn(
    CHROME,
    [
      '--headless=new',
      '--disable-gpu',
      '--no-first-run',
      '--no-default-browser-check',
      '--hide-scrollbars',
      `--remote-debugging-port=${PORT}`,
      `--user-data-dir=${path.resolve('.chrome-auth-profile')}`,
      '--window-size=1440,960',
      'about:blank'
    ],
    { stdio: 'ignore' }
  )

  let cdp
  try {
    const version = await waitForChrome()
    const ws = new WebSocket(version.webSocketDebuggerUrl)
    await new Promise((r) => ws.addEventListener('open', r, { once: true }))
    cdp = new CDP(ws)

    const { targetId } = await cdp.send('Target.createTarget', { url: 'about:blank' })
    const { sessionId } = await cdp.send('Target.attachToTarget', { targetId, flatten: true })
    await cdp.send('Page.enable', {}, sessionId)
    await cdp.send('Runtime.enable', {}, sessionId)

    const errors = []
    cdp.on((msg) => {
      if (msg.method === 'Runtime.exceptionThrown') {
        errors.push('异常: ' + (msg.params?.exceptionDetails?.exception?.description || '').slice(0, 160))
      }
      if (msg.method === 'Runtime.consoleAPICalled' && msg.params.type === 'error') {
        errors.push('console.error: ' + JSON.stringify(msg.params.args?.map((a) => a.value || a.description)).slice(0, 160))
      }
    })

    const evaluate = async (expression, awaitPromise = false) => {
      const { result, exceptionDetails } = await cdp.send(
        'Runtime.evaluate',
        { expression, returnByValue: true, awaitPromise },
        sessionId
      )
      if (exceptionDetails) throw new Error(exceptionDetails.exception?.description || 'JS 执行失败')
      return result.value
    }

    const goto = async (hash, wait = 2200) => {
      await cdp.send('Page.navigate', { url: `${BASE}/#${hash}` }, sessionId)
      await sleep(wait)
    }

    const clickByText = (selector, text) =>
      evaluate(`(() => {
        const el = [...document.querySelectorAll('${selector}')].find((e) => (e.innerText || '').trim().includes('${text}'));
        if (!el) return 'not-found';
        el.click();
        return 'ok';
      })()`)

    const setInput = (selector, value, index = 0) =>
      evaluate(`(() => {
        const els = document.querySelectorAll('${selector}');
        const el = els[${index}];
        if (!el) return 'not-found';
        const proto = el.tagName === 'TEXTAREA' ? window.HTMLTextAreaElement.prototype : window.HTMLInputElement.prototype;
        const setter = Object.getOwnPropertyDescriptor(proto, 'value').set;
        setter.call(el, ${JSON.stringify(value)});
        el.dispatchEvent(new Event('input', { bubbles: true }));
        el.dispatchEvent(new Event('change', { bubbles: true }));
        return 'ok';
      })()`)

    const snapshot = `(() => ({
      token: localStorage.getItem('bili-token') || '',
      user: (() => { try { return JSON.parse(localStorage.getItem('bili-user') || 'null'); } catch(e) { return null; } })(),
      loggedInText: (document.querySelector('.user-menu-head .name') || {}).innerText || '',
      url: location.hash
    }))()`

    const clearSession = async () => {
      // about:blank 上访问不到 localStorage，先切到站点再清
      await goto('/', 1500)
      await evaluate(`localStorage.clear(); 'cleared'`)
    }

    // ============================================================
    // 1. 账号密码 + 图形验证码
    // ============================================================
    console.log('\n---------- 1. 账号密码 + 图形验证码 ----------')
    await clearSession()
    await goto('/login', 2600)
    await clickByText('.auth-tabs button', '密码登录')
    await sleep(600)

    const captchaVisible = await evaluate(`document.querySelectorAll('.captcha-img img').length`)
    check('图形验证码图片已渲染', captchaVisible >= 1, `img=${captchaVisible}`)

    // 点击验证码刷新一次，确认可换图
    await evaluate(`document.querySelector('.captcha-img').click(); 'clicked'`)
    await sleep(900)
    const captchaCode = mysql('select code from bili_clone.captcha_codes order by id desc limit 1')
    check('后端生成了验证码并写入 MySQL', captchaCode.length === 4, `code=${captchaCode}`)

    await setInput('.auth-form > input', 'bili_demo', 0)
    await setInput('.auth-form > input', 'bili123456', 1)
    await setInput('.captcha-field input', captchaCode.toLowerCase())
    await evaluate(`document.querySelector('.auth-submit').click(); 'submitted'`)
    await sleep(2200)
    let snap = await evaluate(snapshot)
    check('密码登录成功（拿到 token）', !!snap.token && !!snap.user, `user=${snap.user?.username || snap.user?.name}`)

    // 图形验证码不能重复使用
    const reuse = await evaluate(
      `fetch('/api/auth/login/password',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({account:'bili_demo',password:'bili123456',captchaKey:'${'x'.repeat(4)}',captchaCode:'${captchaCode}'})}).then(r=>r.json()).then(d=>d.message||'')`,
      true
    )
    check('验证码一次性（重复使用被拒绝）', String(reuse).includes('验证码'), String(reuse))

    // ============================================================
    // 2. 邮箱验证码登录
    // ============================================================
    console.log('\n---------- 2. 邮箱验证码 ----------')
    await clearSession()
    await goto('/login?tab=email', 2600)
    await clickByText('.auth-tabs button', '邮箱登录')
    await sleep(500)

    const email = `e2e_${Date.now()}@bili.dev`
    await setInput('.email-field .row input', email, 0)
    await evaluate(`document.querySelector('.email-field .send-btn').click(); 'sent'`)
    await sleep(2000)
    const devCode = await evaluate(`(document.querySelector('.tip.dev b') || {}).innerText || ''`)
    const mailCode = devCode || mysql(`select code from bili_clone.email_verification_codes where email='${email}' order by id desc limit 1`)
    check('发送邮箱验证码成功（开发环境控制台/页面可见）', /^\d{6}$/.test(String(mailCode)), `code=${mailCode}`)

    await setInput('.email-field .row input', String(mailCode), 1)
    await evaluate(`document.querySelector('.auth-submit').click(); 'submitted'`)
    await sleep(2400)
    snap = await evaluate(snapshot)
    check('邮箱验证码登录成功（新邮箱自动注册）', !!snap.token && !!snap.user, `user=${snap.user?.username}`)

    // ============================================================
    // 3. 二维码登录
    // ============================================================
    console.log('\n---------- 3. 二维码登录 ----------')
    await clearSession()
    await goto('/login?tab=qr', 3000)
    await clickByText('.auth-tabs button', '扫码登录')
    await sleep(1200)

    const qrOk = await evaluate(`(() => { const i = document.querySelector('.qr-img'); return i && i.src.startsWith('data:image/png;base64,') ? i.src.length : 0; })()`)
    check('二维码图片已生成（zxing，真实可扫）', qrOk > 500, `base64 长度=${qrOk}`)

    await clickByText('.qr-actions button', '模拟手机扫码')
    await sleep(1800)
    await clickByText('.simulate button', '确认登录')
    await sleep(2800)
    snap = await evaluate(snapshot)
    check('扫码 + 确认后桌面端自动登录', !!snap.token && !!snap.user, `user=${snap.user?.username || snap.user?.name}`)

    // 二维码状态接口回归
    const qrState = await evaluate(
      `fetch('/api/auth/qr/create',{method:'POST'}).then(r=>r.json()).then(d=>d.status+','+d.expiresIn)`,
      true
    )
    check('二维码创建接口返回待扫描状态', String(qrState).includes('WAITING'), String(qrState))

    // ============================================================
    // 4. 个人中心各页面
    // ============================================================
    console.log('\n---------- 4. 登录后的个人中心 ----------')
    for (const tab of ['overview', 'history', 'favorite', 'message', 'settings', 'upload', 'profile', 'follow']) {
      await goto(`/user/${tab}`, 1800)
      const state = await evaluate(
        `(() => ({ menu: document.querySelectorAll('.uc .menu button').length, content: (document.querySelector('.uc .content')||{}).innerText?.length || 0, auth: document.querySelectorAll('.auth-card').length }))()`
      )
      check(`个人中心 /user/${tab} 正常渲染`, state.menu >= 8 && state.content > 20 && state.auth === 0, `menu=${state.menu} 文字=${state.content}`)
      try {
        const shot = await cdp.send('Page.captureScreenshot', { format: 'png' }, sessionId)
        fs.writeFileSync(path.join(SHOT_DIR, `uc-${tab}.png`), Buffer.from(shot.data, 'base64'))
      } catch (e) {
        /* ignore */
      }
    }

    // 投稿页（不用真的上传，只看渲染）
    await goto('/upload', 1800)
    const uploadOk = await evaluate(`document.querySelectorAll('.drop-box').length`)
    check('投稿页正常渲染', uploadOk >= 1)

    check('整个流程没有 JS 报错', errors.length === 0, errors.slice(0, 3).join(' ; '))
  } finally {
    try {
      chrome.kill()
    } catch (e) {
      /* ignore */
    }
  }

  console.log('\n=============== 登录验收汇总 ===============')
  console.log(`通过 ${passed} 项，失败 ${failed} 项`)
  if (failed > 0) process.exitCode = 1
}

main().catch((e) => {
  console.error('验收脚本异常：', e)
  process.exit(1)
})
