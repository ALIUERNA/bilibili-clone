/**
 * 全局图标系统（自绘图标集，替代 @element-plus/icons-vue）。
 *
 * 设计：
 *   · 路径数据全部放在 ./ui/icons.js，都是 24×24 描边风格的原创图形；
 *   · 这里把路径「编译」成 Vue 组件并全局注册，模板里直接写 <Search /> 即可；
 *   · 图标本身是 1em 大小的 SVG，外层用 <AiIcon :size="18"> 控制大小和颜色。
 *
 * 换成自绘图标集有两个好处：
 *   1. 不再依赖第三方图标包，线宽、圆角、视觉重心都能自己定；
 *   2. 打包体积从「整个图标库」降到「真正用到的这几个路径字符串」。
 */
import { h } from 'vue'
import { ICON_PATHS, FILLED } from './ui/icons'
import AiIcon from './ui/AiIcon.vue'

const DEFAULT_STROKE = 1.7

/**
 * 把一个图标定义编译成无状态组件。
 * 渲染出来就是一段 <svg>，因此任何地方都能安全地重复使用。
 */
function makeGlyph(name) {
  const def = ICON_PATHS[name] || { d: [] }
  const filled = FILLED.has(name)
  const paths = Array.isArray(def.d) ? def.d : [def.d]

  return {
    name,
    functional: true,
    render() {
      return h(
        'svg',
        {
          class: 'ai-glyph',
          viewBox: '0 0 24 24',
          xmlns: 'http://www.w3.org/2000/svg',
          focusable: 'false',
          'aria-hidden': 'true',
          fill: filled ? 'currentColor' : 'none',
          stroke: filled ? 'none' : 'currentColor',
          'stroke-width': def.sw || DEFAULT_STROKE,
          'stroke-linecap': 'round',
          'stroke-linejoin': 'round'
        },
        paths.map((d, i) => h('path', { d, key: i }))
      )
    }
  }
}

/** 名称 → 组件 的映射表（模板里可直接用这些名字） */
export const icons = Object.keys(ICON_PATHS).reduce((acc, name) => {
  acc[name] = makeGlyph(name)
  return acc
}, {})

/** 兜底图标：图表类、内容节点找不到时用 */
export const FALLBACK_ICON = icons.Grid || makeGlyph('Grid')

/** 全局注册所有图标 + <AiIcon> 容器 */
export function registerIcons(app) {
  for (const [name, component] of Object.entries(icons)) {
    app.component(name, component)
  }
  app.component('AiIcon', AiIcon)
}

/** 内容节点 code → 图标（前端自己决定用哪个图形，不再依赖后端下发的 emoji） */
const NODE_ICONS = {
  recommend: 'Star',
  hot: 'Histogram',
  anime: 'VideoCamera',
  bangumi: 'Monitor',
  guochuang: 'Flag',
  music: 'Headset',
  dance: 'MagicStick',
  game: 'Platform',
  knowledge: 'Reading',
  tech: 'Cpu',
  sports: 'Basketball',
  car: 'Van',
  life: 'House',
  food: 'Food',
  animal: 'Chicken',
  kichiku: 'Lightning',
  fashion: 'Brush',
  film: 'Film',
  documentary: 'Reading',
  information: 'Memo',
  entertainment: 'Microphone',
  movie: 'Film',
  variety: 'Ticket',
  live: 'VideoPlay',
  column: 'EditPen',
  activity: 'Present',
  community: 'UserFilled',
  newstar: 'MagicStick',
  anime_series: 'VideoPlay',
  anime_finish: 'CircleCheck',
  anime_mad: 'Film',
  game_standalone: 'Mouse',
  game_esports: 'Trophy',
  game_mobile: 'Cellphone',
  tech_digital: 'Camera',
  tech_pc: 'Monitor',
  film_review: 'ChatDotRound',
  film_cut: 'Scissor',
  music_cover: 'Microphone',
  music_vocaloid: 'Headset',
  knowledge_science: 'DataAnalysis',
  knowledge_humanity: 'Reading',
  community_help: 'QuestionFilled'
}

/** 按节点 code 取图标组件 */
export function nodeIcon(code) {
  return icons[NODE_ICONS[code]] || FALLBACK_ICON
}
