<script setup>
/**
 * 通用头像组件。
 * 传了 faceUrl（用户自己上传的图片）就显示图片，否则显示 emoji 头像。
 */
defineProps({
  face: { type: String, default: '👤' },
  faceUrl: { type: String, default: '' },
  size: { type: Number, default: 40 },
  /** 头像右下角的小等级标签，传 0 表示不显示 */
  level: { type: Number, default: 0 },
  /** 鼠标悬停是否放大 */
  hoverZoom: { type: Boolean, default: false },
  ring: { type: Boolean, default: false }
})
</script>

<template>
  <span
    class="ua"
    :class="{ 'hover-zoom': hoverZoom, ring }"
    :style="{ width: size + 'px', height: size + 'px', fontSize: Math.round(size * 0.5) + 'px' }"
  >
    <img v-if="faceUrl" :src="faceUrl" alt="头像" />
    <span v-else class="emoji">{{ face }}</span>
    <span v-if="level > 0" class="lv" :style="{ fontSize: Math.max(9, Math.round(size * 0.22)) + 'px' }">
      Lv{{ level }}
    </span>
  </span>
</template>

<style scoped>
.ua {
  position: relative;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  border-radius: 50%;
  background: #f1f2f3;
  overflow: visible;
  flex-shrink: 0;
  transition: transform 0.25s cubic-bezier(0.34, 1.56, 0.64, 1), box-shadow 0.25s;
  user-select: none;
}

.ua img {
  width: 100%;
  height: 100%;
  border-radius: 50%;
  object-fit: cover;
  display: block;
}

.ua .emoji {
  line-height: 1;
}

.ua.hover-zoom:hover {
  transform: scale(1.14);
  box-shadow: 0 6px 18px rgba(251, 114, 153, 0.35);
  z-index: 5;
}

.ua.ring {
  box-shadow: 0 0 0 2px #fff, 0 0 0 4px rgba(251, 114, 153, 0.55);
}

.lv {
  position: absolute;
  right: -2px;
  bottom: -2px;
  padding: 0 4px;
  border-radius: 6px;
  background: linear-gradient(135deg, #ff9ab8, #fb7299);
  color: #fff;
  font-weight: 700;
  line-height: 14px;
  border: 1px solid #fff;
  white-space: nowrap;
}
</style>
