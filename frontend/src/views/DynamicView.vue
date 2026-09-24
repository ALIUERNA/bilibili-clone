<script setup>
import { onMounted, ref } from 'vue'
import { api } from '../api'
import { formatCount } from '../utils/format'
import { useUserStore } from '../stores/user'

const userStore = useUserStore()
const loading = ref(true)
const list = ref([])
const page = ref(1)
const hasMore = ref(false)
const loadingMore = ref(false)

async function load(reset = true) {
  if (reset) {
    page.value = 1
    loading.value = true
  } else {
    loadingMore.value = true
  }
  try {
    const res = await api.dynamics({ page: page.value, size: 10 })
    list.value = reset ? res.items : [...list.value, ...res.items]
    hasMore.value = res.hasMore
  } finally {
    loading.value = false
    loadingMore.value = false
  }
}

function like(d) {
  if (!userStore.requireLogin()) return
  d.liked = !d.liked
  d.likes += d.liked ? 1 : -1
}

function more() {
  page.value += 1
  load(false)
}

onMounted(() => load(true))
</script>

<template>
  <div class="dyn-page container fade-up">
    <div class="layout">
      <main class="feed">
        <h1 class="page-title">动态</h1>

        <div v-if="loading">
          <div v-for="i in 4" :key="i" class="skeleton sk-card"></div>
        </div>

        <article v-for="d in list" :key="d.id" class="dyn-card">
          <div class="head">
            <span class="face" @click="$router.push({ name: 'space', params: { id: d.upId } })">{{ d.upFace }}</span>
            <div class="who">
              <div class="name">{{ d.upName }}</div>
              <div class="time">{{ d.time }}</div>
            </div>
            <button class="follow" @click="userStore.requireLogin() && userStore.showToast('已关注 ' + d.upName)">
              + 关注
            </button>
          </div>

          <p class="content">{{ d.content }}</p>

          <div class="pic" :style="{ backgroundImage: `linear-gradient(135deg, ${d.color1}, ${d.color2})` }">
            <span>{{ d.emoji }}</span>
          </div>

          <div
            v-if="d.videoId"
            class="video-box"
            @click="$router.push({ name: 'video', params: { id: d.videoId } })"
          >
            <div class="vt"><AiIcon><VideoCamera /></AiIcon> 相关投稿</div>
            <div class="vtitle clamp-2">{{ d.videoTitle }}</div>
          </div>

          <div class="actions">
            <button :class="{ on: d.liked }" @click="like(d)">
              <AiIcon><Pointer /></AiIcon> {{ formatCount(d.likes) }}
            </button>
            <button @click="userStore.showToast('评论功能在视频页体验哦')">
              <AiIcon><Comment /></AiIcon> {{ formatCount(d.comments) }}
            </button>
            <button @click="userStore.showToast('转发功能未开放')">
              <AiIcon><Share /></AiIcon> 转发
            </button>
          </div>
        </article>

        <div v-if="hasMore" class="more">
          <button class="btn btn-ghost btn-round" :disabled="loadingMore" @click="more">
            {{ loadingMore ? '加载中...' : '加载更多动态' }}
          </button>
        </div>
      </main>

      <aside class="side">
        <div class="side-card">
          <h4><AiIcon><Histogram /></AiIcon> 热门话题</h4>
          <ul>
            <li v-for="(t, i) in ['#新番速览', '#装机避坑', '#深夜食堂', '#健身30天', '#镜头语言']" :key="t">
              <span class="no">{{ i + 1 }}</span>{{ t }}
            </li>
          </ul>
        </div>
        <div class="side-card">
          <h4><AiIcon><Star /></AiIcon> 推荐关注</h4>
          <div v-for="u in ['影像研究所', '硬核科普局', '熊猫厨房']" :key="u" class="reco">
            <span class="reco-face"><AiIcon><UserFilled /></AiIcon></span>
            <span class="reco-name">{{ u }}</span>
            <button class="btn btn-primary btn-round small" @click="userStore.requireLogin() && userStore.showToast('已关注 ' + u)">关注</button>
          </div>
        </div>
      </aside>
    </div>
  </div>
</template>

<style scoped>
.dyn-page {
  padding-top: 20px;
}

.layout {
  display: grid;
  grid-template-columns: 1fr 300px;
  gap: 20px;
  align-items: start;
}

.page-title {
  margin: 0 0 16px;
  font-size: 22px;
}

.dyn-card {
  background: #fff;
  border-radius: 10px;
  padding: 16px 18px;
  margin-bottom: 14px;
}

.head {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 12px;
}

.face {
  width: 44px;
  height: 44px;
  border-radius: 50%;
  background: var(--surface-sunken);
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 22px;
  cursor: pointer;
}

.who {
  flex: 1;
}

.name {
  font-size: 14px;
  font-weight: 600;
}

.time {
  font-size: 12px;
  color: var(--text-3);
}

.follow {
  height: 28px;
  padding: 0 14px;
  border-radius: 6px;
  background: var(--bili-pink);
  color: #fff;
  font-size: 12px;
}

.content {
  margin: 0 0 12px;
  font-size: 15px;
  line-height: 24px;
}

.pic {
  height: 200px;
  border-radius: 8px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 60px;
  margin-bottom: 12px;
}

.video-box {
  background: var(--surface-0);
  border-radius: 8px;
  padding: 12px 14px;
  cursor: pointer;
}

.video-box:hover {
  background: var(--surface-sunken);
}

.vt {
  font-size: 12px;
  color: var(--text-3);
  margin-bottom: 6px;
}

.vtitle {
  font-size: 14px;
  line-height: 21px;
}

.actions {
  display: flex;
  gap: 26px;
  margin-top: 14px;
  padding-top: 12px;
  border-top: 1px solid var(--surface-sunken);
}

.actions button {
  font-size: 13px;
  color: var(--text-3);
}

.actions button:hover {
  color: var(--bili-blue);
}

.actions button.on {
  color: var(--bili-pink);
}

.side-card {
  background: #fff;
  border-radius: 10px;
  padding: 16px;
  margin-bottom: 16px;
}

.side-card h4 {
  margin: 0 0 12px;
  font-size: 15px;
}

.side-card li {
  padding: 6px 0;
  font-size: 14px;
  color: var(--text-2);
  cursor: pointer;
}

.side-card li:hover {
  color: var(--bili-pink);
}

.no {
  display: inline-block;
  width: 18px;
  color: var(--bili-pink);
  font-weight: 700;
}

.reco {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 0;
}

.reco-face {
  width: 34px;
  height: 34px;
  border-radius: 50%;
  background: var(--surface-sunken);
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 18px;
}

.reco-name {
  flex: 1;
  font-size: 13px;
}

.btn.small {
  height: 26px;
  padding: 0 12px;
  font-size: 12px;
}

.sk-card {
  height: 220px;
  margin-bottom: 14px;
  border-radius: 10px;
}

.more {
  display: flex;
  justify-content: center;
  padding: 16px 0;
}

@media (max-width: 900px) {
  .layout {
    grid-template-columns: 1fr;
  }
}
</style>
