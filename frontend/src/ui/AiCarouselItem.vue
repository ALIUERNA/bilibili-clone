<script setup>
/**
 * AiCarouselItem —— <AiCarousel> 的每一屏。
 * 自己不做动画，只负责注册到父级（决定指示器数量）并占满整屏宽度。
 */
import { inject, onBeforeUnmount, onMounted, ref } from 'vue'

// label 由父级 / 模板透传到根元素（inheritAttrs 默认开启），这里不用接成变量
defineProps({
  /** 无障碍标签，读屏时用来区分第几屏 */
  label: { type: String, default: '' }
})

const rootRef = ref(null)
const uid = Symbol('carousel-item')
const carousel = inject('ai-carousel', null)

onMounted(() => {
  carousel?.register({ uid, el: rootRef.value })
})

onBeforeUnmount(() => {
  carousel?.unregister(uid)
})
</script>

<template>
  <div ref="rootRef" class="ai-carousel__item" role="group" :aria-label="label || undefined">
    <slot />
  </div>
</template>

<style scoped>
.ai-carousel__item {
  position: relative;
  flex: 0 0 100%;
  width: 100%;
  height: 100%;
  overflow: hidden;
}
</style>
