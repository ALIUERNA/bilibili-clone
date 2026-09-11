<script setup>
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { useEventListener, useFullscreen } from '@vueuse/core'
import DanmakuLayer from './DanmakuLayer.vue'
import { coverStyle, formatDuration } from '../utils/format'

/**
 * 播放器。
 *
 * 说明：演示项目里没有真实的视频文件，播放进度是用 requestAnimationFrame 模拟出来的，
 * 「画面」用随播放进度变化的渐变动画代替，用来展示弹幕、进度条、倍速等交互。
 * 想接真实视频的话，把 .scene 那一块换成 <video> 标签，把 time 绑定到 video.currentTime 即可。
 */
const props = defineProps({
  video: { type: Object, required: true },
  danmakuList: { type: Array, default: () => [] }
})

const emit = defineEmits(['send-danmaku', 'progress'])

const wrapRef = ref(null)
const barRef = ref(null)

// 全屏交给 VueUse 的 useFullscreen（浏览器兼容、进入/退出状态都由它维护）
const { isFullscreen, toggle: toggleFullscreen } = useFullscreen(wrapRef)

const time = ref(0)
const playing = ref(false)
const rate = ref(1)
const volume = ref(0.6)
const muted = ref(false)
const danmakuOn = ref(true)
const quality = ref('1080P 高码率')
const showQuality = ref(false)
const showSetting = ref(false)
const danmakuText = ref('')
const danmakuColor = ref('#FFFFFF')
const danmakuMode = ref(1)
const controlsVisible = ref(true)
const hoverTime = ref(null)
const hoverX = ref(0)

// 弹幕设置
const dm = ref({
  opacity: 0.95,
  fontSize: 25,
  travel: 9,
  lanes: 9,
  area: 'all'
})

const duration = computed(() => props.video?.duration || 300)
const progress = computed(() => (duration.value ? Math.min(100, (time.value / duration.value) * 100) : 0))
const timeText = computed(() => `${formatDuration(time.value)} / ${formatDuration(duration.value)}`)
const colors = ['#FFFFFF', '#FB7299', '#00AEEC', '#FFF24B', '#FF9900', '#43E97B']

/** 画面配色随进度轻微变化，制造「视频在播放」的感觉 */
const sceneStyle = computed(() => ({
  ...coverStyle(props.video),
  filter: `hue-rotate(${(time.value % 40) * 3}deg) saturate(1.05)`
}))

// ---------------- 播放时钟 ----------------
let rafId = null
let lastTs = 0

function loop(ts) {
  if (!lastTs) lastTs = ts
  const dt = Math.min(0.1, (ts - lastTs) / 1000)
  lastTs = ts
  if (playing.value) {
    time.value = Math.min(duration.value, time.value + dt * rate.value)
    if (time.value >= duration.value) {
      playing.value = false
      time.value = 0
    }
    emit('progress', time.value)
  }
  rafId = requestAnimationFrame(loop)
}

onMounted(() => {
  rafId = requestAnimationFrame(loop)
})

onUnmounted(() => cancelAnimationFrame(rafId))

// 键盘快捷键：空格暂停、← → 快进快退、F 全屏
// useEventListener 会在组件卸载时自动解绑，不用自己写 add/removeEventListener
useEventListener(window, 'keydown', onKeydown)

function toggle() {
  if (!playing.value && time.value >= duration.value) time.value = 0
  playing.value = !playing.value
}

function seekTo(e) {
  const rect = barRef.value.getBoundingClientRect()
  const ratio = Math.min(1, Math.max(0, (e.clientX - rect.left) / rect.width))
  time.value = ratio * duration.value
}

function startDrag(e) {
  seekTo(e)
  const move = (ev) => seekTo(ev)
  const up = () => {
    window.removeEventListener('pointermove', move)
    window.removeEventListener('pointerup', up)
  }
  window.addEventListener('pointermove', move)
  window.addEventListener('pointerup', up)
}

function onBarHover(e) {
  const rect = barRef.value.getBoundingClientRect()
  hoverX.value = e.clientX - rect.left
  hoverTime.value = ((e.clientX - rect.left) / rect.width) * duration.value
}

