/**
 * 自动化验收脚本（curl / 截图之外的另一层保险）
 *
 * 它会启动一个无头 Chrome，逐个打开页面，检查：
 *   1. 关键元素有没有渲染出来（卡片数量、标题、播放器等）
 *   2. 有没有 JS 报错、资源加载失败
 *   3. 点一下播放按钮，看看进度和弹幕有没有真的动起来
 *
 * 用法：node tools/verify.mjs
 */
import { spawn } from 'node:child_process'
import fs from 'node:fs'
import path from 'node:path'

const CHROME = 'C:/Program Files/Google/Chrome/Application/chrome.exe'
const PORT = 9223
const BASE = 'http://localhost:8080'
const SHOT_DIR = path.resolve('shots')

const sleep = (ms) => new Promise((r) => setTimeout(r, ms))

// ---------- 一个极简的 CDP 客户端 ----------
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
      /* 还没起来 */
    }
    await sleep(500)
  }
  throw new Error('Chrome 启动失败')
}

const PROBE = `(() => {
  const q = (s) => document.querySelectorAll(s).length;
  const text = document.body.innerText || '';
  return {
    title: document.title,
    bodyLength: text.length,
    vcards: q('.vcard'),
    links: q('a'),
    banners: q('.banner-slide'),
    channels: q('.channel'),
    player: q('.player'),
    danmakuLayer: q('.danmaku-layer'),
    danmakuItems: q('.danmaku-item'),
    comments: q('.comment'),
    related: q('.vrow'),
    rankRows: q('.vrank'),
    bangumiCards: q('.bg-card'),
    dynCards: q('.dyn-card'),
    userCards: q('.user-card'),
    skeletons: q('.skeleton'),
    videoTitle: (document.querySelector('.v-title') || {}).innerText || '',
    timeText: (document.querySelector('.time-text') || {}).innerText || '',
    sampleText: text.slice(0, 160).replace(/\\n/g, ' | ')
  };
})()`

/**
 * 布局几何检测：把所有元素的盒子都量一遍，看有没有：
 *  - 横向溢出视口（撑出横向滚动条）
 *  - 跑到视口左边的（说明定位算错了）
 *  - 卡片比例不对（封面应该是 16:9）
 * 弹幕、背景光斑这些本来就会移动的元素不算。
 */
const LAYOUT_PROBE = `(() => {
  const vw = window.innerWidth;
  const vh = window.innerHeight;
  const overflow = [];
  document.querySelectorAll('body *').forEach((el) => {
    const r = el.getBoundingClientRect();
    if (r.width < 1 || r.height < 1) return;
    const cls = (el.className || '').toString();
    if (/danmaku|blob|hover-tip|suggest/.test(cls)) return;
    // 轮播里没轮到的幻灯片本来就停在视口外，属于正常情况
    if (el.closest && el.closest('.el-carousel')) return;
    if (r.right > vw + 2 || r.left < -2) {
      overflow.push(el.tagName + '.' + cls.slice(0, 34) + ' [' + Math.round(r.left) + ',' + Math.round(r.right) + ']');
    }
  });

  const header = document.querySelector('.topbar');
  const hRect = header ? header.getBoundingClientRect() : null;

  const player = document.querySelector('.player');
  const pRect = player ? player.getBoundingClientRect() : null;

  const cover = document.querySelector('.vcard .cover');
  const cRect = cover ? cover.getBoundingClientRect() : null;

  const bgCover = document.querySelector('.bg-card .bg-cover');
  const bRect = bgCover ? bgCover.getBoundingClientRect() : null;

  // 首页第一排卡片应该顶部对齐、宽度一致
  const cards = [...document.querySelectorAll('.vcard')].slice(0, 5).map((el) => el.getBoundingClientRect());
  const sameRow = cards.length >= 3 ? cards.filter((r) => Math.abs(r.top - cards[0].top) < 2).length : 0;
  const widths = cards.map((r) => Math.round(r.width));
  const widthSpread = widths.length >= 3 ? Math.max(...widths) - Math.min(...widths) : 0;

  return {
    vw, vh,
    scrollWidth: document.documentElement.scrollWidth,
    horizontalScroll: document.documentElement.scrollWidth > vw + 2,
    overflowCount: overflow.length,
    overflowSample: overflow.slice(0, 6),
    headerHeight: hRect ? Math.round(hRect.height) : -1,
    playerRatio: pRect ? +(pRect.width / pRect.height).toFixed(2) : 0,
    coverRatio: cRect ? +(cRect.width / cRect.height).toFixed(2) : 0,
    bangumiCoverRatio: bRect ? +(bRect.width / bRect.height).toFixed(2) : 0,
    firstRowCards: sameRow,
    cardWidthSpread: widthSpread
  };
})()`

