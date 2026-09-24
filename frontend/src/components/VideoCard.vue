<script setup>
import { computed, ref } from 'vue'
import { useRouter } from 'vue-router'
import { useMouseInElement } from '@vueuse/core'
import { coverStyle, formatCount, formatDuration } from '../utils/format'

const props = defineProps({
  video: { type: Object, required: true },
  // grid：首页网格卡片；list：右侧相关推荐那种横向小卡；rank：排行榜行
  layout: { type: String, default: 'grid' },
  rank: { type: Number, default: 0 },
  showUp: { type: Boolean, default: true }
})

const router = useRouter()

// 鼠标在卡片上移动时，卡片会朝着鼠标方向轻轻倾斜（用 VueUse 的 useMouseInElement 拿到相对坐标）
const cardRef = ref(null)
const { elementX, elementY, elementWidth, elementHeight, isOutside } = useMouseInElement(cardRef)

const tiltStyle = computed(() => {
  if (props.layout !== 'grid' || isOutside.value || !elementWidth.value) return {}
  const px = elementX.value / elementWidth.value - 0.5
  const py = elementY.value / elementHeight.value - 0.5
  return {
    transform: `perspective(900px) rotateY(${(px * 7).toFixed(2)}deg) rotateX(${(-py * 6).toFixed(2)}deg) translateY(-2px)`
  }
})

const cover = computed(() => coverStyle(props.video))
const durationText = computed(() => formatDuration(props.video.duration))
const viewText = computed(() => formatCount(props.video.views))
const danmakuText = computed(() => formatCount(props.video.danmakus))

/**
 * 真实封面帧：后端 FFmpeg 截帧后会给 coverUrl / posterUrl。
 * 没有真实封面（或图片加载失败）时，用渐变占位图兜底 —— 永远不会出现空白。
 */
const imgFailed = ref(false)
const coverImage = computed(() => {
  if (imgFailed.value) return ''
  const v = props.video
  return v?.coverUrl || v?.posterUrl || ''
})
function onImgError() {
  imgFailed.value = true
}

function open() {
  router.push({ name: 'video', params: { id: props.video.id } })
}

function openUp(e) {
  e.stopPropagation()
  router.push({ name: 'space', params: { id: props.video.upId } })
}

const rankClass = computed(() => {
  if (props.rank === 1) return 'gold'
  if (props.rank === 2) return 'silver'
  if (props.rank === 3) return 'bronze'
  return ''
})
</script>