function onKeydown(e) {
  if (['INPUT', 'TEXTAREA'].includes(e.target?.tagName)) return
  if (e.code === 'Space') {
    e.preventDefault()
    toggle()
  } else if (e.code === 'ArrowRight') {
    time.value = Math.min(duration.value, time.value + 5)
  } else if (e.code === 'ArrowLeft') {
    time.value = Math.max(0, time.value - 5)
  } else if (e.code === 'KeyF') {
    toggleFullscreen()
  }
}

function send() {
  const text = danmakuText.value.trim()
  if (!text) return
  emit('send-danmaku', {
    text,
    color: danmakuColor.value,
    mode: danmakuMode.value,
    time: Math.round(time.value * 10) / 10
  })
  danmakuText.value = ''
}

// 鼠标静止 3 秒后自动隐藏控制栏
let hideTimer = null

function wake() {
  controlsVisible.value = true
  clearTimeout(hideTimer)
  hideTimer = setTimeout(() => {
    if (playing.value) controlsVisible.value = false
  }, 3000)
}

const visibleDanmaku = computed(() => {
  if (dm.value.area === 'top') return props.danmakuList.filter((d) => d.mode === 5)
  if (dm.value.area === 'bottom') return props.danmakuList.filter((d) => d.mode === 4)
  return props.danmakuList
})
</script>

