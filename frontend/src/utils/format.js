/**
 * 一些格式化的小工具。
 */

/** 播放量：12345 → 1.2万 ；123456789 → 1.2亿 */
export function formatCount(num) {
  const n = Number(num) || 0
  if (n >= 100000000) return (n / 100000000).toFixed(1) + '亿'
  if (n >= 10000) return (n / 10000).toFixed(1) + '万'
  return String(n)
}

/** 时长：125 → 02:05 ；3725 → 1:02:05 */
export function formatDuration(sec) {
  const s = Math.max(0, Math.floor(Number(sec) || 0))
  const h = Math.floor(s / 3600)
  const m = Math.floor((s % 3600) / 60)
  const ss = s % 60
  const pad = (v) => String(v).padStart(2, '0')
  return h > 0 ? `${h}:${pad(m)}:${pad(ss)}` : `${pad(m)}:${pad(ss)}`
}

/** 发布时间：只显示到日期 */
export function formatDate(str) {
  if (!str) return ''
  return String(str).slice(0, 16)
}

/**
 * 生成封面用的渐变色。
 * 后端只给了两个颜色，这里补一层光斑，让封面看起来更有设计感。
 */
export function coverStyle(video) {
  const c1 = video?.coverColor1 || '#FB7299'
  const c2 = video?.coverColor2 || '#A6C1EE'
  return {
    backgroundImage:
      `radial-gradient(circle at 22% 20%, rgba(255,255,255,.55), transparent 42%),` +
      `radial-gradient(circle at 82% 78%, rgba(255,255,255,.35), transparent 45%),` +
      `linear-gradient(135deg, ${c1} 0%, ${c2} 100%)`
  }
}

/** 给弹幕按时间排序 */
export function sortByTime(list) {
  return [...(list || [])].sort((a, b) => a.time - b.time)
}
