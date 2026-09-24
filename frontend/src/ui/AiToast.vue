<script setup>
/**
 * AiToast —— 全局提示的渲染宿主，在 App.vue 里挂一次即可。
 * 数据来自 src/ui/toast.js 的响应式队列。
 */
import { toasts } from './toast'

const ICON = {
  info: 'Bell',
  success: 'CircleCheckFilled',
  error: 'Close',
  warn: 'QuestionFilled'
}
</script>

<template>
  <div class="toast-layer" role="status" aria-live="polite">
    <transition-group name="toast">
      <div v-for="t in toasts.list" :key="t.id" class="toast" :class="`is-${t.type}`">
        <span class="ico">
          <AiIcon :size="15"><component :is="ICON[t.type] || 'Bell'" /></AiIcon>
        </span>
        <span class="text">{{ t.text }}</span>
      </div>
    </transition-group>
  </div>
</template>

<style scoped>
.toast-layer {
  position: fixed;
  top: calc(var(--header-height) + 14px);
  left: 50%;
  transform: translateX(-50%);
  z-index: var(--z-toast);
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
  pointer-events: none;
}

.toast {
  display: flex;
  align-items: center;
  gap: 8px;
  max-width: min(78vw, 460px);
  padding: 10px 16px 10px 12px;
  border-radius: var(--r-full);
  background: var(--surface-1);
  border: 1px solid var(--line);
  box-shadow: var(--sd-3);
  font-size: var(--fs-base);
  font-weight: var(--fw-medium);
  color: var(--ink-1);
}

.toast .ico {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 22px;
  height: 22px;
  border-radius: 50%;
  flex-shrink: 0;
  color: #fff;
  background: var(--grad-brand);
}

.toast .text {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

/* 类型差异只体现在图标底色上，整体保持克制的品牌感 */
.toast.is-success .ico {
  background: var(--mint-500);
}

.toast.is-error .ico {
  background: var(--rose-500);
}

.toast.is-warn .ico {
  background: var(--gold-500);
}

/* 动画：从上方落下 + 淡出 */
.toast-enter-active {
  transition: opacity var(--dur-base) var(--ease-out), transform var(--dur-base) var(--ease-spring);
}

.toast-leave-active {
  transition: opacity var(--dur-fast), transform var(--dur-fast);
  position: absolute;
}

.toast-enter-from {
  opacity: 0;
  transform: translateY(-14px) scale(0.94);
}

.toast-leave-to {
  opacity: 0;
  transform: translateY(-8px) scale(0.96);
}

.toast-move {
  transition: transform var(--dur-base) var(--ease-out);
}
</style>
