<script setup>
import { computed, provide, ref } from 'vue'

/**
 * 面包屑容器（替代 <el-breadcrumb>）。
 *
 * props:
 *   separator  分隔符，默认 '/'
 *
 * 通过 provide('aiBreadcrumb', ...) 把分隔符和「谁是最后一项」的判断下发给
 * AiBreadcrumbItem，因此两个组件要配套使用。
 */
const props = defineProps({
  separator: { type: String, default: '/' }
})

/** 已挂载子项的根元素，用来判断最后一项（兼容 v-for / v-if 动态增删） */
const items = ref([])

function registerItem(el) {
  if (!el) return () => {}
  items.value.push(el)
  return () => {
    items.value = items.value.filter((item) => item !== el)
  }
}

/** 按真实 DOM 顺序取最后一个已登记的子项 */
const lastItem = computed(() => {
  let last = null
  for (const el of items.value) {
    if (!last) {
      last = el
      continue
    }
    // 4 === Node.DOCUMENT_POSITION_FOLLOWING，说明 el 排在 last 之后
    if (last.compareDocumentPosition(el) & 4) last = el
  }
  return last
})

provide('aiBreadcrumb', {
  separator: computed(() => props.separator),
  isLast: (el) => lastItem.value === el,
  registerItem
})
</script>

<template>
  <nav class="ai-breadcrumb-nav" aria-label="面包屑">
    <ol class="ai-breadcrumb">
      <slot />
    </ol>
  </nav>
</template>

<style scoped>
.ai-breadcrumb {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  font-size: var(--fs-sm);
  line-height: var(--lh-tight);
  color: var(--ink-2);
}
</style>