async function main() {
  fs.mkdirSync(SHOT_DIR, { recursive: true })
  const profile = path.resolve('.chrome-verify-profile')

  const chrome = spawn(
    CHROME,
    [
      '--headless=new',
      '--disable-gpu',
      '--no-first-run',
      '--no-default-browser-check',
      '--hide-scrollbars',
      `--remote-debugging-port=${PORT}`,
      `--user-data-dir=${profile}`,
      '--window-size=1680,1050',
      'about:blank'
    ],
    { stdio: 'ignore' }
  )

  const report = []
  let cdp
  try {
    const version = await waitForChrome()
    const ws = new WebSocket(version.webSocketDebuggerUrl)
    await new Promise((r) => ws.addEventListener('open', r, { once: true }))
    cdp = new CDP(ws)

    const { targetId } = await cdp.send('Target.createTarget', { url: 'about:blank' })
    const { sessionId } = await cdp.send('Target.attachToTarget', { targetId, flatten: true })

    const errors = []
    cdp.on((msg) => {
      if (msg.method === 'Runtime.exceptionThrown') {
        errors.push('异常: ' + (msg.params?.exceptionDetails?.exception?.description || '').slice(0, 200))
      }
      if (msg.method === 'Runtime.consoleAPICalled' && msg.params.type === 'error') {
        errors.push('console.error: ' + JSON.stringify(msg.params.args?.map((a) => a.value || a.description)).slice(0, 200))
      }
      if (msg.method === 'Log.entryAdded' && msg.params.entry.level === 'error') {
        errors.push('日志: ' + msg.params.entry.text.slice(0, 200))
      }
      if (msg.method === 'Network.loadingFailed') {
        errors.push('请求失败: ' + (msg.params.errorText || '') + ' ' + (msg.params.type || ''))
      }
    })

    await cdp.send('Page.enable', {}, sessionId)
    await cdp.send('Runtime.enable', {}, sessionId)
    await cdp.send('Log.enable', {}, sessionId)
    await cdp.send('Network.enable', {}, sessionId)

    const routes = [
      { name: 'home', url: `${BASE}/#/`, wait: 2500, expect: (r) => r.vcards >= 10 && r.channels >= 8 },
      { name: 'video', url: `${BASE}/#/video/100001`, wait: 2500, expect: (r) => r.player === 1 && r.comments >= 3 && r.related >= 5 },
      { name: 'ranking', url: `${BASE}/#/ranking`, wait: 2000, expect: (r) => r.rankRows >= 10 },
      { name: 'bangumi', url: `${BASE}/#/bangumi`, wait: 2000, expect: (r) => r.bangumiCards >= 8 },
      { name: 'space', url: `${BASE}/#/space/1000`, wait: 2000, expect: (r) => r.vcards >= 3 },
      { name: 'dynamic', url: `${BASE}/#/dynamic`, wait: 2000, expect: (r) => r.dynCards >= 3 },
      { name: 'search', url: `${BASE}/#/search?keyword=%E6%B8%B8%E6%88%8F`, wait: 2000, expect: (r) => r.vcards >= 1 },
    ]

    for (const route of routes) {
      errors.length = 0
      await cdp.send('Page.navigate', { url: route.url }, sessionId)
      await sleep(route.wait)

      const { result } = await cdp.send(
        'Runtime.evaluate',
        { expression: PROBE, returnByValue: true, awaitPromise: false },
        sessionId
      )
      const value = result.value || {}

      const { result: layoutResult } = await cdp.send(
        'Runtime.evaluate',
        { expression: LAYOUT_PROBE, returnByValue: true, awaitPromise: false },
        sessionId
      )
      const layout = layoutResult.value || {}
      const layoutOk =
        !layout.horizontalScroll && (layout.overflowCount || 0) === 0 && layout.headerHeight === 64

      const ok = route.expect(value) && layoutOk

      // 保存截图（方便你自己回头看）
      try {
        const shot = await cdp.send('Page.captureScreenshot', { format: 'png' }, sessionId)
        fs.writeFileSync(path.join(SHOT_DIR, `${route.name}.png`), Buffer.from(shot.data, 'base64'))
      } catch (e) {
        /* 截图失败不影响结论 */
      }

      report.push({ route: route.name, ok, errors: [...new Set(errors)], metrics: value, layout })

      // 播放页额外测试：点播放 → 时间是否前进、弹幕是否出现
      if (route.name === 'video') {
        await cdp.send(
          'Runtime.evaluate',
          { expression: `document.querySelector('.big-play')?.click(); true` },
          sessionId
        )
        await sleep(2500)
        const { result: after } = await cdp.send(
          'Runtime.evaluate',
          { expression: PROBE, returnByValue: true },
          sessionId
        )
        try {
          const shot = await cdp.send('Page.captureScreenshot', { format: 'png' }, sessionId)
          fs.writeFileSync(path.join(SHOT_DIR, 'video-playing.png'), Buffer.from(shot.data, 'base64'))
        } catch (e) {}
        report.push({
          route: 'video(播放中)',
          ok: after.value.danmakuItems > 0 && after.value.timeText !== '00:00 / ' + after.value.timeText.split('/')[1]?.trim(),
          errors: [...new Set(errors)],
          metrics: { danmakuItems: after.value.danmakuItems, timeText: after.value.timeText }
        })
      }
    }
  } finally {
    try {
      chrome.kill()
    } catch (e) {}
  }

  console.log('\n================ 验收结果 ================')
  for (const r of report) {
    console.log(`${r.ok ? '✅ 通过' : '❌ 失败'}  ${r.route}`)
    console.log('    元素: ', JSON.stringify(r.metrics))
    if (r.layout) {
      const L = r.layout
      console.log(
        `    布局:  视口${L.vw}x${L.vh} 文档宽${L.scrollWidth} 溢出元素${L.overflowCount} ` +
          `横向滚动:${L.horizontalScroll ? '有(异常)' : '无'} 顶栏高:${L.headerHeight} ` +
          `播放器比例:${L.playerRatio} 封面比例:${L.coverRatio} 番剧封面:${L.bangumiCoverRatio} ` +
          `首排卡片:${L.firstRowCards} 卡片宽度差:${L.cardWidthSpread}`
      )
      if (L.overflowSample?.length) console.log('    溢出样本:', L.overflowSample.join(' ; '))
    }
    if (r.errors.length) {
      console.log('    报错:')
      r.errors.forEach((e) => console.log('      - ' + e))
    }
  }
  const failed = report.filter((r) => !r.ok).length
  console.log(`\n合计 ${report.length} 项，失败 ${failed} 项\n`)
  process.exit(failed ? 1 : 0)
}

main().catch((e) => {
  console.error('脚本异常:', e)
  process.exit(2)
})
