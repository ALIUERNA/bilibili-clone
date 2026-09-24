<script setup>
/**
 * AiTooltip —— 轻量文字提示，替代 Element Plus 的 <el-tooltip>。
 *
 * 只做「鼠标悬停 / 键盘聚焦 → 在触发元素旁边冒出一句说明」这一件事，
 * 所以不需要引入 popper.js：一个绝对定位的胶囊 + 两个定时器就够了。
 *
 * 用法：
 *   <AiTooltip content="消息中心" placement="bottom" :show-after="400">
 *     <button class="icon-btn">…</button>
 *   </AiTooltip>
 */
import { onBeforeUnmount, ref } from 'vue'

const props = defineProps({
  content: { type: String, default: '' },
  /** bottom | top | right | left */
  placement: { type: String, default: 'bottom' },
  /** 悬停多久才显示（毫秒），避免鼠标划过时到处弹 */
  showAfter: { type: Number, default: 300 },
  hideAfter: { type: Number, default: 80 },
  disabled: { type: Boolean, default: false }
})

const visible = ref(false)
let showTimer = null
let hideTimer = null

function clear() {
  clearTimeout(showTimer)
  clearTimeout(hideTimer)
}

function open() {
  if (props.disabled || !props.content) return
  clear()
  showTimer = setTimeout(() => (visible.value = true), props.showAfter)
}

function close() {
  clear()
  hideTimer = setTimeout(() => (visible.value = false), props.hideAfter)
}

onBeforeUnmount(clear)
</script>

<template>
  <span
    class="ai-tip"
    @mouseenter="open"
    @mouseleave="close"
    @focusin="open"
    @focusout="close"
    @click="close"
  >
    <slot />
    <transition name="tip">
      <span v-if="visible" class="ai-tip__bubble" :class="`is-${placement}`" role="tooltip">
        {{ content }}
      </span>
    </transition>
  </span>
</template>

<style scoped>
.ai-tip {
  position: relative;
  display: inline-flex;
}

.ai-tip__bubble {
  position: absolute;
  z-index: var(--z-menu);
  padding: 5px 10px;
  border-radius: var(--r-sm);
  background: rgba(20, 22, 26, 0.92);
  color: #fff;
  font-size: var(--fs-xs);
  line-height: 1.5;
  white-space: nowrap;
  pointer-events: none;
  box-shadow: var(--sd-2);
}

.theme-dark .ai-tip__bubble,
[data-theme='dark'] .ai-tip__bubble {
  background: #2c313a;
}

.ai-tip__bubble.is-bottom {
  top: calc(100% + 8px);
  left: 50%;
  transform: translateX(-50%);
}

.ai-tip__bubble.is-top {
  bottom: calc(100% + 8px);
  left: 50%;
  transform: translateX(-50%);
}

.ai-tip__bubble.is-right {
  left: calc(100% + 8px);
  top: 50%;
  transform: translateY(-50%);
}

.ai-tip__bubble.is-left {
  right: calc(100% + 8px);
  top: 50%;
  transform: translateY(-50%);
}

.tip-enter-active,
.tip-leave-active {
  transition: opacity var(--dur-fast), transform var(--dur-fast) var(--ease-out);
}

.tip-enter-from,
.tip-leave-to {
  opacity: 0;
}
</style>