<template>
  <!-- 首页网格卡片 -->
  <article v-if="layout === 'grid'" ref="cardRef" class="vcard" :style="tiltStyle" @click="open">
    <div class="cover" :style="cover">
      <!-- 真实封面帧：有就铺满，加载失败自动回落到下面的渐变占位层 -->
      <img
        v-if="coverImage"
        class="cover-img"
        :src="coverImage"
        :alt="video.title"
        loading="lazy"
        decoding="async"
        @error="onImgError"
      />
      <template v-else>
        <span class="cover-emoji">{{ video.coverEmoji }}</span>
        <span class="cover-bigtext">{{ video.coverText }}</span>
      </template>
      <span class="duration">{{ durationText }}</span>
      <span v-if="video.playable" class="live-badge"><AiIcon><VideoPlay /></AiIcon> 可播放</span>
      <span class="play-badge"><AiIcon :size="30"><VideoPlay /></AiIcon></span>
      <div class="cover-mask">
        <span><AiIcon><VideoPlay /></AiIcon> {{ viewText }}播放</span>
        <span><AiIcon><ChatDotRound /></AiIcon> {{ danmakuText }}弹幕</span>
      </div>
    </div>

    <h3 class="title clamp-2" :title="video.title">{{ video.title }}</h3>

    <div v-if="showUp" class="up" @click="openUp">
      <span class="up-face">{{ video.upFace }}</span>
      <span class="up-name">{{ video.upName }}</span>
    </div>
  </article>

  <!-- 右侧相关推荐：横向小卡 -->
  <article v-else-if="layout === 'list'" class="vrow" @click="open">
    <div class="row-cover" :style="cover">
      <img v-if="coverImage" class="cover-img" :src="coverImage" :alt="video.title" loading="lazy" @error="onImgError" />
      <span v-else class="row-emoji">{{ video.coverEmoji }}</span>
      <span class="duration">{{ durationText }}</span>
    </div>
    <div class="row-info">
      <h4 class="clamp-2" :title="video.title">{{ video.title }}</h4>
      <p class="row-meta">{{ viewText }}播放 · {{ danmakuText }}弹幕</p>
      <p class="row-up" @click="openUp">{{ video.upName }}</p>
    </div>
  </article>

  <!-- 排行榜行 -->
  <article v-else class="vrank" @click="open">
    <span class="rank-no" :class="rankClass">{{ rank }}</span>
    <div class="rank-cover" :style="cover">
      <img v-if="coverImage" class="cover-img" :src="coverImage" :alt="video.title" loading="lazy" @error="onImgError" />
      <span v-else class="row-emoji">{{ video.coverEmoji }}</span>
      <span class="duration">{{ durationText }}</span>
    </div>
    <div class="rank-info">
      <h4 class="clamp-2" :title="video.title">{{ video.title }}</h4>
      <p class="row-meta">
        <span @click.stop="openUp">{{ video.upName }}</span>
        <span class="sep">·</span>
        <span>{{ video.category }}</span>
        <span class="sep">·</span>
        <span>{{ video.pubAgo }}</span>
      </p>
      <p class="rank-stat">
        <span><AiIcon><VideoPlay /></AiIcon> {{ viewText }}</span>
        <span><AiIcon><ChatDotRound /></AiIcon> {{ danmakuText }}</span>
        <span><AiIcon><Pointer /></AiIcon> {{ formatCount(video.likes) }}</span>
      </p>
    </div>
  </article>
</template>

<style scoped>
/* ---------------- 网格卡片 ---------------- */
.vcard {
  cursor: pointer;
  /* 鼠标在卡片上移动时会轻微倾斜，见上面的 tiltStyle */
  transition: transform 0.18s ease-out;
  transform-style: preserve-3d;
  will-change: transform;
}

.cover {
  position: relative;
  width: 100%;
  aspect-ratio: 16 / 9;
  border-radius: 8px;
  overflow: hidden;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: box-shadow 0.25s ease;
  /* 兜底背景：即使图片没加载出来也不会是空白 */
  background-color: var(--surface-sunken);
  background-size: cover;
  background-position: center;
}

/* 兼容老浏览器（不支持 aspect-ratio 时用 padding 撑出 16:9） */
.cover::before {
  content: '';
  display: block;
  padding-top: 56.25%;
}

.cover-img {
  position: absolute;
  inset: 0;
  width: 100%;
  height: 100%;
  object-fit: cover;
  display: block;
}

.live-badge {
  position: absolute;
  left: 6px;
  bottom: 6px;
  padding: 1px 6px;
  border-radius: 4px;
  background: rgba(110, 86, 248, 0.9);
  color: #fff;
  font-size: 11px;
  line-height: 16px;
}

.vcard:hover .cover {
  box-shadow: 0 10px 24px rgba(0, 0, 0, 0.18);
}

.cover-emoji {
  font-size: 52px;
  filter: drop-shadow(0 4px 10px rgba(0, 0, 0, 0.18));
  opacity: 0.95;
  transform: translateY(-6px);
  transition: transform 0.35s cubic-bezier(0.34, 1.4, 0.64, 1);
}

.vcard:hover .cover-emoji {
  transform: translateY(-6px) scale(1.18) rotate(-4deg);
}

/* 鼠标移上去出现的播放按钮 */
.play-badge {
  position: absolute;
  left: 50%;
  top: 50%;
  width: 46px;
  height: 46px;
  margin: -23px 0 0 -23px;
  border-radius: 50%;
  background: rgba(0, 0, 0, 0.45);
  border: 2px solid rgba(255, 255, 255, 0.9);
  color: #fff;
  font-size: 15px;
  display: flex;
  align-items: center;
  justify-content: center;
  opacity: 0;
  transform: scale(0.6);
  transition: opacity 0.25s, transform 0.3s cubic-bezier(0.34, 1.5, 0.64, 1);
}

