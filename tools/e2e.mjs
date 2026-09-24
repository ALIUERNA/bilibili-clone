/**
 * 端到端交互测试：模拟一个真实用户走一遍核心流程
 *   首页点卡片 → 播放页 → 未登录点赞被拦 → 一键登录 → 发弹幕 → 发评论 → 一键三连 → 顶部搜索
 *
 * 用法：node tools/e2e.mjs
 */
import path from 'node:path'
import { openBrowser, sleep } from './cdp.mjs'

const BASE = 'http://localhost:8080'
const results = []

function check(name, ok, detail) {
  results.push({ name, ok, detail })
  console.log(`${ok ? '✅' : '❌'} ${name}${detail ? '  → ' + detail : ''}`)
}

const browser = await openBrowser({
  port: 9225,
  profile: path.resolve('.chrome-e2e-profile')
})

try {
  // ---------- 1. 首页 → 点第一个视频卡片 ----------
  await browser.goto(`${BASE}/#/`, 2500)
  const cardTitle = await browser.eval(`(document.querySelector('.vcard .title')||{}).innerText||''`)
  await browser.click('.vcard')
  await sleep(2200)
  const url1 = await browser.eval('location.hash')
  const hasPlayer = await browser.eval(`!!document.querySelector('.player')`)
  check('首页点击卡片能进播放页', /#\/video\/\d+/.test(url1) && hasPlayer, `URL=${url1} 卡片标题「${cardTitle.slice(0, 14)}…」`)

  // ---------- 2. 未登录点赞 → 应该弹出登录框 ----------
  await browser.eval('localStorage.removeItem("bili-user"); localStorage.removeItem("bili-token"); true')
  await browser.goto(`${BASE}/#/video/100001`, 1500)
  await browser.reload(2500)
  await browser.click('.act-btn')
  await sleep(600)
  const modalShown = await browser.eval(`!!document.querySelector('.modal')`)
  check('未登录时点赞会弹出登录框', modalShown)

  // ---------- 3. 一键登录 ----------
  // 登录弹窗默认停在「扫码登录」标签页，演示入口是右侧常驻的 .demo-btn
  await browser.click('.modal .demo-btn')
  await sleep(1200)
  const loggedIn = await browser.eval(`!!localStorage.getItem('bili-user')`)
  const avatarText = await browser.eval(`(document.querySelector('.topbar .ua .emoji')||{}).innerText||''`)
  const modalGone = await browser.eval(`!document.querySelector('.modal')`)
  check('一键登录成功', loggedIn && modalGone, `头像=${avatarText.trim()}`)

  // ---------- 4. 播放视频 + 发弹幕 ----------
  await browser.click('.big-play')
  await sleep(2500)
  const timeText = await browser.eval(`(document.querySelector('.time-text')||{}).innerText||''`)
  const dmCount = await browser.eval(`document.querySelectorAll('.danmaku-item').length`)
  check('视频能播放（进度在走 + 屏幕有弹幕）', /^00:0[2-9]|00:1\d/.test(timeText) && dmCount > 0, `${timeText}，屏幕弹幕 ${dmCount} 条`)

  const dmText = '测试弹幕' + Math.floor(Math.random() * 1000)
  await browser.type('.dm-input input', dmText)
  await browser.click('.dm-send')
  const selfShown = await browser.waitFor(
    async () => (await browser.eval(`document.querySelectorAll('.danmaku-item.self').length`)) > 0,
    { timeout: 6000 }
  )
  const dmOnScreen = await browser.eval(
    `[...document.querySelectorAll('.danmaku-item.self')].map(e=>e.innerText).join(',')`
  )
  check('发弹幕后弹幕出现在屏幕上', !!selfShown, `自己发的: ${dmOnScreen}`)

  // ---------- 5. 发评论 ----------
  const cmtText = '自动化测试评论 ' + Math.floor(Math.random() * 1000)
  await browser.type('.cmt-field textarea', cmtText)
  await browser.click('.cmt-actions .btn-primary')
  const cmtOk = await browser.waitFor(async () => {
    const first = await browser.eval(`(document.querySelector('.comment .content')||{}).innerText||''`)
    return first.includes(cmtText)
  })
  const cmtAuthor = await browser.eval(`(document.querySelector('.comment .user')||{}).innerText||''`)
  check('发表评论成功', !!cmtOk, `第一条评论作者=${cmtAuthor}`)

  // ---------- 6. 一键三连 ----------
  await browser.click('.triple-btn')
  await sleep(1800)
  const onCount = await browser.eval(`document.querySelectorAll('.act-btn.on').length`)
  check('一键三连（点赞/投币/收藏都点亮）', onCount >= 3, `点亮 ${onCount} 个`)

  // ---------- 7. 关注 UP 主 ----------
  await browser.click('.follow-btn')
  await sleep(1200)
  const followed = await browser.eval(`(document.querySelector('.follow-btn')||{}).innerText||''`)
  check('关注 UP 主', followed.includes('已关注'), `按钮文案=${followed.trim()}`)

  // ---------- 8. 顶部搜索 ----------
  await browser.type('.search-box input', '美食')
  await browser.click('.search-btn')
  await sleep(2200)
  const url2 = await browser.eval('location.hash')
  const resultCount = await browser.eval(`document.querySelectorAll('.vcard').length`)
  check('顶部搜索能跳转并出结果', url2.includes('/search?keyword=') && resultCount > 0, `URL=${url2}，结果 ${resultCount} 条`)

  // ---------- 9. 投币后刷新页面，数据仍然在（后端有状态） ----------
  await browser.goto(`${BASE}/#/video/100001`, 2500)
  const likedAfter = await browser.eval(`document.querySelectorAll('.act-btn.on').length`)
  check('刷新后点赞状态仍然保留（后端记录了状态）', likedAfter >= 3, `点亮 ${likedAfter} 个`)

  // ---------- 控制台错误 ----------
  const realErrors = browser.errors.filter((e) => !e.includes('favicon'))
  check('整个流程没有 JS 报错', realErrors.length === 0, realErrors.slice(0, 3).join(' | ') || '无')
} finally {
  browser.close()
}

const failed = results.filter((r) => !r.ok)
console.log(`\n========== 交互测试：${results.length} 项，失败 ${failed.length} 项 ==========`)
process.exit(failed.length ? 1 : 0)
