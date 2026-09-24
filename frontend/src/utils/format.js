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
  const c1 = video?.coverColor1 || '#6E56F8'
  const c2 = video?.coverColor2 || '#FF7A45'
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

/** 日期时间：2024-11-08T20:15:00 → 2024-11-08 20:15 */
export function formatDateTime(value) {
  if (!value) return ''
  const str = String(value).replace('T', ' ')
  return str.length >= 16 ? str.slice(0, 16) : str
}

/** 秒 → 「3分12秒 / 1小时2分」 */
export function formatDurationCn(sec) {
  const s = Math.max(0, Math.floor(Number(sec) || 0))
  if (s < 60) return s + '秒'
  const m = Math.floor(s / 60)
  if (m < 60) return m + '分' + (s % 60 ? (s % 60) + '秒' : '')
  return Math.floor(m / 60) + '小时' + (m % 60) + '分'
}

/**
 * 数据库行 → VideoCard 需要的视频对象。
 * 个人中心的「历史 / 收藏 / 点赞」列表用的是数据库里的行数据，这里做一次统一转换。
 */
export function rowToVideo(row) {
  if (!row) return null
  const coverPath = row.cover_path || ''
  let coverUrl = row.coverUrl || row.cover_path
  if (coverUrl && !String(coverUrl).startsWith('/api/') && !String(coverUrl).startsWith('http')) {
    coverUrl = '/api/files/cover/' + String(coverPath).replace(/^covers\//, '')
  }
  return {
    id: row.id ?? row.video_id,
    bvid: row.bvid,
    title: row.title,
    duration: row.duration || 0,
    views: row.views || 0,
    danmakus: row.danmaku_count || 0,
    upName: row.up_name,
    upId: row.up_id,
    upFace: row.up_face,
    coverUrl,
    posterUrl: coverUrl,
    coverColor1: row.cover_color1,
    coverColor2: row.cover_color2,
    coverEmoji: row.cover_emoji,
    coverText: row.cover_text,
    pubAgo: row.time || row.pub_ago || ''
  }
}
