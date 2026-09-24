<script setup>
/**
 * AiCarousel —— 轮播容器，替代 Element Plus 的 <el-carousel>。
 *
 * 自己写的好处：动画曲线、指示器、箭头都能直接吃到品牌令牌，
 * 而且不用为了一个首屏组件引入 Element Plus 的整套运行时。
 *
 * 用法：
 *   <AiCarousel :interval="5200" arrow="hover" @change="i => (idx = i)">
 *     <AiCarouselItem v-for="b in banners" :key="b.id">…</AiCarouselItem>
 *   </AiCarousel>
 */
import { computed, onBeforeUnmount, onMounted, provide, ref } from 'vue'

const props = defineProps({
  /** 自动播放间隔（毫秒），<= 0 表示不自动播放 */
  interval: { type: Number, default: 5000 },
  autoplay: { type: Boolean, default: true },
  /** 鼠标悬停时暂停自动播放 */
  pauseOnHover: { type: Boolean, default: true },
  /** always | hover | never */
  arrow: { type: String, default: 'hover' },
  /** inside | outside | none */
  indicatorPosition: { type: String, default: 'inside' },
  /** 是否循环播放 */
  loop: { type: Boolean, default: true },
  /** 是否支持左右拖拽切换 */
  touchable: { type: Boolean, default: true }
})

const emit = defineEmits(['change'])

const rootRef = ref(null)
const trackRef = ref(null)
const entries = ref([])

const index = ref(0)
const paused = ref(false)

const count = computed(() => entries.value.length)
const canPrev = computed(() => props.loop || index.value > 0)
const canNext = computed(() => props.loop || index.value < count.value - 1)

/** 子项按 DOM 顺序注册，保证指示器顺序和视觉顺序一致 */
function register(entry) {
  entries.value = [...entries.value.filter((e) => e.uid !== entry.uid), entry].sort((a, b) => {
    if (!a.el || !b.el) return 0
    const rel = a.el.compareDocumentPosition(b.el)
    if (rel & Node.DOCUMENT_POSITION_FOLLOWING) return -1
    if (rel & Node.DOCUMENT_POSITION_PRECEDING) return 1
    return 0
  })
}

function unregister(uid) {
  entries.value = entries.value.filter((e) => e.uid !== uid)
  if (index.value > count.value - 1) index.value = Math.max(0, count.value - 1)
}

provide('ai-carousel', { register, unregister, activeIndex: index })

function goTo(i, fromUser = false) {
  if (!count.value) return
  // 两个分支都会重新赋值，所以不给初值（给了也是死代码）
  let next
  if (props.loop) next = ((i % count.value) + count.value) % count.value
  else next = Math.max(0, Math.min(i, count.value - 1))
  if (next === index.value) return
  index.value = next
  emit('change', next)
  if (fromUser) restart()
}

function prev() {
  goTo(index.value - 1, true)
}

function next() {
  goTo(index.value + 1, true)
}

// ---------- 自动播放 ----------
let timer = null

function stop() {
  clearInterval(timer)
  timer = null
}

function restart() {
  stop()
  if (!props.autoplay || props.interval <= 0 || count.value <= 1) return
  timer = setInterval(() => {
    if (paused.value) return
    index.value = (index.value + 1) % count.value
    emit('change', index.value)
  }, props.interval)
}

onMounted(restart)
onBeforeUnmount(() => {
  stop()
  unbindDrag()
})

function onEnter() {
  if (props.pauseOnHover) paused.value = true
}

function onLeave() {
  paused.value = false
}

// ---------- 键盘 ----------
function onKeydown(e) {
  if (e.key === 'ArrowLeft') {
    e.preventDefault()
    prev()
  } else if (e.key === 'ArrowRight') {
    e.preventDefault()
    next()
  }
}

// ---------- 拖拽 / 滑动 ----------
let startX = 0
let dragging = false

function onPointerDown(e) {
  if (!props.touchable || count.value <= 1) return
  if (e.pointerType === 'mouse' && e.button !== 0) return
  dragging = true
  startX = e.clientX
  window.addEventListener('pointerup', onPointerUp)
}

function onPointerUp(e) {
  if (!dragging) return
  dragging = false
  unbindDrag()
  const dx = e.clientX - startX
  if (Math.abs(dx) < 42) return
  if (dx < 0) next()
  else prev()
}

function unbindDrag() {
  window.removeEventListener('pointerup', onPointerUp)
}

const trackStyle = computed(() => ({
  transform: `translate3d(${-index.value * 100}%, 0, 0)`,
  transition: dragging ? 'none' : undefined
}))
</script>

