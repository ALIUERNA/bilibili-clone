<script setup>
import { onMounted, onUnmounted, ref } from 'vue'

/**
 * 回到顶部（替代 <el-backtop>）。
 *
 * props:
 *   right             距离视口右侧的距离（px），默认 28
 *   bottom            距离视口底部的距离（px），默认 80
 *   visibilityHeight  页面滚动超过该距离后才显示按钮，默认 500
 *
 * 滚动的 smooth / 瞬间跳转由 prefers-reduced-motion 决定；卸载时会移除滚动监听。
 */
const props = defineProps({
  right: { type: Number, default: 28 },
  bottom: { type: Number, default: 80 },
  visibilityHeight: { type: Number, default: 500 }
})

const visible = ref(false)

/** 用户是否开启了「减少动态效果」 */
function prefersReducedMotion() {
  return window.matchMedia?.('(prefers-reduced-motion: reduce)').matches === true
}

function onScroll() {
  visible.value = window.scrollY > props.visibilityHeight
}

function backToTop() {
  window.scrollTo({ top: 0, left: 0, behavior: prefersReducedMotion() ? 'auto' : 'smooth' })
}

onMounted(() => {
  // 先同步一次，防止刷新后页面已滚动却看不到按钮
  onScroll()
  window.addEventListener('scroll', onScroll, { passive: true })
})

onUnmounted(() => {
  window.removeEventListener('scroll', onScroll)
})
</script>

<template>
  <transition name="ai-backtop">
    <button
      v-show="visible"
      class="ai-backtop"
      type="button"
      aria-label="回到顶部"
      title="回到顶部"
      :style="{ right: `${right}px`, bottom: `${bottom}px` }"
      @click="backToTop"
    >
      <svg
        class="ai-backtop-icon"
        width="20"
        height="20"
        viewBox="0 0 24 24"
        fill="none"
        stroke="currentColor"
        stroke-width="2.4"
        stroke-linecap="round"
        stroke-linejoin="round"
        aria-hidden="true"
        focusable="false"
      >
        <path d="M6 14.5 12 8.5l6 6" />
      </svg>
    </button>
  </transition>
</template>

<style scoped>
.ai-backtop {
  position: fixed;
  z-index: var(--z-sticky);
  display: flex;
  align-items: center;
  justify-content: center;
  width: 44px;
  height: 44px;
  border-radius: var(--r-full);
  background: var(--grad-brand);
  color: var(--brand-fg);
  box-shadow: var(--sd-brand);
  -webkit-tap-highlight-color: transparent;
  transition: transform var(--dur-base) var(--ease-out), box-shadow var(--dur-base) var(--ease-out);
}

.ai-backtop:hover {
  color: var(--brand-fg);
  transform: translateY(-2px);
  box-shadow: var(--sd-3);
}

.ai-backtop:active {
  transform: translateY(0) scale(0.94);
}

.ai-backtop-icon {
  display: block;
}

.ai-backtop-enter-active,
.ai-backtop-leave-active {
  transition: opacity var(--dur-base) var(--ease-out), transform var(--dur-base) var(--ease-out);
}

.ai-backtop-enter-from,
.ai-backtop-leave-to {
  opacity: 0;
  transform: translateY(8px) scale(0.9);
}
</style>
