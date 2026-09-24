<script setup>
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { api } from '../api'
import VideoCard from '../components/VideoCard.vue'
import { formatCount } from '../utils/format'
import { useUserStore } from '../stores/user'

const route = useRoute()
const userStore = useUserStore()

const loading = ref(true)
const notFound = ref(false)
const up = ref(null)
const videos = ref([])
const dynamics = ref([])
const totalViews = ref(0)
const tab = ref('home')
const sort = ref('new')

const sortedVideos = computed(() => {
  const list = [...videos.value]
  if (sort.value === 'play') list.sort((a, b) => b.views - a.views)
  else if (sort.value === 'like') list.sort((a, b) => b.likes - a.likes)
  else list.sort((a, b) => b.id - a.id)
  return list
})

async function load(id) {
  loading.value = true
  notFound.value = false
  try {
    const res = await api.space(id)
    up.value = res.up
    videos.value = res.videos || []
    dynamics.value = res.dynamics || []
    totalViews.value = res.totalViews || 0
  } catch (e) {
    up.value = null
    videos.value = []
    dynamics.value = []
    notFound.value = true
  } finally {
    loading.value = false
  }
}

onMounted(() => load(route.params.id))
watch(() => route.params.id, (id) => id && load(id))

function follow() {
  if (!userStore.requireLogin()) return
  const first = videos.value[0]
  if (first) {
    api.action(first.id, 'follow').then((res) => {
      up.value.followed = res.followed
      up.value.fans += res.followed ? 1 : -1
      userStore.showToast(res.followed ? '关注成功~' : '已取消关注')
    })
  } else {
    userStore.showToast('TA 还没有投稿')
  }
}
</script>

<template>
  <div class="space-page fade-up">
    <!-- 用户不存在 / 接口异常 -->
    <div v-if="notFound" class="space-empty">
      <el-empty description="用户不存在或已注销">
        <button class="btn btn-primary btn-round" @click="$router.push('/')">回首页看看</button>
      </el-empty>
    </div>

    <template v-else>
    <!-- 顶部横幅 -->
    <div class="banner" :style="{ backgroundImage: `linear-gradient(120deg, ${up?.face ? 'var(--brand-100)' : '#eee'}, var(--cyan-50))` }">
      <div class="banner-inner container">
        <div v-if="loading" class="skeleton sk-avatar"></div>
        <div v-else class="avatar">
          <span v-if="up?.face">{{ up.face }}</span>
          <AiIcon v-else :size="40"><UserFilled /></AiIcon>
        </div>

        <div class="info">
          <h1>{{ up?.name || '加载中...' }}</h1>
          <p class="sign">{{ up?.sign || '' }}</p>
          <div class="stats">
            <span><b>{{ formatCount(up?.fans || 0) }}</b> 粉丝</span>
            <span><b>{{ formatCount(up?.following || 128) }}</b> 关注</span>
            <span><b>{{ formatCount(up?.likes || 0) }}</b> 获赞</span>
            <span><b>{{ formatCount(totalViews) }}</b> 播放</span>
          </div>
        </div>

        <div class="actions">
          <button class="btn btn-primary btn-round" @click="follow">
            {{ up?.followed ? '已关注' : '+ 关注' }}
          </button>
          <button class="btn btn-ghost btn-round" @click="userStore.showToast('私信功能未开放')">
            <AiIcon><Message /></AiIcon> 发消息
          </button>
        </div>
      </div>
    </div>

    <div class="container body">
      <!-- 左侧 TAB -->
      <aside class="tabs">
        <button :class="{ on: tab === 'home' }" @click="tab = 'home'"><AiIcon><HomeFilled /></AiIcon> 主页</button>
        <button :class="{ on: tab === 'dynamic' }" @click="tab = 'dynamic'"><AiIcon><Promotion /></AiIcon> 动态</button>
        <button :class="{ on: tab === 'video' }" @click="tab = 'video'"><AiIcon><VideoCamera /></AiIcon> 投稿</button>
        <button class="disabled" @click="userStore.showToast('合集功能未开放')"><AiIcon><Collection /></AiIcon> 合集</button>
        <div class="tab-divider"></div>
        <button class="disabled" @click="userStore.showToast('收藏夹暂不公开')"><AiIcon><Star /></AiIcon> 收藏夹</button>
      </aside>

      <!-- 右侧内容 -->
      <main class="content">
        <!-- 主页 -->
        <template v-if="tab === 'home'">
          <section class="block">
            <h3 class="block-title">代表作</h3>
            <div class="grid">
              <VideoCard v-for="v in sortedVideos.slice(0, 8)" :key="v.id" :video="v" />
            </div>
          </section>

          <section class="block">
            <h3 class="block-title">最近动态</h3>
            <div v-if="!dynamics.length" class="empty">TA 还没有发布动态</div>
            <div v-for="d in dynamics.slice(0, 3)" :key="d.id" class="dyn">
              <div class="dyn-head">
                <span class="dyn-face">{{ d.upFace }}</span>
                <span class="dyn-name">{{ d.upName }}</span>
                <span class="dyn-time">{{ d.time }}</span>
              </div>
              <p class="dyn-content">{{ d.content }}</p>
            </div>
          </section>
        </template>

        <!-- 动态 -->
        <template v-else-if="tab === 'dynamic'">
          <div v-if="!dynamics.length" class="empty">TA 还没有发布动态</div>
          <article v-for="d in dynamics" :key="d.id" class="dyn-card">
            <div class="dyn-head">
              <span class="dyn-face">{{ d.upFace }}</span>
              <div>
                <div class="dyn-name">{{ d.upName }}</div>
                <div class="dyn-time">{{ d.time }}</div>
              </div>
            </div>
            <p class="dyn-content">{{ d.content }}</p>
            <div v-if="d.videoId" class="dyn-video" @click="$router.push({ name: 'video', params: { id: d.videoId } })">
              <div class="dyn-thumb" :style="{ backgroundImage: `linear-gradient(135deg, ${d.color1}, ${d.color2})` }">
                <span>{{ d.emoji }}</span>
              </div>
              <div class="dyn-vtitle clamp-2">{{ d.videoTitle }}</div>
            </div>
            <div class="dyn-actions">
              <span><AiIcon><Pointer /></AiIcon> {{ formatCount(d.likes) }}</span>
              <span><AiIcon><Comment /></AiIcon> {{ formatCount(d.comments) }}</span>
              <span><AiIcon><Share /></AiIcon> 分享</span>
            </div>
          </article>
        </template>

        <!-- 投稿 -->
        <template v-else>
          <div class="sort-bar">
            <button :class="{ on: sort === 'new' }" @click="sort = 'new'">最新发布</button>
            <button :class="{ on: sort === 'play' }" @click="sort = 'play'">最多播放</button>
            <button :class="{ on: sort === 'like' }" @click="sort = 'like'">最多点赞</button>
          </div>
          <div class="grid">
            <VideoCard v-for="v in sortedVideos" :key="v.id" :video="v" :show-up="false" />
          </div>
        </template>
      </main>
    </div>
    </template>
  </div>
