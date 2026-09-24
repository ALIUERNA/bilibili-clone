<script setup>
/**
 * 空状态占位（替代 <el-empty>）。
 *
 * props:
 *   description  说明文字，传空字符串则只显示插画
 *   imageSize    插画尺寸（px），默认 96
 *
 * 默认插槽放在文字下方，一般用来放「回首页」之类的操作按钮。
 */
defineProps({
  description: { type: String, default: '' },
  imageSize: { type: Number, default: 96 }
})
</script>

<template>
  <div class="ai-empty">
    <!-- 装饰性插画：虚线胶片框 + 弹幕气泡 + 小星星，颜色全部走主题令牌 -->
    <svg
      class="ai-empty-art"
      :width="imageSize"
      :height="imageSize"
      viewBox="0 0 96 96"
      fill="none"
      aria-hidden="true"
      focusable="false"
    >
      <rect class="art-frame" x="14" y="16" width="68" height="66" rx="16" />
      <path
        class="art-bubble"
        d="M30 24h36a10 10 0 0 1 10 10v18a10 10 0 0 1-10 10H46l-8 8v-8h-8a10 10 0 0 1-10-10V34a10 10 0 0 1 10-10Z"
      />
      <rect class="art-line" x="30" y="36" width="30" height="5" rx="2.5" />
      <rect class="art-line dim" x="30" y="47" width="18" height="5" rx="2.5" />
      <path class="art-star" d="M76 12l2.4 5.4 5.4 2.4-5.4 2.4L76 27.6l-2.4-5.4-5.4-2.4 5.4-2.4L76 12Z" />
      <rect class="art-danmaku" x="2" y="38" width="9" height="4" rx="2" />
      <rect class="art-danmaku" x="85" y="60" width="9" height="4" rx="2" />
    </svg>
    <p v-if="description" class="ai-empty-desc">{{ description }}</p>
    <div v-if="$slots.default" class="ai-empty-extra">
      <slot />
    </div>
  </div>
</template>

<style scoped>
.ai-empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: var(--sp-3);
  padding: var(--sp-8) var(--sp-4);
  text-align: center;
}

.ai-empty-art {
  flex-shrink: 0;
}

/* 虚线胶片框 */
.art-frame {
  fill: none;
  stroke: var(--line-strong);
  stroke-width: 2;
  stroke-dasharray: 6 7;
  stroke-linecap: round;
  opacity: 0.9;
}

/* 弹幕气泡 */
.art-bubble {
  fill: var(--brand-200);
  fill-opacity: 0.55;
  stroke: var(--brand-300);
  stroke-width: 2;
  stroke-linejoin: round;
}

/* 气泡里的文字条 */
.art-line {
  fill: var(--brand-500);
  opacity: 0.55;
}

.art-line.dim {
  opacity: 0.3;
}

/* 小星星：暗一点，只做点缀 */
.art-star {
  fill: var(--accent-400);
  opacity: 0.9;
}

/* 飘过的迷你弹幕 */
.art-danmaku {
  fill: var(--line-strong);
  opacity: 0.75;
}

.ai-empty-desc {
  max-width: 420px;
  color: var(--ink-3);
  font-size: var(--fs-base);
  line-height: var(--lh-base);
}

.ai-empty-extra {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: center;
  gap: var(--sp-3);
}
</style>
