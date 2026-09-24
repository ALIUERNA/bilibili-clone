<script setup>
import { computed } from 'vue'
import AiSkeletonItem from './AiSkeletonItem.vue'

/**
 * 骨架屏容器（替代 <el-skeleton>，配合 AiSkeletonItem 使用）。
 *
 * props:
 *   loading   是否处于加载中，默认 true；为 false 时只渲染默认插槽（真实内容）
 *   animated  骨架是否播放微光动画，默认 true（动画来自 global.css 的 .skeleton）
 *   rows      默认插槽为空时，自动生成的占位文字条数量，默认 0
 *
 * 用法：把 AiSkeletonItem 放进默认插槽即可，此时本身就是加载态。
 */
const props = defineProps({
  animated: { type: Boolean, default: true },
  rows: { type: Number, default: 0 },
  loading: { type: Boolean, default: true }
})

/** 自动占位行数，兜底处理负数 / 小数 / NaN */
const rowsCount = computed(() => {
  const value = Number(props.rows)
  return Number.isFinite(value) ? Math.max(0, Math.floor(value)) : 0
})
</script>

<template>
  <div v-if="loading" class="ai-skeleton" :class="{ 'is-animated': animated }" aria-busy="true">
    <span class="ai-skeleton-sr">内容加载中</span>
    <slot>
      <AiSkeletonItem
        v-for="i in rowsCount"
        :key="i"
        variant="text"
        :width="i === rowsCount ? '62%' : '100%'"
      />
    </slot>
  </div>
  <slot v-else />
</template>

<style scoped>
.ai-skeleton {
  position: relative;
  display: block;
}

/* 自动生成的占位行之间留一点间隙 */
.ai-skeleton-item + .ai-skeleton-item {
  margin-top: var(--sp-3);
}

/* animated 关闭时只停掉动画，保留 .skeleton 的静态底色 */
.ai-skeleton:not(.is-animated) :deep(.skeleton) {
  animation: none;
}

/* 屏幕阅读器可见、视觉隐藏的加载提示 */
.ai-skeleton-sr {
  position: absolute;
  width: 1px;
  height: 1px;
  margin: -1px;
  padding: 0;
  overflow: hidden;
  clip-path: inset(50%);
  white-space: nowrap;
  border: 0;
}
</style>
