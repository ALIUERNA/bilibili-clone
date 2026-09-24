/**
 * 自动化验收脚本（页面渲染 + 布局几何 + 轮播铺满检测 + 多分辨率）
 *
 * 它会启动一个无头 Chrome，逐个打开页面，检查：
 *   1. 关键元素有没有渲染出来（卡片数量、标题、播放器等）
 *   2. 有没有 JS 报错、资源加载失败
 *   3. 点一下播放按钮，看看进度和弹幕有没有真的动起来（真实视频还要看 currentTime）
 *   4. 首页轮播在桌面 / 平板 / 手机三种宽度下是否完整铺满（不能有大面积空白）
 *
 * 用法：node tools/verify.mjs
 */
import { spawn } from 'node:child_process'
import fs from 'node:fs'
import path from 'node:path'

// 支持用环境变量切换浏览器，用于多内核验证：
//   BILI_BROWSER=chrome（默认） / edge
const BROWSER = (process.env.BILI_BROWSER || 'chrome').toLowerCase()
const CHROME =
  process.env.BILI_CHROME_PATH ||
  (BROWSER === 'edge'
    ? 'C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe'
    : 'C:/Program Files/Google/Chrome/Application/chrome.exe')
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
    nodeCards: q('.node-card'),
    channels: q('.channel'),
    player: q('.player'),
    realVideo: q('video.real-video'),
    danmakuLayer: q('.danmaku-layer'),
    danmakuItems: q('.danmaku-item'),
    comments: q('.comment'),
    related: q('.vrow'),
    rankRows: q('.vrank'),
    bangumiCards: q('.bg-card'),
    dynCards: q('.dyn-card'),
    userCards: q('.user-card'),
    authCard: q('.auth-card'),
    captchaImg: q('.captcha-img img'),
    qrImg: q('.qr-img'),
    ucMenu: q('.uc .menu button'),
    uploadBox: q('.drop-box'),
    notFound: q('.not-found'),
    nodePage: q('.node-page'),
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
  const inScrollable = (el) => {
    let p = el.parentElement;
    while (p && p !== document.body) {
      const ox = getComputedStyle(p).overflowX;
      if (ox === 'auto' || ox === 'scroll' || ox === 'hidden') return true;
      p = p.parentElement;
    }
    return false;
  };
  document.querySelectorAll('body *').forEach((el) => {
    const r = el.getBoundingClientRect();
    if (r.width < 1 || r.height < 1) return;
    const cls = (el.className || '').toString();
    if (/danmaku|blob|hover-tip|suggest/.test(cls)) return;
    if (el.closest && el.closest('.el-carousel')) return;
    // 横向滚动容器（分区导航条）里的元素超出视口是正常的
    if (inScrollable(el)) return;
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

/**
 * 轮播铺满检测：
 *  - .banner-wrap（外框）高度必须等于 carousel 轨道高度（差 <= 2px）
 *  - 当前显示的轮播项必须完整覆盖外框（上下都不能有缝）
 *  - 有真实封面帧时必须铺满（object-fit: cover）
 * 任何一项超标，就说明「轮播容器很大、只有上面一点有画面」的老问题复现了。
 */
const BANNER_PROBE = `(() => {
  const wrap = document.querySelector('.banner-wrap');
  if (!wrap) return { exists: false };
  const w = wrap.getBoundingClientRect();
  const container = document.querySelector('.el-carousel__container');
  const c = container ? container.getBoundingClientRect() : null;
  const slides = [...document.querySelectorAll('.el-carousel__item')].map((el) => {
    const r = el.getBoundingClientRect();
    return { active: el.classList.contains('is-active'), w: Math.round(r.width), h: Math.round(r.height), bottom: Math.round(r.bottom) };
  });
  const active = slides.find((s) => s.active) || slides[0] || null;
  const img = document.querySelector('.el-carousel__item.is-active img');
  let imgInfo = null;
  if (img) {
    const r = img.getBoundingClientRect();
    imgInfo = {
      w: Math.round(r.width), h: Math.round(r.height),
      natural: img.naturalWidth + 'x' + img.naturalHeight,
      objectFit: getComputedStyle(img).objectFit,
      display: getComputedStyle(img).display
    };
  }
  return {
    exists: true,
    wrap: { w: Math.round(w.width), h: Math.round(w.height), top: Math.round(w.top), bottom: Math.round(w.bottom) },
    container: c ? { w: Math.round(c.width), h: Math.round(c.height), bottom: Math.round(c.bottom) } : null,
    active,
    gapBelowCarousel: c ? Math.round(w.bottom - c.bottom) : -1,
    gapBelowSlide: active ? Math.round(w.bottom - active.bottom) : -1,
    slideFillRatio: active ? +(active.h / w.height).toFixed(3) : 0,
    img: imgInfo
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
      '--autoplay-policy=no-user-gesture-required',
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
      { name: 'home', url: `${BASE}/#/`, wait: 2600, expect: (r) => r.vcards >= 10 && r.channels >= 8 && r.banners >= 1 },
      { name: 'video', url: `${BASE}/#/video/100000`, wait: 2800, expect: (r) => r.player === 1 && r.comments >= 3 && r.related >= 5 },
      { name: 'ranking', url: `${BASE}/#/ranking`, wait: 2000, expect: (r) => r.rankRows >= 10 },
      { name: 'bangumi', url: `${BASE}/#/bangumi`, wait: 2000, expect: (r) => r.bangumiCards >= 8 },
      { name: 'space', url: `${BASE}/#/space/1000`, wait: 2000, expect: (r) => r.vcards >= 3 },
      { name: 'dynamic', url: `${BASE}/#/dynamic`, wait: 2000, expect: (r) => r.dynCards >= 3 },
      { name: 'search', url: `${BASE}/#/search?keyword=%E6%B8%B8%E6%88%8F`, wait: 2000, expect: (r) => r.vcards >= 1 },
      // 新增页面
      { name: 'login', url: `${BASE}/#/login`, wait: 2400, expect: (r) => r.authCard >= 1 && r.captchaImg >= 1 },
      { name: 'login(邮箱tab)', url: `${BASE}/#/login?tab=email`, wait: 2200, expect: (r) => r.authCard >= 1 },
      { name: 'login(扫码tab)', url: `${BASE}/#/login?tab=qr`, wait: 2600, expect: (r) => r.authCard >= 1 },
      { name: 'register', url: `${BASE}/#/register`, wait: 2400, expect: (r) => r.authCard >= 1 },
      { name: 'forgot', url: `${BASE}/#/forgot`, wait: 2400, expect: (r) => r.authCard >= 1 },
      { name: 'node-game', url: `${BASE}/#/node/game`, wait: 2400, expect: (r) => r.nodePage >= 1 },
      { name: 'node-documentary(占位)', url: `${BASE}/#/node/documentary`, wait: 2200, expect: (r) => r.nodePage >= 1 },
      { name: 'live(预留)', url: `${BASE}/#/live`, wait: 2200, expect: (r) => r.nodePage >= 1 },
      { name: 'column(预留)', url: `${BASE}/#/column`, wait: 2200, expect: (r) => r.nodePage >= 1 },
      { name: 'activity(预留)', url: `${BASE}/#/activity`, wait: 2200, expect: (r) => r.nodePage >= 1 },
      { name: 'community(预留)', url: `${BASE}/#/community`, wait: 2200, expect: (r) => r.nodePage >= 1 },
      { name: '404', url: `${BASE}/#/this-page-does-not-exist`, wait: 2000, expect: (r) => r.notFound >= 1 },
      { name: 'user(未登录跳转)', url: `${BASE}/#/user/overview`, wait: 2200, expect: (r) => r.authCard >= 1 },
      { name: 'upload(未登录跳转)', url: `${BASE}/#/upload`, wait: 2200, expect: (r) => r.authCard >= 1 }
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
      const layoutOk = !layout.horizontalScroll && (layout.overflowCount || 0) === 0 && layout.headerHeight === 64

      let banner = null
      if (route.name === 'home') {
        const { result: b } = await cdp.send('Runtime.evaluate', { expression: BANNER_PROBE, returnByValue: true }, sessionId)
        banner = b.value || {}
      }
      const bannerOk =
        !banner ||
        (banner.slideFillRatio >= 0.98 &&
          banner.gapBelowCarousel <= 2 &&
          banner.gapBelowSlide <= 2 &&
          (!banner.img || (banner.img.w >= banner.wrap.w * 0.9 && banner.img.objectFit === 'cover')))

      const ok = route.expect(value) && layoutOk && bannerOk

      try {
        const shot = await cdp.send('Page.captureScreenshot', { format: 'png' }, sessionId)
        fs.writeFileSync(path.join(SHOT_DIR, `${route.name}.png`), Buffer.from(shot.data, 'base64'))
      } catch (e) {
        /* 截图失败不影响结论 */
      }

      report.push({ route: route.name, ok, errors: [...new Set(errors)], metrics: value, layout, banner })

      // 播放页额外测试：点播放 → 时间是否前进、弹幕是否出现、真实视频有没有在解码
      if (route.name === 'video') {
        await cdp.send(
          'Runtime.evaluate',
          {
            expression: `document.querySelector('.big-play')?.click(); const v=document.querySelector('video.real-video'); if(v){v.muted=true; v.play().catch(()=>{});} true`,
            awaitPromise: false
          },
          sessionId
        )
        await sleep(3200)
        const { result: after } = await cdp.send('Runtime.evaluate', { expression: PROBE, returnByValue: true }, sessionId)
        const { result: mediaState } = await cdp.send(
          'Runtime.evaluate',
          {
            expression: `(() => { const v = document.querySelector('video.real-video'); return v ? { exists:true, currentTime:+v.currentTime.toFixed(2), duration:+(v.duration||0).toFixed(2), paused:v.paused, readyState:v.readyState, videoWidth:v.videoWidth, videoHeight:v.videoHeight, error:v.error?v.error.code:null } : { exists:false }; })()`,
            returnByValue: true
          },
          sessionId
        )
        try {
          const shot = await cdp.send('Page.captureScreenshot', { format: 'png' }, sessionId)
          fs.writeFileSync(path.join(SHOT_DIR, 'video-playing.png'), Buffer.from(shot.data, 'base64'))
        } catch (e) {
          /* ignore */
        }
        const v = mediaState.value || {}
        report.push({
          route: 'video(播放中)',
          ok: after.value.danmakuItems > 0 && (!v.exists || (v.currentTime > 0 && v.videoWidth > 0)),
          errors: [...new Set(errors)],
          metrics: { danmakuItems: after.value.danmakuItems, timeText: after.value.timeText, video: v }
        })
      }
    }

    // ---------- 多分辨率下的轮播铺满检测 ----------
    const viewports = [
      { name: '桌面 1680x1050', w: 1680, h: 1050 },
      { name: '笔记本 1366x768', w: 1366, h: 768 },
      { name: '平板 834x1112', w: 834, h: 1112 },
      { name: '手机 390x844', w: 390, h: 844 }
    ]
    for (const vp of viewports) {
      errors.length = 0
      await cdp.send(
        'Emulation.setDeviceMetricsOverride',
        { width: vp.w, height: vp.h, deviceScaleFactor: 1, mobile: vp.w < 600 },
        sessionId
      )
      await cdp.send('Page.navigate', { url: `${BASE}/#/` }, sessionId)
      await sleep(2600)
      const { result: b } = await cdp.send('Runtime.evaluate', { expression: BANNER_PROBE, returnByValue: true }, sessionId)
      const { result: l } = await cdp.send('Runtime.evaluate', { expression: LAYOUT_PROBE, returnByValue: true }, sessionId)
      const banner = b.value || {}
      const layout = l.value || {}
      const ok =
        banner.exists &&
        banner.slideFillRatio >= 0.98 &&
        banner.gapBelowCarousel <= 2 &&
        banner.gapBelowSlide <= 2 &&
        !layout.horizontalScroll
      try {
        const shot = await cdp.send('Page.captureScreenshot', { format: 'png' }, sessionId)
        fs.writeFileSync(path.join(SHOT_DIR, `home-${vp.w}.png`), Buffer.from(shot.data, 'base64'))
      } catch (e) {
        /* ignore */
      }
      report.push({ route: `轮播铺满 @ ${vp.name}`, ok, errors: [...new Set(errors)], banner, layout })
    }
    await cdp.send('Emulation.clearDeviceMetricsOverride', {}, sessionId)
  } finally {
    try {
      chrome.kill()
    } catch (e) {}
  }

  console.log('\n================ 验收结果 ================')
  let pass = 0
  for (const r of report) {
    if (r.ok) pass++
    console.log(`${r.ok ? '✅ 通过' : '❌ 失败'}  ${r.route}`)
    if (r.metrics) console.log('    元素: ', JSON.stringify(r.metrics))
    if (r.layout) {
      const L = r.layout
      console.log(
        `    布局:  视口${L.vw}x${L.vh} 文档宽${L.scrollWidth} 溢出元素${L.overflowCount} ` +
          `横向滚动:${L.horizontalScroll ? '有(异常)' : '无'} 顶栏高:${L.headerHeight} ` +
          `播放器比例:${L.playerRatio} 封面比例:${L.coverRatio} ` +
          `首排卡片:${L.firstRowCards} 卡片宽度差:${L.cardWidthSpread}`
      )
      if (L.overflowSample?.length) console.log('    溢出样本:', L.overflowSample.join(' ; '))
    }
    if (r.banner) {
      const B = r.banner
      if (B.exists) {
        console.log(
          `    轮播:  外框${B.wrap?.w}x${B.wrap?.h} 轨道${B.container?.w}x${B.container?.h} ` +
            `可见项${B.active?.w}x${B.active?.h} 铺满率${B.slideFillRatio} ` +
            `下方空白:${B.gapBelowCarousel}px/${B.gapBelowSlide}px ` +
            (B.img ? `封面图${B.img.w}x${B.img.h}(原图${B.img.natural},${B.img.objectFit})` : '封面图:无(渐变兜底)')
        )
      } else {
        console.log('    轮播:  页面上没有轮播容器')
      }
    }
    if (r.errors?.length) {
      console.log('    报错:')
      r.errors.forEach((e) => console.log('      - ' + e))
    }
  }
  console.log(`\n合计：${pass}/${report.length} 项通过`)
}

main().catch((e) => {
  console.error('验收脚本失败：', e)
  process.exit(1)
})
