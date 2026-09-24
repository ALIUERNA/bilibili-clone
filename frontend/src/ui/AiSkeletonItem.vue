<script setup>
import { computed } from 'vue'

/**
 * 骨架屏单元（替代 <el-skeleton-item>）。
 *
 * props:
 *   variant  骨架形态：'image' | 'text' | 'circle' | 'button'，默认 'text'
 *   width    宽度（Number 会补 px，String 原样使用），不传则用该形态的默认值
 *   height   高度（Number 会补 px，String 原样使用），不传则用该形态的默认值
 *
 * 微光动画直接复用 global.css 里的 .skeleton，不在这里重复定义 keyframes。
 */
const props = defineProps({
  variant: {
    type: String,
    default: 'text',
    validator: (value) => ['image', 'text', 'circle', 'button'].includes(value)
  },
  width: { type: [String, Number], default: undefined },
  height: { type: [String, Number], default: undefined }
})

/** 数字统一补成 px，字符串（如 '85%'）原样使用 */
function toSize(value) {
  return typeof value === 'number' ? `${value}px` : value
}

const sizeStyle = computed(() => {
  const style = {}
  if (props.width != null && props.width !== '') style.width = toSize(props.width)
  if (props.height != null && props.height !== '') style.height = toSize(props.height)
  // 圆形只给了宽度时，高度跟着宽度走，保证还是正圆
  if (props.variant === 'circle' && style.width && !style.height) style.height = style.width
  return style
})
</script>

<template>
  <span
    class="ai-skeleton-item skeleton"
    :class="`is-${variant}`"
    :style="sizeStyle"
    aria-hidden="true"
  />
</template>

<style scoped>
.ai-skeleton-item {
  display: block;
  flex-shrink: 0;
}

.ai-skeleton-item.is-text {
  width: 100%;
  height: 16px;
  border-radius: var(--r-xs);
}

/* 视频封面是站内的主角，图片骨架默认按 16:9 撑开 */
.ai-skeleton-item.is-image {
  width: 100%;
  height: 120px;
  border-radius: var(--r-md);
}

.ai-skeleton-item.is-circle {
  width: 40px;
  height: 40px;
  border-radius: var(--r-full);
}

.ai-skeleton-item.is-button {
  width: 88px;
  height: 34px;
  border-radius: var(--r-sm);
}

@supports (aspect-ratio: 16 / 9) {
  .ai-skeleton-item.is-image {
    height: auto;
    aspect-ratio: 16 / 9;
  }
}
</style>
