<script setup>
/**
 * AiPopover —— 悬停/点击弹出的浮层，替代 Element Plus 的 <el-popover>。
 *
 * 与 el-popover 的差别：
 *   · 用 Teleport 挂到 body，再按触发元素的位置算 fixed 坐标，
 *     所以不会被父级的 overflow:hidden 裁掉（顶栏、卡片里都能用）；
 *   · 只实现项目真正用得到的几个方向，代码量小很多。
 *
 * 用法（顶栏头像的悬停资料卡）：
 *   <AiPopover placement="bottom-end" :width="330" :show-after="150">
 *     <template #reference><div class="avatar">…</div></template>
 *     <UserHoverCard />
 *   </AiPopover>
 */
import { computed, nextTick, onBeforeUnmount, ref, watch } from 'vue'

const props = defineProps({
  /** bottom / bottom-start / bottom-end / top / top-end / right */
  placement: { type: String, default: 'bottom' },
  width: { type: [Number, String], default: 320 },
  /** 触发方式：hover | click | manual */
  trigger: { type: String, default: 'hover' },
  showAfter: { type: Number, default: 120 },
  hideAfter: { type: Number, default: 160 },
  /** 与触发元素的距离 */
  offset: { type: Number, default: 10 },
  disabled: { type: Boolean, default: false }
})

const visible = ref(false)
const triggerRef = ref(null)
const panelRef = ref(null)
const pos = ref({ top: 0, left: 0 })

let showTimer = null
let hideTimer = null

const panelStyle = computed(() => ({
  top: `${pos.value.top}px`,
  left: `${pos.value.left}px`,
  width: typeof props.width === 'number' ? `${props.width}px` : props.width
}))

/** 按触发元素的位置推算浮层坐标，并做视口边界收敛 */
async function place() {
  await nextTick()
  const t = triggerRef.value?.getBoundingClientRect?.()
  if (!t) return
  const panel = panelRef.value?.getBoundingClientRect?.()
  const w = panel?.width || Number(props.width) || 320
  const h = panel?.height || 0

  let top
  let left

  if (props.placement.startsWith('top')) {
    top = t.top - h - props.offset
  } else if (props.placement === 'right') {
    top = t.top + t.height / 2 - h / 2
    left = t.right + props.offset
  } else {
    top = t.bottom + props.offset
  }

  if (props.placement !== 'right') {
    if (props.placement.endsWith('end')) left = t.right - w
    else if (props.placement.endsWith('start')) left = t.left
    else left = t.left + t.width / 2 - w / 2
  }

  // 不让浮层跑出视口
  const maxLeft = window.innerWidth - w - 10
  left = Math.max(10, Math.min(left, Math.max(10, maxLeft)))
  if (h) {
    const maxTop = window.innerHeight - h - 10
    top = Math.max(10, Math.min(top, Math.max(10, maxTop)))
  }

  pos.value = { top, left }
}

function clear() {
  clearTimeout(showTimer)
  clearTimeout(hideTimer)
}

async function open() {
  if (props.disabled) return
  clear()
  showTimer = setTimeout(async () => {
    visible.value = true
    await place()
    window.addEventListener('scroll', place, true)
    window.addEventListener('resize', place)
  }, props.showAfter)
}

function close() {
  clear()
  hideTimer = setTimeout(() => {
    visible.value = false
    unbind()
  }, props.hideAfter)
}

function unbind() {
  window.removeEventListener('scroll', place, true)
  window.removeEventListener('resize', place)
}

function onTriggerClick() {
  if (props.trigger !== 'click' || props.disabled) return
  if (visible.value) {
    visible.value = false
    unbind()
  } else {
    open()
  }
}

// 内容变高变矮（比如资料卡里的数据加载完）后重新定位
watch(visible, (v) => {
  if (v) nextTick(place)
})

onBeforeUnmount(() => {
  clear()
  unbind()
})

defineExpose({ open, close, visible })
</script>

<template>
  <span
    ref="triggerRef"
    class="ai-pop-trigger"
    @mouseenter="trigger === 'hover' && open()"
    @mouseleave="trigger === 'hover' && close()"
    @focusin="open"
    @focusout="close"
    @click="onTriggerClick"
  >
    <slot name="reference" />
  </span>

  <Teleport to="body">
    <transition name="pop">
      <div
        v-if="visible"
        ref="panelRef"
        class="ai-pop"
        :style="panelStyle"
        @mouseenter="clear()"
        @mouseleave="close"
      >
        <slot />
      </div>
    </transition>
  </Teleport>
</template>

<style scoped>
.ai-pop-trigger {
  display: inline-flex;
}

.ai-pop {
  position: fixed;
  z-index: var(--z-menu);
  background: var(--surface-1);
  border: 1px solid var(--line);
  border-radius: var(--r-md);
  box-shadow: var(--sd-3);
  overflow: hidden;
}

.pop-enter-active {
  transition: opacity var(--dur-base) var(--ease-out), transform var(--dur-base) var(--ease-spring);
}

.pop-leave-active {
  transition: opacity var(--dur-fast), transform var(--dur-fast);
}

.pop-enter-from,
.pop-leave-to {
  opacity: 0;
  transform: translateY(-8px) scale(0.97);
}
</style>
