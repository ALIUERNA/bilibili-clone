<script setup>
/**
 * AiIcon —— 图标容器，替代 Element Plus 的 <AiIcon>。
 *
 * 用法与 el-icon 完全一致，但尺寸 / 颜色由自己的设计令牌驱动：
 *   <AiIcon :size="18"><Search /></AiIcon>
 *   <AiIcon :size="20" color="#fff"><VideoPlay /></AiIcon>
 *
 * 内部的图标组件（Search / VideoPlay …）由 src/icons.js 全局注册，
 * 它们都是 1em 大小的 SVG，所以这里只要控制 font-size 就能缩放。
 */
import { computed } from 'vue'

const props = defineProps({
  /** 图标尺寸：数字按 px 处理，字符串原样使用（如 '1.2em'） */
  size: { type: [Number, String], default: 16 },
  /** 图标颜色，不传就继承父级 currentColor */
  color: { type: String, default: '' }
})

const style = computed(() => ({
  fontSize: typeof props.size === 'number' ? `${props.size}px` : props.size,
  ...(props.color ? { color: props.color } : {})
}))
</script>

<template>
  <span class="ai-icon" :style="style" aria-hidden="true">
    <slot />
  </span>
</template>

<style scoped>
.ai-icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 1em;
  height: 1em;
  line-height: 1;
  flex-shrink: 0;
  vertical-align: -0.14em;
}

.ai-icon :deep(svg) {
  width: 1em;
  height: 1em;
  display: block;
}
</style>
