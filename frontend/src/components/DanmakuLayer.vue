<script setup>
import { computed, onMounted, onUnmounted, ref } from 'vue'

/**
 * 弹幕层。
 *
 * 设计思路：弹幕的位置是「当前播放时间」的纯函数 —— 只要给我 time，
 * 我就能算出一条弹幕此刻应该出现在哪里。这样暂停、拖动进度、倍速都不会乱。
 */
const props = defineProps({
  list: { type: Array, default: () => [] },   // 弹幕数据
  time: { type: Number, default: 0 },         // 当前播放到第几秒
  visible: { type: Boolean, default: true },  // 弹幕开关
  opacity: { type: Number, default: 0.95 },
  fontSize: { type: Number, default: 25 },
  /** 一条弹幕从右边走到左边需要多少秒 */
  travel: { type: Number, default: 9 },
  /** 屏幕上同时显示多少「轨道」 */
  lanes: { type: Number, default: 9 }
})

const boxRef = ref(null)
const boxWidth = ref(800)
const boxHeight = ref(450)
let observer = null

onMounted(() => {
  const update = () => {
    if (!boxRef.value) return
    boxWidth.value = boxRef.value.clientWidth || 800
    boxHeight.value = boxRef.value.clientHeight || 450
  }
  update()
  observer = new ResizeObserver(update)
  observer.observe(boxRef.value)
})

onUnmounted(() => observer && observer.disconnect())

/** 粗略估算一条弹幕的宽度（中文按 1 个字宽，英文数字按 0.55 个字宽） */
function estimateWidth(text, fontSize) {
  let w = 0
  for (const ch of String(text)) {
    w += ch.charCodeAt(0) > 255 ? fontSize : fontSize * 0.55
  }
  return w + 16
}

/** 屏幕上此刻应该出现的弹幕 */
const items = computed(() => {
  if (!props.visible) return []
  const t = props.time
  const travel = props.travel
  const list = props.list || []
  const laneHeight = Math.max(28, (boxHeight.value * 0.78) / props.lanes)
  const out = []

  for (let i = 0; i < list.length; i++) {
    const d = list[i]
    if (!d || typeof d.time !== 'number') continue
    const elapsed = t - d.time
    // 还没到出现时间，或者已经跑出屏幕 → 跳过
    if (elapsed < 0) continue

    const size = Math.round((d.fontSize || 25) * (props.fontSize / 25))
    const lane = i % props.lanes

    if (d.mode === 4 || d.mode === 5) {
      // 顶部 / 底部固定弹幕，停留 4 秒
      if (elapsed > 4) continue
      out.push({
        id: d.id ?? i,
        text: d.text,
        color: d.color || '#fff',
        size,
        self: d.self,
        style: {
          left: '50%',
          top: d.mode === 5 ? laneHeight * lane + 'px' : 'auto',
          bottom: d.mode === 4 ? laneHeight * lane + 'px' : 'auto',
          transform: 'translateX(-50%)'
        }
      })
      continue
    }

    // 滚动弹幕
    if (elapsed > travel) continue
    const width = estimateWidth(d.text, size)
    const progress = elapsed / travel
    const x = boxWidth.value - progress * (boxWidth.value + width)
    out.push({
      id: d.id ?? i,
      text: d.text,
      color: d.color || '#fff',
      size,
      self: d.self,
      style: {
        left: x + 'px',
        top: laneHeight * lane + 6 + 'px',
        width: width + 'px'
      }
    })
  }
  return out
})
</script>

<template>
  <div ref="boxRef" class="danmaku-layer" :style="{ opacity }">
    <div
      v-for="item in items"
      :key="item.id"
      class="danmaku-item"
      :class="{ self: item.self }"
      :style="{ ...item.style, color: item.color, fontSize: item.size + 'px' }"
    >
      {{ item.text }}
    </div>
  </div>
</template>

<style scoped>
.danmaku-layer {
  position: absolute;
  inset: 0;
  overflow: hidden;
  pointer-events: none;
  z-index: 5;
  transition: opacity 0.2s;
}

.danmaku-item {
  position: absolute;
  white-space: nowrap;
  font-weight: 600;
  letter-spacing: 0.5px;
  text-shadow: 0 1px 2px rgba(0, 0, 0, 0.85), 0 0 2px rgba(0, 0, 0, 0.8);
  will-change: transform, left;
}

.danmaku-item.self {
  border: 1px solid var(--brand-400);
  border-radius: 4px;
  padding: 0 4px;
  box-shadow: 0 0 6px rgba(110, 86, 248, 0.6);
}
</style>