<template>
  <div
    ref="wrapRef"
    class="player"
    @mousemove="wake"
    @mouseleave="controlsVisible = true"
    @click.self="toggle"
  >
    <!-- 模拟的视频画面 -->
    <div class="scene" :style="sceneStyle" @click="toggle">
      <div class="blob b1"></div>
      <div class="blob b2"></div>
      <div class="scene-center">
        <div class="scene-title">{{ video.coverText }}</div>
        <div class="scene-sub">{{ video.category }} · {{ video.upName }}</div>
      </div>
      <div class="scene-hint">演示播放器 · 画面由动画模拟</div>
    </div>

    <!-- 弹幕层 -->
    <DanmakuLayer
      :list="visibleDanmaku"
      :time="time"
      :visible="danmakuOn"
      :opacity="dm.opacity"
      :font-size="dm.fontSize"
      :travel="dm.travel"
      :lanes="dm.lanes"
    />

    <!-- 中间的大播放按钮 -->
    <transition name="fade">
      <button v-if="!playing" class="big-play" @click.stop="toggle">▶</button>
    </transition>

    <!-- 顶部标题栏 -->
    <div class="top-mask" :class="{ hide: !controlsVisible }">
      <span class="dm-count">💬 {{ video.danmakus }}</span>
      <span class="p-title ellipsis">{{ video.title }}</span>
    </div>

    <!-- 底部控制栏 -->
    <div class="controls" :class="{ hide: !controlsVisible }" @click.stop>
      <!-- 进度条 -->
      <div class="progress-wrap" ref="barRef" @pointerdown="startDrag" @mousemove="onBarHover" @mouseleave="hoverTime = null">
        <div class="progress-bg">
          <div class="progress-loaded" :style="{ width: Math.min(100, progress + 12) + '%' }"></div>
          <div class="progress-played" :style="{ width: progress + '%' }"></div>
          <div class="progress-dot" :style="{ left: progress + '%' }"></div>
        </div>
        <div v-if="hoverTime !== null" class="hover-tip" :style="{ left: hoverX + 'px' }">
          {{ formatDuration(hoverTime) }}
        </div>
      </div>

      <div class="bar-row">
        <button class="ctrl" :title="playing ? '暂停' : '播放'" @click="toggle">
          {{ playing ? '⏸' : '▶' }}
        </button>
        <button class="ctrl" title="下一个" @click="emit('send-danmaku', null)">⏭</button>

        <span class="time-text">{{ timeText }}</span>
        <span class="spacer"></span>

        <!-- 发弹幕 -->
        <div class="dm-input">
          <span class="dm-dot" :style="{ background: danmakuColor }" @click="danmakuOn = !danmakuOn"></span>
          <input
            v-model="danmakuText"
            type="text"
            maxlength="50"
            placeholder="发个弹幕见证当下"
            @keyup.enter="send"
          />
          <button class="dm-send" @click="send">发送</button>
          <div class="dm-colors">
            <button
              v-for="c in colors"
              :key="c"
              class="color-dot"
              :class="{ on: danmakuColor === c }"
              :style="{ background: c }"
              @click="danmakuColor = c"
            ></button>
          </div>
        </div>

        <button class="ctrl text" :class="{ off: !danmakuOn }" title="弹幕开关" @click="danmakuOn = !danmakuOn">
          {{ danmakuOn ? '弹' : '关' }}
        </button>

        <div class="menu-wrap">
          <button class="ctrl text" title="弹幕设置" @click="showSetting = !showSetting">⚙</button>
          <transition name="fade">
            <div v-if="showSetting" class="setting-panel">
              <div class="set-title">弹幕设置</div>
              <label class="set-row">
                <span>不透明度</span>
                <input v-model.number="dm.opacity" type="range" min="0.2" max="1" step="0.05" />
              </label>
              <label class="set-row">
                <span>字号</span>
                <input v-model.number="dm.fontSize" type="range" min="16" max="40" step="1" />
              </label>
              <label class="set-row">
                <span>速度</span>
                <input v-model.number="dm.travel" type="range" min="5" max="14" step="1" />
              </label>
              <label class="set-row">
                <span>显示区域</span>
                <select v-model="dm.area">
                  <option value="all">全部</option>
                  <option value="top">仅顶部</option>
                  <option value="bottom">仅底部</option>
                </select>
              </label>
              <label class="set-row">
                <span>轨道数</span>
                <input v-model.number="dm.lanes" type="range" min="4" max="14" step="1" />
              </label>
            </div>
          </transition>
        </div>

        <div class="menu-wrap">
          <button class="ctrl text" @click="showQuality = !showQuality">{{ quality }}</button>
          <transition name="fade">
            <ul v-if="showQuality" class="quality-panel">
              <li
                v-for="q in ['1080P 高码率', '1080P', '720P', '480P']"
                :key="q"
                :class="{ on: quality === q }"
                @click="quality = q; showQuality = false"
              >
                {{ q }}
              </li>
            </ul>
          </transition>
        </div>

        <div class="menu-wrap">
          <button class="ctrl text" @click="rate = rate === 1 ? 1.5 : rate === 1.5 ? 2 : rate === 2 ? 0.5 : 1">
            {{ rate }}x
          </button>
        </div>

        <div class="volume">
          <button class="ctrl" @click="muted = !muted">{{ muted || volume === 0 ? '🔇' : '🔊' }}</button>
          <input v-model.number="volume" type="range" min="0" max="1" step="0.05" @input="muted = false" />
        </div>

        <button class="ctrl" :title="isFullscreen ? '退出全屏 (F)' : '全屏 (F)'" @click="toggleFullscreen">
          {{ isFullscreen ? '⤡' : '⛶' }}
        </button>
      </div>
    </div>

    <!-- 弹幕输入（全屏时的输入条位置提示） -->
    <transition name="fade">
      <div v-if="!controlsVisible" class="tip-floating" @click="wake">
        点击任意位置显示控制栏 · 空格暂停 · ←/→ 快退快进 · F 全屏
      </div>
    </transition>
  </div>
</template>

<style scoped>
.player {
  position: relative;
  width: 100%;
  aspect-ratio: 16 / 9;
  background: #000;
  border-radius: 8px;
  overflow: hidden;
  user-select: none;
}

/* ---------- 模拟画面 ---------- */
.scene {
  position: absolute;
  inset: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: filter 0.3s linear;
  cursor: pointer;
}

.blob {
  position: absolute;
  border-radius: 50%;
  filter: blur(30px);
  opacity: 0.55;
  mix-blend-mode: screen;
}

.b1 {
  width: 45%;
  aspect-ratio: 1;
  background: rgba(255, 255, 255, 0.75);
  left: -6%;
  top: -12%;
  animation: float1 14s ease-in-out infinite;
}

