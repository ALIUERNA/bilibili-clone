<script setup>
/**
 * AiLogo —— a哩a哩 的图形标志。
 *
 * 设计说明（刻意和「小电视」拉开距离）
 * ------------------------------------
 * 哔哩哔哩的标志是一只带天线的电视，粉底白屏，识别度极高——
 * 但也正因为太高，任何"电视 + 天线"的造型都会立刻被看成模仿。
 * 所以这里换一个完全不同的出发角度：**弹幕本身**。
 *
 * 图形拆解：
 *   · 圆角方块  —— 一块"屏幕"，承载极光渐变，作为品牌色块；
 *   · 播放三角  —— 视频/播放，压在方块中央，是最容易读懂的一层；
 *   · 两道弹幕条 —— 一上一下、长短错落、透明度不同，从三角两侧掠出画面。
 *      它们既像弹幕正在飞过，也给了标志"速度感"和不对称的呼吸感。
 *
 * 三层叠起来讲的是同一件事：**这里的视频会飘弹幕**。
 * 造型上没有任何一条曲线是照着别人画的，方块 + 三角 + 横条全是基础几何形。
 *
 * 尺寸用法：默认 28px，会随字号缩放；传 size 可指定数值(px)或任意 CSS 长度。
 */
defineProps({
  /** 数字按 px，其它按 CSS 长度原样使用 */
  size: { type: [Number, String], default: 28 },
  /** 是否显示「a哩a哩」文字 */
  wordmark: { type: Boolean, default: false },
  /** 文字大小，只在 wordmark 为 true 时生效 */
  textSize: { type: [Number, String], default: 19 }
})

/** 同一页面可能出现多个 logo，用随机后缀避免 <defs> 里的 id 撞车 */
const uid = `aili-mark-${Math.random().toString(36).slice(2, 9)}`
</script>

<template>
  <span class="ai-logo">
    <svg
      class="mark"
      :width="typeof size === 'number' ? size : undefined"
      :height="typeof size === 'number' ? size : undefined"
      :style="typeof size === 'number' ? undefined : { width: size, height: size }"
      viewBox="0 0 32 32"
      role="img"
      aria-label="a哩a哩"
    >
      <defs>
        <linearGradient :id="uid" x1="0" y1="0" x2="1" y2="1">
          <stop offset="0%" stop-color="#7c5cff" />
          <stop offset="52%" stop-color="#9b6bf5" />
          <stop offset="100%" stop-color="#ff7a45" />
        </linearGradient>
      </defs>

      <!-- 屏幕 -->
      <rect width="32" height="32" rx="9.5" :fill="`url(#${uid})`" />

      <!-- 弹幕条：上方一条长的，右下方一条短的，长短 / 透明度都错开 -->
      <rect x="5.4" y="6.4" width="9.4" height="2.5" rx="1.25" fill="#fff" opacity="0.92" />
      <rect x="18.6" y="23.1" width="8" height="2.5" rx="1.25" fill="#fff" opacity="0.5" />

      <!-- 播放三角：圆角用 stroke 的 linejoin 做，比手写贝塞尔稳 -->
      <path
        d="M13.4 12.2 L21.2 16.2 L13.4 20.2 Z"
        fill="#fff"
        stroke="#fff"
        stroke-width="2.8"
        stroke-linejoin="round"
        stroke-linecap="round"
      />
    </svg>

    <span
      v-if="wordmark"
      class="word"
      :style="{ fontSize: typeof textSize === 'number' ? `${textSize}px` : textSize }"
      >a哩a哩</span
    >
  </span>
</template>

<style scoped>
.ai-logo {
  display: inline-flex;
  align-items: center;
  gap: 9px;
  flex-shrink: 0;
}

.mark {
  display: block;
  flex-shrink: 0;
}

.word {
  font-weight: var(--fw-bold, 700);
  letter-spacing: 0.2px;
  color: var(--ink-1);
  white-space: nowrap;
  line-height: 1;
}
</style>
