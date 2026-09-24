<script setup>
/**
 * AiModal —— 模态框，替代 Element Plus 的 <el-dialog>。
 *
 * 相比 el-dialog：
 *   · 体积只有它的零头，且完全走自己的设计令牌；
 *   · 自带焦点陷阱、ESC 关闭、点遮罩关闭、打开时锁定 body 滚动；
 *   · 关闭后把焦点还给打开它的那个元素（无障碍）。
 *
 * 用法：
 *   <AiModal v-if="open" title="编辑资料" width="520" @close="open = false">
 *     ...内容...
 *     <template #footer><button class="btn btn-primary">保存</button></template>
 *   </AiModal>
 */
import { nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'

const props = defineProps({
  /** 标题，也可以直接用 header 插槽 */
  title: { type: String, default: '' },
  /** 面板宽度，数字按 px 处理 */
  width: { type: [Number, String], default: 480 },
  /** 点击遮罩是否关闭 */
  closeOnClickModal: { type: Boolean, default: true },
  /** 是否显示右上角关闭按钮 */
  showClose: { type: Boolean, default: true },
  /** 垂直方向的偏移，数字按 px 处理 */
  top: { type: [Number, String], default: '10vh' }
})

const emit = defineEmits(['close'])

const panelRef = ref(null)
let lastActive = null
let lockedOverflow = ''

const style = {
  width: typeof props.width === 'number' ? `${props.width}px` : props.width,
  marginTop: typeof props.top === 'number' ? `${props.top}px` : props.top
}

/** 焦点陷阱：Tab 在面板内循环 */
function onKeydown(e) {
  if (e.key === 'Escape') {
    e.stopPropagation()
    emit('close')
    return
  }
  if (e.key !== 'Tab' || !panelRef.value) return

  const focusable = panelRef.value.querySelectorAll(
    'a[href], button:not([disabled]), textarea, input, select, [tabindex]:not([tabindex="-1"])'
  )
  if (!focusable.length) return
  const first = focusable[0]
  const last = focusable[focusable.length - 1]

  if (e.shiftKey && document.activeElement === first) {
    e.preventDefault()
    last.focus()
  } else if (!e.shiftKey && document.activeElement === last) {
    e.preventDefault()
    first.focus()
  }
}

function onMaskClick() {
  if (props.closeOnClickModal) emit('close')
}

onMounted(async () => {
  lastActive = document.activeElement
  // 打开时锁滚动，记录原始值以便还原
  lockedOverflow = document.body.style.overflow
  document.body.style.overflow = 'hidden'
  document.addEventListener('keydown', onKeydown, true)

  await nextTick()
  // 优先聚焦第一个表单控件，没有就聚焦面板本身
  const target =
    panelRef.value?.querySelector(
      'input:not([type="hidden"]), textarea, select, button:not(.close-btn)'
    ) || panelRef.value
  target?.focus?.()
})

onBeforeUnmount(() => {
  document.removeEventListener('keydown', onKeydown, true)
  document.body.style.overflow = lockedOverflow
  // 把焦点交还给触发弹窗的元素，避免焦点掉到 body 上
  if (lastActive && typeof lastActive.focus === 'function') lastActive.focus()
})

// 父组件用 v-if 控制显隐，这里只处理宽度变化
watch(
  () => props.width,
  () => {}
)
</script>

<template>
  <div class="ai-modal" role="dialog" aria-modal="true" :aria-label="title || undefined">
    <div class="ai-modal__mask" @click="onMaskClick"></div>

    <div ref="panelRef" class="ai-modal__panel" :style="style" tabindex="-1">
      <header v-if="title || $slots.header" class="ai-modal__head">
        <slot name="header">
          <h3>{{ title }}</h3>
        </slot>
        <button v-if="showClose" class="close-btn" aria-label="关闭" @click="emit('close')">
          <AiIcon :size="18"><Close /></AiIcon>
        </button>
      </header>
      <span v-else-if="showClose" class="ai-modal__floating-close">
        <button class="close-btn" aria-label="关闭" @click="emit('close')">
          <AiIcon :size="18"><Close /></AiIcon>
        </button>
      </span>

      <div class="ai-modal__body">
        <slot />
      </div>

      <footer v-if="$slots.footer" class="ai-modal__foot">
        <slot name="footer" />
      </footer>
    </div>
  </div>
</template>

<style scoped>
.ai-modal {
  position: fixed;
  inset: 0;
  z-index: var(--z-modal);
  display: flex;
  align-items: flex-start;
  justify-content: center;
  padding: 0 16px 40px;
  overflow-y: auto;
  -webkit-overflow-scrolling: touch;
}

.ai-modal__mask {
  position: fixed;
  inset: 0;
  background: rgba(16, 18, 24, 0.46);
  backdrop-filter: blur(3px);
  -webkit-backdrop-filter: blur(3px);
  animation: mask-in var(--dur-base) var(--ease-out) both;
}

@keyframes mask-in {
  from {
    opacity: 0;
  }
  to {
    opacity: 1;
  }
}

.ai-modal__panel {
  position: relative;
  z-index: 1;
  max-width: 100%;
  margin-bottom: auto;
  background: var(--surface-1);
  border-radius: var(--r-lg);
  box-shadow: var(--sd-4);
  animation: panel-in var(--dur-base) var(--ease-spring) both;
  outline: none;
  overflow: hidden;
}

@keyframes panel-in {
  from {
    opacity: 0;
    transform: translateY(-14px) scale(0.97);
  }
  to {
    opacity: 1;
    transform: none;
  }
}

.ai-modal__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 18px 20px 14px;
}

.ai-modal__head h3 {
  font-size: var(--fs-lg);
  font-weight: var(--fw-bold);
}

.close-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 30px;
  height: 30px;
  border-radius: var(--r-sm);
  color: var(--ink-3);
  transition: background var(--dur-fast), color var(--dur-fast), transform var(--dur-fast);
}

.close-btn:hover {
  background: var(--surface-sunken);
  color: var(--ink-1);
  transform: rotate(90deg);
}

.ai-modal__floating-close {
  position: absolute;
  top: 12px;
  right: 12px;
  z-index: 2;
}

.ai-modal__body {
  padding: 0 20px 20px;
}

.ai-modal__foot {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 10px;
  padding: 14px 20px;
  border-top: 1px solid var(--line);
  background: var(--surface-2);
}

@media (max-width: 520px) {
  .ai-modal {
    padding: 0 10px 24px;
  }

  .ai-modal__panel {
    border-radius: var(--r-md);
  }
}
</style>