<template>
  <div
    ref="rootRef"
    class="ai-carousel"
    :class="[`arrow-${arrow}`, `indicator-${indicatorPosition}`]"
    role="region"
    aria-roledescription="carousel"
    tabindex="0"
    @mouseenter="onEnter"
    @mouseleave="onLeave"
    @keydown="onKeydown"
    @pointerdown="onPointerDown"
  >
    <div ref="trackRef" class="ai-carousel__track" :style="trackStyle">
      <slot />
    </div>

    <button
      v-if="arrow !== 'never' && count > 1"
      class="ai-carousel__arrow is-prev"
      :disabled="!canPrev"
      aria-label="上一张"
      @click.stop="prev"
    >
      <svg viewBox="0 0 24 24" aria-hidden="true">
        <path
          d="M14.5 5.5 8 12l6.5 6.5"
          fill="none"
          stroke="currentColor"
          stroke-width="2"
          stroke-linecap="round"
          stroke-linejoin="round"
        />
      </svg>
    </button>

    <button
      v-if="arrow !== 'never' && count > 1"
      class="ai-carousel__arrow is-next"
      :disabled="!canNext"
      aria-label="下一张"
      @click.stop="next"
    >
      <svg viewBox="0 0 24 24" aria-hidden="true">
        <path
          d="M9.5 5.5 16 12l-6.5 6.5"
          fill="none"
          stroke="currentColor"
          stroke-width="2"
          stroke-linecap="round"
          stroke-linejoin="round"
        />
      </svg>
    </button>

    <div v-if="indicatorPosition !== 'none' && count > 1" class="ai-carousel__dots" role="tablist">
      <button
        v-for="(e, i) in entries"
        :key="e.uid"
        class="ai-carousel__dot"
        :class="{ on: i === index }"
        role="tab"
        :aria-selected="i === index"
        :aria-label="`第 ${i + 1} 张`"
        @click.stop="goTo(i, true)"
      ></button>
    </div>
  </div>
</template>

<style scoped>
.ai-carousel {
  position: relative;
  width: 100%;
  height: 100%;
  overflow: hidden;
  border-radius: inherit;
  outline: none;
}

.ai-carousel:focus-visible {
  box-shadow: var(--sd-focus);
}

.ai-carousel__track {
  display: flex;
  height: 100%;
  will-change: transform;
  transition: transform 0.55s var(--ease-in-out);
}

/* ---------- 箭头 ---------- */
.ai-carousel__arrow {
  position: absolute;
  top: 50%;
  z-index: 3;
  display: flex;
  align-items: center;
  justify-content: center;
  width: 38px;
  height: 38px;
  margin-top: -19px;
  border-radius: 50%;
  color: #fff;
  background: rgba(18, 20, 26, 0.34);
  backdrop-filter: blur(6px);
  -webkit-backdrop-filter: blur(6px);
  transition: background var(--dur-base), transform var(--dur-base), opacity var(--dur-base);
}

.ai-carousel__arrow svg {
  width: 22px;
  height: 22px;
}

.ai-carousel__arrow.is-prev {
  left: 14px;
}

.ai-carousel__arrow.is-next {
  right: 14px;
}

.ai-carousel__arrow:hover:not(:disabled) {
  background: var(--brand-500);
  transform: scale(1.08);
}

.ai-carousel__arrow:disabled {
  opacity: 0.32;
  cursor: not-allowed;
}

.arrow-hover .ai-carousel__arrow {
  opacity: 0;
  transform: translateX(0) scale(0.9);
  pointer-events: none;
}

.arrow-hover:hover .ai-carousel__arrow,
.arrow-hover:focus-within .ai-carousel__arrow {
  opacity: 1;
  transform: none;
  pointer-events: auto;
}

.arrow-hover .ai-carousel__arrow.is-prev {
  transform: translateX(-8px);
}

.arrow-hover .ai-carousel__arrow.is-next {
  transform: translateX(8px);
}

.arrow-hover:hover .ai-carousel__arrow.is-prev,
.arrow-hover:hover .ai-carousel__arrow.is-next {
  transform: none;
}

/* ---------- 指示器 ---------- */
.ai-carousel__dots {
  position: absolute;
  z-index: 3;
  display: flex;
  align-items: center;
  gap: 7px;
}

.indicator-inside .ai-carousel__dots {
  right: 22px;
  bottom: 18px;
}

.indicator-outside .ai-carousel__dots {
  left: 50%;
  bottom: -24px;
  transform: translateX(-50%);
}

.ai-carousel__dot {
  width: 8px;
  height: 8px;
  border-radius: var(--r-full);
  background: rgba(255, 255, 255, 0.62);
  box-shadow: 0 1px 4px rgba(0, 0, 0, 0.25);
  transition: width var(--dur-base) var(--ease-spring), background var(--dur-base);
}

.indicator-outside .ai-carousel__dot {
  background: var(--line-strong);
  box-shadow: none;
}

.ai-carousel__dot:hover {
  background: rgba(255, 255, 255, 0.9);
}

.indicator-outside .ai-carousel__dot:hover {
  background: var(--brand-300);
}

.ai-carousel__dot.on {
  width: 22px;
  background: var(--grad-brand);
}

@media (max-width: 720px) {
  .ai-carousel__arrow {
    display: none;
  }

  .indicator-inside .ai-carousel__dots {
    right: 50%;
    transform: translateX(50%);
  }
}
</style>