.vcard:hover .play-badge {
  opacity: 1;
  transform: scale(1);
}

.cover-bigtext {
  position: absolute;
  left: 12px;
  bottom: 12px;
  right: 60px;
  color: #fff;
  font-size: 20px;
  font-weight: 800;
  letter-spacing: 1px;
  text-shadow: 0 2px 8px rgba(0, 0, 0, 0.35);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.duration {
  position: absolute;
  right: 6px;
  bottom: 6px;
  padding: 1px 5px;
  border-radius: 4px;
  background: rgba(0, 0, 0, 0.55);
  color: #fff;
  font-size: 12px;
  line-height: 16px;
}

.cover-mask {
  position: absolute;
  inset: 0;
  background: linear-gradient(180deg, rgba(0, 0, 0, 0.05), rgba(0, 0, 0, 0.6));
  color: #fff;
  font-size: 12px;
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  padding: 8px;
  opacity: 0;
  transition: opacity 0.22s;
}

.vcard:hover .cover-mask {
  opacity: 1;
}

.title {
  margin: 10px 0 6px;
  font-size: 15px;
  line-height: 22px;
  font-weight: 500;
  color: var(--text-1);
  min-height: 44px;
  transition: color 0.2s;
}

.vcard:hover .title {
  color: var(--bili-blue);
}

.up {
  display: flex;
  align-items: center;
  gap: 6px;
  color: var(--text-3);
  font-size: 13px;
  width: fit-content;
}

.up:hover {
  color: var(--bili-pink);
}

.up-face {
  font-size: 14px;
}

/* ---------------- 横向小卡 ---------------- */
.vrow {
  display: flex;
  gap: 10px;
  cursor: pointer;
  padding: 6px 0;
}

.row-cover {
  position: relative;
  width: 140px;
  flex-shrink: 0;
  aspect-ratio: 16 / 9;
  border-radius: 6px;
  display: flex;
  align-items: center;
  justify-content: center;
  overflow: hidden;
  background-color: var(--surface-sunken);
  background-size: cover;
}

.row-emoji {
  font-size: 30px;
  filter: drop-shadow(0 2px 6px rgba(0, 0, 0, 0.2));
}

.row-info {
  min-width: 0;
  flex: 1;
}

.row-info h4 {
  margin: 0 0 6px;
  font-size: 14px;
  font-weight: 500;
  line-height: 20px;
  transition: color 0.2s;
}

.vrow:hover h4 {
  color: var(--bili-blue);
}

.row-meta,
.row-up {
  margin: 0;
  font-size: 12px;
  color: var(--text-3);
}

.row-up:hover {
  color: var(--bili-pink);
}

/* ---------------- 排行榜行 ---------------- */
.vrank {
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 12px 16px;
  border-radius: 8px;
  cursor: pointer;
  transition: background 0.2s;
}

.vrank:hover {
  background: var(--surface-0);
}

.rank-no {
  width: 34px;
  flex-shrink: 0;
  text-align: center;
  font-size: 20px;
  font-weight: 700;
  color: var(--text-3);
  font-style: italic;
}

.rank-no.gold {
  color: var(--gold-500);
}

.rank-no.silver {
  color: var(--ink-4);
}

.rank-no.bronze {
  color: var(--accent-500);
}

.rank-cover {
  position: relative;
  width: 160px;
  flex-shrink: 0;
  aspect-ratio: 16 / 9;
  border-radius: 6px;
  display: flex;
  align-items: center;
  justify-content: center;
  overflow: hidden;
}

.rank-info {
  flex: 1;
  min-width: 0;
}

.rank-info h4 {
  margin: 0 0 6px;
  font-size: 15px;
  font-weight: 500;
  line-height: 22px;
}

.vrank:hover h4 {
  color: var(--bili-blue);
}

.rank-stat {
  display: flex;
  gap: 16px;
  margin: 6px 0 0;
  font-size: 12px;
  color: var(--text-3);
}

.sep {
  margin: 0 6px;
  color: var(--line);
}
</style>
