<script setup>
import { computed, inject, onMounted, onUnmounted, ref } from 'vue'

/**
 * 面包屑单项（替代 <el-breadcrumb-item>）。
 *
 * props:
 *   to  目标路由（String | Object），可选。传了就渲染成 <router-link>，否则是纯文本。
 *
 * 从父级 AiBreadcrumb inject 分隔符和「是否最后一项」：最后一项会带上
 * aria-current="page" 并使用更浅的文字色。单独使用时退化为普通文本。
 */
defineProps({
  to: { type: [String, Object], default: undefined }
})

const ctx = inject('aiBreadcrumb', null)

const rootEl = ref(null)
let unregister = null

const isLast = computed(() => (ctx ? ctx.isLast(rootEl.value) : false))

/** 父级没有提供分隔符时的兜底 */
const separator = computed(() => ctx?.separator?.value ?? '/')

onMounted(() => {
  if (ctx) unregister = ctx.registerItem(rootEl.value)
})

onUnmounted(() => {
  if (unregister) unregister()
})
</script>

<template>
  <li
    ref="rootEl"
    class="ai-breadcrumb-item"
    :class="{ 'is-last': isLast }"
    :aria-current="isLast ? 'page' : undefined"
  >
    <router-link v-if="to" class="ai-breadcrumb-inner" :to="to">
      <slot />
    </router-link>
    <span v-else class="ai-breadcrumb-inner">
      <slot />
    </span>
    <span v-if="ctx && !isLast" class="ai-breadcrumb-separator" aria-hidden="true">{{ separator }}</span>
  </li>
</template>

<style scoped>
.ai-breadcrumb-item {
  display: inline-flex;
  align-items: center;
  min-width: 0;
}

.ai-breadcrumb-inner {
  max-width: 260px;
  overflow: hidden;
  color: var(--ink-2);
  text-overflow: ellipsis;
  white-space: nowrap;
  transition: color var(--dur-fast) var(--ease-out);
}

a.ai-breadcrumb-inner:hover {
  color: var(--brand-600);
}

/* 最后一项：当前页，颜色更浅、不可点 */
.ai-breadcrumb-item.is-last .ai-breadcrumb-inner {
  color: var(--ink-3);
}

.ai-breadcrumb-separator {
  margin: 0 var(--sp-2);
  color: var(--ink-4);
  user-select: none;
}
</style>
