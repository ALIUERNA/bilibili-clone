<script setup>
import { computed } from 'vue'

/**
 * 进度条（替代 <el-progress>）。
 *
 * props:
 *   percentage   进度百分比，0-100，必填（超出范围会自动夹紧）
 *   strokeWidth  进度条高度（px），默认 8
 *   showText     是否显示右侧文字，默认 true
 *   color        自定义填充色（CSS 颜色或变量），不传则用品牌极光渐变
 *
 * 具名插槽 text 可以自定义右侧文案，不传时显示百分比。
 */
const props = defineProps({
  percentage: { type: Number, required: true },
  strokeWidth: { type: Number, default: 8 },
  showText: { type: Boolean, default: true },
  color: { type: String, default: '' }
})

/** 把外部传入的百分比夹到 0-100，避免进度条越界 */
const percent = computed(() => {
  const value = Number(props.percentage)
  if (!Number.isFinite(value)) return 0
  return Math.min(100, Math.max(0, value))
})

/** 轨道高度兜底，至少 2px 才看得见 */
const barHeight = computed(() => {
  const value = Number(props.strokeWidth)
  return Number.isFinite(value) ? Math.max(2, value) : 8
})

const displayText = computed(() => `${Math.round(percent.value)}%`)

const barStyle = computed(() => ({
  width: `${percent.value}%`,
  background: props.color || 'var(--grad-brand)'
}))
</script>

<template>
  <div class="ai-progress">
    <div
      class="ai-progress-track"
      :style="{ height: `${barHeight}px` }"
      role="progressbar"
      :aria-valuenow="percent"
      aria-valuemin="0"
      aria-valuemax="100"
    >
      <div class="ai-progress-fill" :style="barStyle" />
    </div>
    <div v-if="showText" class="ai-progress-text">
      <slot name="text">
        <span class="num">{{ displayText }}</span>
      </slot>
    </div>
  </div>
</template>

<style scoped>
.ai-progress {
  display: flex;
  align-items: center;
  gap: var(--sp-3);
  width: 100%;
}

.ai-progress-track {
  position: relative;
  flex: 1;
  min-width: 0;
  overflow: hidden;
  background: var(--surface-sunken);
  border-radius: var(--r-full);
}

.ai-progress-fill {
  height: 100%;
  border-radius: var(--r-full);
  transition: width var(--dur-slow) var(--ease-out), background var(--dur-base) var(--ease-out);
}

.ai-progress-text {
  flex-shrink: 0;
  min-width: 40px;
  color: var(--ink-2);
  font-size: var(--fs-xs);
  line-height: 1;
  text-align: right;
}
</style>