.b2 {
  width: 38%;
  aspect-ratio: 1;
  background: rgba(255, 255, 255, 0.5);
  right: -4%;
  bottom: -14%;
  animation: float2 18s ease-in-out infinite;
}

@keyframes float1 {
  0%,
  100% {
    transform: translate(0, 0) scale(1);
  }
  50% {
    transform: translate(60px, 40px) scale(1.15);
  }
}

@keyframes float2 {
  0%,
  100% {
    transform: translate(0, 0) scale(1.05);
  }
  50% {
    transform: translate(-50px, -30px) scale(0.9);
  }
}

.scene-center {
  position: relative;
  text-align: center;
  color: #fff;
  text-shadow: 0 4px 18px rgba(0, 0, 0, 0.35);
}

.scene-title {
  font-size: 54px;
  font-weight: 900;
  letter-spacing: 4px;
}

.scene-sub {
  margin-top: 10px;
  font-size: 16px;
  opacity: 0.92;
  letter-spacing: 2px;
}

.scene-hint {
  position: absolute;
  left: 14px;
  top: 14px;
  font-size: 12px;
  color: rgba(255, 255, 255, 0.9);
  background: rgba(0, 0, 0, 0.28);
  padding: 3px 8px;
  border-radius: 4px;
}

.big-play {
  position: absolute;
  left: 50%;
  top: 50%;
  transform: translate(-50%, -50%);
  width: 76px;
  height: 76px;
  border-radius: 50%;
  background: rgba(0, 0, 0, 0.45);
  border: 2px solid rgba(255, 255, 255, 0.85);
  color: #fff;
  font-size: 26px;
  padding-left: 6px;
  z-index: 8;
  transition: background 0.2s, transform 0.2s;
}

.big-play:hover {
  background: var(--bili-pink);
  transform: translate(-50%, -50%) scale(1.06);
}

/* ---------- 顶部标题 ---------- */
.top-mask {
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  padding: 12px 16px;
  display: flex;
  align-items: center;
  gap: 12px;
  background: linear-gradient(180deg, rgba(0, 0, 0, 0.55), transparent);
  color: #fff;
  font-size: 14px;
  z-index: 6;
  transition: opacity 0.25s, transform 0.25s;
}

.top-mask.hide {
  opacity: 0;
  transform: translateY(-10px);
}

.dm-count {
  font-size: 12px;
  opacity: 0.85;
  flex-shrink: 0;
}

.p-title {
  font-weight: 500;
}

/* ---------- 控制栏 ---------- */
.controls {
  position: absolute;
  left: 0;
  right: 0;
  bottom: 0;
  padding: 0 12px 6px;
  background: linear-gradient(0deg, rgba(0, 0, 0, 0.72), transparent);
  z-index: 10;
  transition: opacity 0.25s, transform 0.25s;
}

.controls.hide {
  opacity: 0;
  transform: translateY(12px);
}

.progress-wrap {
  position: relative;
  height: 16px;
  display: flex;
  align-items: center;
  cursor: pointer;
}

.progress-bg {
  position: relative;
  width: 100%;
  height: 3px;
  border-radius: 3px;
  background: rgba(255, 255, 255, 0.28);
  transition: height 0.15s;
}

.progress-wrap:hover .progress-bg {
  height: 5px;
}

.progress-loaded {
  position: absolute;
  left: 0;
  top: 0;
  bottom: 0;
  background: rgba(255, 255, 255, 0.35);
  border-radius: 3px;
}

.progress-played {
  position: absolute;
  left: 0;
  top: 0;
  bottom: 0;
  background: var(--bili-pink);
  border-radius: 3px;
}

.progress-dot {
  position: absolute;
  top: 50%;
  width: 12px;
  height: 12px;
  margin-left: -6px;
  border-radius: 50%;
  background: #fff;
  transform: translateY(-50%) scale(0);
  transition: transform 0.15s;
}

.progress-wrap:hover .progress-dot {
  transform: translateY(-50%) scale(1);
}

.hover-tip {
  position: absolute;
  bottom: 20px;
  transform: translateX(-50%);
  background: rgba(0, 0, 0, 0.75);
  color: #fff;
  font-size: 12px;
  padding: 2px 6px;
  border-radius: 4px;
  pointer-events: none;
}

