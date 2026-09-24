/**
 * 用户互动功能验收：头像悬停卡片 / 改昵称 / 上传头像 / 签到 / 经验飘字
 *
 * 用法：node tools/e2e-user.mjs
 */
import fs from 'node:fs'
import path from 'node:path'
import { openBrowser, sleep } from './cdp.mjs'

const BASE = 'http://localhost:8080'
const AVATAR_FILE = path.resolve('shots/test-avatar.png')
const results = []

function check(name, ok, detail) {
  results.push({ name, ok })
  console.log(`${ok ? '✅' : '❌'} ${name}${detail ? '  → ' + detail : ''}`)
}

if (!fs.existsSync(AVATAR_FILE)) {
  console.error('缺少测试头像文件：' + AVATAR_FILE)
  process.exit(2)
}

const browser = await openBrowser({ port: 9226, profile: path.resolve('.chrome-user-profile') })

/** 用真实的鼠标移动去触发 hover */
async function hover(selector) {
  const box = await browser.eval(`(() => {
    const el = document.querySelector(${JSON.stringify(selector)});
    if (!el) return null;
    const r = el.getBoundingClientRect();
    return { x: r.left + r.width / 2, y: r.top + r.height / 2 };
  })()`)
  if (!box) throw new Error('找不到元素: ' + selector)
  await browser.cdp.send(
    'Input.dispatchMouseEvent',
    { type: 'mouseMoved', x: Math.round(box.x), y: Math.round(box.y), buttons: 0 },
    browser.sessionId
  )
}