</template>

<style scoped>
.banner {
  padding: 30px 0;
  background-size: cover;
}

.space-page {
  min-height: 60vh;
}

.space-empty {
  padding: 80px 16px;
}

.banner-inner {
  display: flex;
  align-items: center;
  gap: 20px;
}

.avatar,
.sk-avatar {
  width: 96px;
  height: 96px;
  border-radius: 50%;
  background: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 44px;
  box-shadow: 0 6px 18px rgba(0, 0, 0, 0.12);
  flex-shrink: 0;
}

.info {
  flex: 1;
  min-width: 0;
}

.info h1 {
  margin: 0 0 6px;
  font-size: 24px;
}

.sign {
  margin: 0 0 12px;
  color: var(--text-2);
  font-size: 14px;
}

.stats {
  display: flex;
  gap: 22px;
  font-size: 13px;
  color: var(--text-2);
}

.stats b {
  color: var(--text-1);
  font-size: 15px;
  margin-right: 2px;
}

.actions {
  display: flex;
  gap: 10px;
  flex-shrink: 0;
}

.body {
  display: grid;
  grid-template-columns: 180px 1fr;
  gap: 24px;
  padding-top: 20px;
}

.tabs {
  background: #fff;
  border-radius: 10px;
  padding: 10px;
  height: fit-content;
  position: sticky;
  top: 80px;
}

.tabs button {
  display: block;
  width: 100%;
  text-align: left;
  padding: 10px 14px;
  border-radius: 8px;
  font-size: 14px;
  color: var(--text-2);
  margin-bottom: 4px;
}

.tabs button:hover {
  background: var(--surface-0);
  color: var(--bili-pink);
}

.tabs button.on {
  background: var(--brand-50);
  color: var(--bili-pink);
  font-weight: 600;
}

.tabs button.disabled {
  color: var(--text-3);
}

.tab-divider {
  height: 1px;
  background: var(--line);
  margin: 8px 6px;
}

.content {
  min-width: 0;
}

.block {
  margin-bottom: 26px;
}

.block-title {
  margin: 0 0 14px;
  font-size: 18px;
}

.grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(220px, 1fr));
  gap: 20px 16px;
}

.sort-bar {
  display: flex;
  gap: 8px;
  margin-bottom: 16px;
}

.sort-bar button {
  padding: 5px 14px;
  border-radius: 999px;
  background: #fff;
  color: var(--text-2);
  font-size: 13px;
}

.sort-bar button.on {
  background: var(--cyan-50);
  color: var(--bili-blue);
  font-weight: 600;
}

.dyn,
.dyn-card {
  background: #fff;
  border-radius: 10px;
  padding: 16px;
  margin-bottom: 14px;
}

.dyn-head {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 10px;
}

.dyn-face {
  width: 40px;
  height: 40px;
  border-radius: 50%;
  background: var(--surface-sunken);
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 20px;
}

.dyn-name {
  font-weight: 600;
  font-size: 14px;
}

.dyn-time {
  font-size: 12px;
  color: var(--text-3);
}

.dyn-content {
  margin: 0 0 12px;
  font-size: 14px;
  line-height: 23px;
}

.dyn-video {
  display: flex;
  gap: 12px;
  padding: 8px;
  border-radius: 8px;
  background: var(--surface-0);
  cursor: pointer;
}

.dyn-video:hover {
  background: var(--surface-sunken);
}

.dyn-thumb {
  width: 110px;
  aspect-ratio: 16 / 9;
  border-radius: 6px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 26px;
  flex-shrink: 0;
}

.dyn-vtitle {
  font-size: 13px;
  line-height: 20px;
}

.dyn-actions {
  display: flex;
  gap: 26px;
  margin-top: 12px;
  font-size: 13px;
  color: var(--text-3);
}

.dyn-actions span:hover {
  color: var(--bili-blue);
  cursor: pointer;
}

.empty {
  padding: 40px 0;
  text-align: center;
  color: var(--text-3);
  background: #fff;
  border-radius: 10px;
}

@media (max-width: 900px) {
  .body {
    grid-template-columns: 1fr;
  }

  .tabs {
    display: flex;
    overflow-x: auto;
    position: static;
  }

  .tab-divider {
    display: none;
  }

  .banner-inner {
    flex-wrap: wrap;
  }
}
</style>