.bar-row {
  display: flex;
  align-items: center;
  gap: 8px;
  height: 40px;
  color: #fff;
}

.ctrl {
  color: #fff;
  font-size: 16px;
  padding: 4px 6px;
  border-radius: 4px;
  opacity: 0.92;
  transition: opacity 0.2s, background 0.2s;
}

.ctrl:hover {
  opacity: 1;
  background: rgba(255, 255, 255, 0.14);
}

.ctrl.text {
  font-size: 13px;
}

.ctrl.text.off {
  opacity: 0.55;
}

.time-text {
  font-size: 12px;
  opacity: 0.9;
  margin-left: 4px;
  white-space: nowrap;
}

.spacer {
  flex: 1;
}

/* 弹幕输入框 */
.dm-input {
  display: flex;
  align-items: center;
  gap: 6px;
  background: rgba(255, 255, 255, 0.14);
  border-radius: 6px;
  padding: 0 8px;
  height: 30px;
  min-width: 250px;
}

.dm-input input {
  flex: 1;
  border: none;
  background: transparent;
  color: #fff;
  font-size: 13px;
  width: 120px;
}

.dm-input input::placeholder {
  color: rgba(255, 255, 255, 0.65);
}

.dm-dot {
  width: 10px;
  height: 10px;
  border-radius: 50%;
  cursor: pointer;
  border: 1px solid rgba(255, 255, 255, 0.6);
}

.dm-send {
  color: #fff;
  font-size: 13px;
  background: var(--bili-pink);
  padding: 3px 10px;
  border-radius: 4px;
  white-space: nowrap;
}

.dm-send:hover {
  background: var(--bili-pink-hover);
}

.dm-colors {
  display: flex;
  gap: 4px;
  padding-left: 4px;
  border-left: 1px solid rgba(255, 255, 255, 0.25);
}

.color-dot {
  width: 12px;
  height: 12px;
  border-radius: 50%;
  border: 1px solid rgba(255, 255, 255, 0.5);
}

.color-dot.on {
  box-shadow: 0 0 0 2px rgba(255, 255, 255, 0.9);
}

.menu-wrap {
  position: relative;
}

.setting-panel,
.quality-panel {
  position: absolute;
  right: 0;
  bottom: 34px;
  background: rgba(24, 25, 28, 0.95);
  border-radius: 8px;
  padding: 10px 12px;
  width: 210px;
  color: #fff;
  font-size: 13px;
  box-shadow: 0 8px 24px rgba(0, 0, 0, 0.35);
}

.quality-panel {
  width: 130px;
  padding: 6px 0;
}

.quality-panel li {
  padding: 7px 14px;
  cursor: pointer;
}

.quality-panel li:hover {
  background: rgba(255, 255, 255, 0.12);
}

.quality-panel li.on {
  color: var(--bili-pink);
}

.set-title {
  font-weight: 600;
  margin-bottom: 8px;
}

.set-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  margin-bottom: 8px;
}

.set-row input[type='range'] {
  width: 110px;
  accent-color: var(--bili-pink);
}

.set-row select {
  background: #2b2c30;
  color: #fff;
  border: none;
  border-radius: 4px;
  padding: 3px 6px;
}

.volume {
  display: flex;
  align-items: center;
  gap: 2px;
}

.volume input[type='range'] {
  width: 0;
  opacity: 0;
  transition: width 0.2s, opacity 0.2s;
  accent-color: var(--bili-pink);
}

.volume:hover input[type='range'] {
  width: 72px;
  opacity: 1;
}

.tip-floating {
  position: absolute;
  left: 50%;
  bottom: 12px;
  transform: translateX(-50%);
  color: rgba(255, 255, 255, 0.75);
  font-size: 12px;
  background: rgba(0, 0, 0, 0.4);
  padding: 3px 10px;
  border-radius: 999px;
  z-index: 9;
  cursor: pointer;
}

.fade-enter-active,
.fade-leave-active {
  transition: opacity 0.2s;
}

.fade-enter-from,
.fade-leave-to {
  opacity: 0;
}
</style>