try {
  // ---------- 0. 准备：清掉登录状态，重新走一遍登录 ----------
  await browser.goto(`${BASE}/#/`, 2200)
  await browser.eval('localStorage.clear(); true')
  await browser.reload(2600)
  await browser.click('.avatar-hit')
  await sleep(700)
  // 登录弹窗默认停在「扫码登录」标签页，演示入口是右侧常驻的 .demo-btn
  await browser.click('.modal .demo-btn')
  await sleep(1400)
  const loggedIn = await browser.eval(`(JSON.parse(localStorage.getItem('bili-user')||'null')||{}).name || ''`)
  check('登录成功', !!loggedIn, `当前昵称：${loggedIn}`)
  // 记下原始昵称，测试结束后恢复，不弄乱你自己的资料
  const originalName = loggedIn

  // ---------- 1. 鼠标悬停头像 → 弹出资料卡 ----------
  await hover('.avatar-hit')
  // 注意：Popover 的内容可能先挂到 DOM、后显示出来，
  // 所以要等它「真的有尺寸」再算通过，不然量到的是 0x0
  const cardShown = await browser.waitFor(
    async () =>
      await browser.eval(`(() => {
        const el = document.querySelector('.user-card');
        if (!el) return false;
        const r = el.getBoundingClientRect();
        return r.width > 100 && r.height > 100;
      })()`),
    { timeout: 5000 }
  )
  const cardText = await browser.eval(
    `(document.querySelector('.user-card')||{}).innerText || ''`
  )
  check('悬停头像弹出资料卡', !!cardShown, cardText.split('\n').slice(0, 3).join(' / '))
  check(
    '资料卡里有昵称 / 等级 / 经验 / 统计',
    cardText.includes('Lv') && cardText.includes('经验') && cardText.includes('粉丝') && cardText.includes('硬币')
  )

  const avatarBox = await browser.eval(`(() => {
    const el = document.querySelector('.user-card .ua');
    const r = el.getBoundingClientRect();
    return { w: Math.round(r.width), h: Math.round(r.height) };
  })()`)
  check('资料卡里的头像是放大版', avatarBox.w >= 80, `头像尺寸 ${avatarBox.w}x${avatarBox.h}`)

  // 经验条存在并且有宽度
  const expWidth = await browser.eval(
    `(() => { const el = document.querySelector('.user-card .ai-progress-fill'); return el ? Math.round(el.getBoundingClientRect().width) : 0; })()`
  )
  check('经验进度条正常渲染', expWidth > 10, `进度条宽度 ${expWidth}px`)

  // ---------- 2. 签到 +10 经验，并且有飘字动画 ----------
  const expBefore = await browser.eval(`(JSON.parse(localStorage.getItem('bili-user')).exp) || 0`)
  const lvBefore = await browser.eval(`(JSON.parse(localStorage.getItem('bili-user')).level) || 0`)
  const checkinText = await browser.eval(`(document.querySelector('.user-card .checkin')||{}).innerText || ''`)
  if (checkinText.includes('已签到')) {
    // 同一天重复跑测试时会走到这里，说明「每天只能签到一次」的限制生效了
    const disabled = await browser.eval(`(document.querySelector('.user-card .checkin')||{}).disabled === true`)
    check('签到按钮状态正确（今天已签到 + 置灰）', disabled, checkinText.trim())
  } else {
    await browser.click('.user-card .checkin')
    await sleep(1600)
    const floatShown = await browser.eval(`document.querySelectorAll('.exp-float').length > 0`)
    const expAfter = await browser.eval(`(JSON.parse(localStorage.getItem('bili-user')).exp) || 0`)
    const lvAfter = await browser.eval(`(JSON.parse(localStorage.getItem('bili-user')).level) || 0`)
    const gained = lvAfter > lvBefore || expAfter > expBefore
    check('签到后经验增加', gained, `Lv${lvBefore}(${expBefore}) → Lv${lvAfter}(${expAfter})`)
    check('签到后弹出「+经验」飘字', floatShown && gained)
  }

  // ---------- 3. 编辑资料：改昵称 ----------
  await browser.click('.user-card .btn-primary')
  await sleep(800)
  const modalOpen = await browser.eval(`!!document.querySelector('.modal .title')`)
  check('打开「编辑资料」弹窗', !!modalOpen)

  const newName = 'a哩a哩' + Math.floor(Math.random() * 900 + 100)
  await browser.type('.modal .field input', newName)
  await sleep(300)
  const previewName = await browser.eval(`(document.querySelector('.pv-name')||{}).innerText || ''`)
  check('弹窗里有实时预览', previewName === newName, `预览昵称：${previewName}`)

  // ---------- 4. 上传头像（走真实的文件选择事件） ----------
  const doc = await browser.cdp.send('DOM.getDocument', {}, browser.sessionId)
  const node = await browser.cdp.send(
    'DOM.querySelector',
    { nodeId: doc.root.nodeId, selector: '.modal input[type=file]' },
    browser.sessionId
  )
  check('找到头像文件选择框', !!node.nodeId)
  await browser.cdp.send('DOM.setFileInputFiles', { files: [AVATAR_FILE], nodeId: node.nodeId }, browser.sessionId)
  await sleep(2500)

  const uploadedUrl = await browser.eval(`(JSON.parse(localStorage.getItem('bili-user')||'{}').faceUrl) || ''`)
  check('头像上传成功并记录地址', uploadedUrl.startsWith('/api/files/avatar/'), uploadedUrl)

  const imgShown = await browser.eval(
    `(() => { const img = document.querySelector('.modal .drop img'); return img ? img.currentSrc || img.src : ''; })()`
  )
  // 选完文件会先显示浏览器本地的 blob 预览，再换成服务器地址，两种都算正常
  check(
    '弹窗里立刻显示新头像',
    imgShown.includes('/api/files/avatar/') || imgShown.startsWith('blob:'),
    imgShown.startsWith('blob:') ? '本地预览(blob)' : imgShown.slice(-40)
  )

  // 图片真的能访问到
  const httpStatus = await browser.eval(
    `fetch(${JSON.stringify(uploadedUrl)}).then(r => r.status).catch(() => 0)`
  )
  check('头像地址可以正常访问', httpStatus === 200, `HTTP ${httpStatus}`)

  // ---------- 5. 保存昵称 ----------
  await browser.click('.modal .footer .btn-primary')
  await sleep(1600)
  const savedName = await browser.eval(`(JSON.parse(localStorage.getItem('bili-user')||'{}').name) || ''`)
  const modalClosed = await browser.eval(`!document.querySelector('.modal')`)
  check('保存昵称成功', savedName === newName && modalClosed, `昵称：${savedName}`)

  // ---------- 6. 刷新页面后资料还在（后端有落盘） ----------
  await browser.goto(`${BASE}/#/`, 2600)
  const nameAfterReload = await browser.eval(`(JSON.parse(localStorage.getItem('bili-user')||'{}').name) || ''`)
  const avatarAfterReload = await browser.eval(
    `(() => { const img = document.querySelector('.topbar .ua img'); return img ? img.getAttribute('src') : ''; })()`
  )
  check('刷新后昵称仍然是新的', nameAfterReload === newName, nameAfterReload)
  check('刷新后头像仍然是上传的图片', avatarAfterReload.startsWith('/api/files/avatar/'), avatarAfterReload.slice(-36))

  // ---------- 7. 点赞后经验飘字 ----------
  await browser.goto(`${BASE}/#/video/100001`, 2600)
  // 如果上一次测试已经点过赞，先取消掉，保证下面这一次是「新点赞」（取消点赞不加经验）
  const wasLiked = await browser.eval(`(() => {
    const el = document.querySelector('.act-btn');
    return el ? el.classList.contains('on') : false;
  })()`)
  if (wasLiked) {
    await browser.click('.act-btn')
    await sleep(700)
  }
  const expBefore2 = await browser.eval(`(JSON.parse(localStorage.getItem('bili-user')).exp) || 0`)
  await browser.click('.act-btn')
  await sleep(900)
  const floatShown2 = await browser.eval(`document.querySelectorAll('.exp-float').length > 0`)
  const expAfter2 = await browser.eval(`(JSON.parse(localStorage.getItem('bili-user')).exp) || 0`)
  check('点赞后经验增加并飘字', expAfter2 > expBefore2 && floatShown2, `${expBefore2} → ${expAfter2}`)

  // ---------- 8. 三连粒子特效 ----------
  await browser.click('.triple-btn')
  await sleep(400)
  const particles = await browser.eval(`document.querySelectorAll('.particle').length`)
  check('一键三连有粒子特效', particles > 0, `${particles} 个粒子`)

  // ---------- 9. 「用回表情头像」要真的生效（后端也删掉图片） ----------
  const cleared = await browser.eval(`(() => {
    return fetch('/api/user/avatar', { method: 'DELETE' })
      .then(r => r.json())
      .then(d => {
        localStorage.setItem('bili-user', JSON.stringify(d.user));
        return !d.user.faceUrl;
      });
  })()`)
  check('可以改回表情头像（后端同步生效）', cleared === true)

  // ---------- 10. 收尾：恢复测试前的昵称，不弄乱你的资料 ----------
  const restored = await browser.eval(`(() => {
    return fetch('/api/user/profile', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ name: ${JSON.stringify(originalName)} })
    }).then(r => r.json()).then(d => {
      localStorage.setItem('bili-user', JSON.stringify(d.user));
      return d.user.name;
    });
  })()`)
  check('测试后已恢复原昵称', restored === originalName, `恢复为：${restored}`)

  const errors = browser.errors.filter((e) => !e.includes('favicon'))
  check('全程没有 JS 报错', errors.length === 0, errors.slice(0, 2).join(' | ') || '无')
} finally {
  browser.close()
}

const failed = results.filter((r) => !r.ok)
console.log(`\n========== 用户互动测试：${results.length} 项，失败 ${failed.length} 项 ==========`)
process.exit(failed.length ? 1 : 0)
