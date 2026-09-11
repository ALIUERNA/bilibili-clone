<script setup>
import { ref } from 'vue'
import { useUserStore } from '../stores/user'
import UserAvatar from './UserAvatar.vue'

const props = defineProps({
  comment: { type: Object, required: true }
})

// 点赞数据由父组件持有，子组件只负责「通知」，不直接改 prop
const emit = defineEmits(['like'])

const userStore = useUserStore()
const showReplies = ref(false)
const pop = ref(false)

function like() {
  if (!userStore.requireLogin()) return
  emit('like', props.comment)
  pop.value = true
  setTimeout(() => (pop.value = false), 320)
}

function reply() {
  if (!userStore.requireLogin()) return
  userStore.showToast('演示项目：回复功能暂未开放~')
}

const fmt = (n) => (n >= 10000 ? (n / 10000).toFixed(1) + '万' : String(n))
</script>

<template>
  <div class="comment">
    <UserAvatar :face="comment.face" :face-url="comment.faceUrl" :size="40" hover-zoom />
    <div class="body">
      <div class="head">
        <span class="user">{{ comment.user }}</span>
        <span v-if="comment.upLiked" class="tag tag-pink up-liked">UP主觉得很赞</span>
        <span class="level">Lv{{ (comment.floor % 6) + 1 }}</span>
      </div>
      <p class="content">{{ comment.content }}</p>
      <div class="meta">
        <span>{{ comment.time }}</span>
        <span class="dot">·</span>
        <span>{{ comment.location || '未知' }}</span>
        <span class="actions">
          <button class="act" :class="{ on: comment.liked, pop }" @click="like">👍 {{ fmt(comment.likes) }}</button>
          <button class="act" @click="reply">💬 回复</button>
        </span>
      </div>

      <div v-if="comment.replies && comment.replies.length" class="replies">
        <div class="reply" v-for="r in comment.replies" :key="r.id">
          <span class="r-user">{{ r.user }}</span>
          <span class="r-text">：{{ r.content }}</span>
          <span class="r-meta">{{ r.time }}</span>
        </div>
        <button class="toggle" @click="showReplies = !showReplies">
          {{ showReplies ? '收起回复' : `展开 ${comment.replies.length} 条回复` }}
        </button>
      </div>
    </div>
  </div>
</template>

<style scoped>
.comment {
  display: flex;
  gap: 12px;
  padding: 16px 0;
  border-bottom: 1px solid #f1f2f3;
}

.avatar {
  width: 40px;
  height: 40px;
  border-radius: 50%;
  background: #f1f2f3;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 20px;
  flex-shrink: 0;
}

.act.pop {
  animation: actPop 0.32s cubic-bezier(0.34, 1.56, 0.64, 1);
}

@keyframes actPop {
  0% {
    transform: scale(1);
  }
  45% {
    transform: scale(1.35);
  }
  100% {
    transform: scale(1);
  }
}

.body {
  flex: 1;
  min-width: 0;
}

.head {
  display: flex;
  align-items: center;
  gap: 8px;
}

.user {
  font-size: 14px;
  font-weight: 600;
  color: #5c6b87;
}

.up-liked {
  font-size: 11px;
}

.level {
  font-size: 11px;
  color: #fff;
  background: linear-gradient(90deg, #ffb3d1, #fb7299);
  border-radius: 3px;
  padding: 0 4px;
}

.content {
  margin: 6px 0 6px;
  font-size: 15px;
  line-height: 24px;
  color: var(--text-1);
  word-break: break-word;
}

.meta {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 12px;
  color: var(--text-3);
}

.dot {
  color: var(--line);
}

.actions {
  margin-left: 12px;
  display: flex;
  gap: 14px;
}

.act {
  font-size: 12px;
  color: var(--text-3);
}

.act:hover {
  color: var(--bili-blue);
}

.act.on {
  color: var(--bili-pink);
}

.replies {
  margin-top: 10px;
  background: #f7f8fa;
  border-radius: 8px;
  padding: 10px 12px;
}

.reply {
  font-size: 13px;
  line-height: 22px;
  color: var(--text-2);
}

.r-user {
  color: #5c6b87;
  font-weight: 600;
}

.r-meta {
  margin-left: 8px;
  font-size: 12px;
  color: var(--text-3);
}

.toggle {
  margin-top: 6px;
  font-size: 12px;
  color: var(--bili-blue);
}
</style>
