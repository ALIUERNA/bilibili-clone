<script setup>
import { computed, onMounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { api } from '../api'
import VideoCard from '../components/VideoCard.vue'
import { coverStyle } from '../utils/format'

const router = useRouter()

const loading = ref(true)
const banners = ref([])
const categories = ref([])
const hotSearch = ref([])
const onlineCount = ref(0)
const videos = ref([])
const activeCategory = ref('recommend')
const page = ref(1)
const hasMore = ref(false)
const loadingMore = ref(false)
const bannerIndex = ref(0)

const size = 24

const activeName = computed(
  () => categories.value.find((c) => c.key === activeCategory.value)?.name || '推荐'
)

async function loadHome() {
  const res = await api.home()
  banners.value = res.banners || []
  categories.value = res.categories || []
  hotSearch.value = res.hotSearch || []
  onlineCount.value = res.onlineCount || 0
}

async function loadVideos(reset = true) {
  if (reset) {
    page.value = 1
    videos.value = []
    loading.value = true
  } else {
    loadingMore.value = true
  }
  try {
    const res = await api.videos({ category: activeCategory.value, page: page.value, size })
    videos.value = reset ? res.items : [...videos.value, ...res.items]
    hasMore.value = res.hasMore
  } finally {
    loading.value = false
    loadingMore.value = false
  }
}

function switchCategory(key) {
  if (activeCategory.value === key) return
  activeCategory.value = key
  loadVideos(true)
  window.scrollTo({ top: 420, behavior: 'smooth' })
}

function loadMore() {
  page.value += 1
  loadVideos(false)
}

function goBanner(banner) {
  if (banner) router.push({ name: 'video', params: { id: banner.videoId } })
}

function doSearch(word) {
  router.push({ name: 'search', query: { keyword: word } })
}

onMounted(async () => {
  await loadHome()
  await loadVideos(true)
})

watch(activeCategory, () => {
  bannerIndex.value = 0
})
</script>

<template>
  <div class="home container-wide fade-up">
    <!-- 顶部分区导航 -->
    <nav class="channel-bar">
      <button
        v-for="c in categories.slice(0, 14)"
        :key="c.key"
        class="channel"
        :class="{ on: activeCategory === c.key }"
        @click="switchCategory(c.key)"
      >
        <span class="ch-emoji">{{ c.emoji }}</span>
        <span class="ch-name">{{ c.name }}</span>
      </button>
      <div class="online">🔥 {{ (onlineCount / 10000).toFixed(1) }}万人在线</div>
    </nav>

    <!-- 轮播 + 热搜 -->
    <section class="hero">
      <!-- 用 Element Plus 的 Carousel：自动播放、箭头、指示器都不用自己写 -->
      <div class="banner-wrap">
        <el-carousel
          v-if="banners.length"
          class="banner"
          height="300px"
          :interval="4500"
          :pause-on-hover="true"
          arrow="hover"
          indicator-position="inside"
          @change="(i) => (bannerIndex = i)"
        >
          <el-carousel-item v-for="b in banners" :key="b.id">
            <div class="banner-slide" @click="goBanner(b)">
              <div class="banner-info">
                <span class="banner-tag">{{ b.tag }}</span>
                <h2>{{ b.title }}</h2>
                <p>{{ b.subtitle }}</p>
                <span class="banner-btn">立即观看 ▶</span>
              </div>
              <div class="banner-art" :style="coverStyle(b)">
                <span class="banner-emoji">{{ b.emoji }}</span>
              </div>
            </div>
          </el-carousel-item>
        </el-carousel>
        <div v-else class="banner-empty skeleton"></div>
      </div>

      <aside class="side">
        <div class="side-card">
          <h4>🔍 大家都在搜</h4>
          <ul class="hot-list">
            <li v-for="(h, i) in hotSearch" :key="h" @click="doSearch(h)">
              <span class="hot-no" :class="{ top3: i < 3 }">{{ i + 1 }}</span>
              <span class="ellipsis">{{ h }}</span>
            </li>
          </ul>
        </div>
        <div class="side-card login-card">
          <div class="lc-emoji">🎉</div>
          <p>登录后可以发弹幕、评论、收藏视频</p>
          <button class="btn btn-primary btn-round" @click="$router.push('/dynamic')">看看动态</button>
        </div>
      </aside>
    </section>

    <!-- 视频网格 -->
    <section class="feed">
      <header class="feed-head">
        <h3>
          {{ activeCategory === 'recommend' ? '为你推荐' : activeCategory === 'hot' ? '正在热播' : activeName + '区' }}
        </h3>
        <span class="feed-sub">双击封面可以直接进播放页 · 共 {{ videos.length }} 条</span>
      </header>

      <div v-if="loading" class="grid">
        <div v-for="i in 12" :key="i">
          <el-skeleton animated>
            <template #template>
              <el-skeleton-item variant="image" class="sk-cover-el" />
              <div class="sk-lines">
                <el-skeleton-item variant="text" style="width: 85%" />
                <el-skeleton-item variant="text" style="width: 50%; margin-top: 10px" />
              </div>
            </template>
          </el-skeleton>
        </div>
      </div>

      <div v-else class="grid">
        <VideoCard
          v-for="(v, i) in videos"
          :key="v.id"
          :video="v"
          v-reveal="{ delay: Math.min(i, 11) * 28 }"
        />
      </div>

      <div v-if="!loading && hasMore" class="more">
        <button class="btn btn-ghost btn-round" :disabled="loadingMore" @click="loadMore">
          {{ loadingMore ? '加载中...' : '加载更多' }}
        </button>
      </div>
      <div v-if="!loading && !hasMore && videos.length" class="more muted">—— 已经到底啦 ——</div>
    </section>
  </div>
</template>

<style scoped>
.home {
  padding-top: 16px;
}

/* ---------- 分区导航 ---------- */
.channel-bar {
  display: flex;
  align-items: center;
  gap: 6px;
  background: #fff;
  border-radius: 8px;
  padding: 10px 14px;
  margin-bottom: 16px;
  box-shadow: 0 1px 4px rgba(0, 0, 0, 0.04);
  overflow-x: auto;
}

.channel {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 6px 12px;
  border-radius: 999px;
  color: var(--text-2);
  font-size: 14px;
  white-space: nowrap;
  transition: all 0.2s;
}

.channel:hover {
  color: var(--bili-pink);
  background: #fff5f8;
}

.channel.on {
  background: linear-gradient(135deg, #ff9ab8, #fb7299);
  color: #fff;
  font-weight: 600;
  box-shadow: 0 3px 10px rgba(251, 114, 153, 0.35);
}

.ch-emoji {
  font-size: 15px;
}

.online {
  margin-left: auto;
  font-size: 12px;
  color: var(--text-3);
  white-space: nowrap;
  padding-left: 12px;
}

/* ---------- 轮播 ---------- */
.hero {
  display: grid;
  grid-template-columns: 1fr 300px;
  gap: 16px;
  margin-bottom: 24px;
}

.banner-wrap {
  border-radius: 10px;
  overflow: hidden;
  box-shadow: var(--shadow-card);
  background: #fff;
}

.banner-empty {
  width: 100%;
  height: 300px;
}

/* 把 Element Plus 轮播的默认配色改成 B 站粉 */
.banner :deep(.el-carousel__item) {
  border-radius: 10px;
  overflow: hidden;
}

.banner :deep(.el-carousel__indicators--inside) {
  left: auto;
  right: 24px;
  bottom: 18px;
  transform: none;
  display: flex;
  gap: 6px;
  align-items: center;
}

.banner :deep(.el-carousel__indicator) {
  padding: 0;
}

.banner :deep(.el-carousel__indicator button) {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: rgba(0, 0, 0, 0.18);
  opacity: 1;
  transition: all 0.25s;
}

.banner :deep(.el-carousel__indicator.is-active button) {
  width: 20px;
  border-radius: 999px;
  background: var(--bili-pink);
}

.banner :deep(.el-carousel__arrow) {
  background: rgba(0, 0, 0, 0.22);
  font-size: 14px;
}

.banner :deep(.el-carousel__arrow:hover) {
  background: rgba(251, 114, 153, 0.85);
}

.banner-slide {
  display: flex;
  height: 100%;
  cursor: pointer;
}

.banner-info {
  flex: 1;
  padding: 40px 36px;
  background: linear-gradient(120deg, #fff 30%, #fff6f9 100%);
  display: flex;
  flex-direction: column;
  justify-content: center;
}

.banner-tag {
  align-self: flex-start;
  background: rgba(251, 114, 153, 0.12);
  color: var(--bili-pink);
  font-size: 12px;
  padding: 2px 8px;
  border-radius: 4px;
  margin-bottom: 12px;
}

.banner-info h2 {
  margin: 0 0 10px;
  font-size: 28px;
  line-height: 38px;
}

.banner-info p {
  margin: 0 0 20px;
  color: var(--text-2);
  font-size: 14px;
}

.banner-btn {
  align-self: flex-start;
  display: inline-flex;
  align-items: center;
  height: 38px;
  padding: 0 22px;
  border-radius: 999px;
  background: var(--bili-pink);
  color: #fff;
  font-size: 14px;
  font-weight: 500;
  transition: background 0.2s, transform 0.2s;
}

.banner-slide:hover .banner-btn {
  background: var(--bili-pink-hover);
  transform: translateX(2px);
}

.banner-art {
  width: 42%;
  display: flex;
  align-items: center;
  justify-content: center;
}

.banner-emoji {
  font-size: 92px;
  filter: drop-shadow(0 10px 24px rgba(0, 0, 0, 0.22));
  transition: transform 0.4s cubic-bezier(0.34, 1.4, 0.64, 1);
}

.banner-slide:hover .banner-emoji {
  transform: scale(1.12) rotate(-5deg);
}

/* ---------- 右侧 ---------- */
.side {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.side-card {
  background: #fff;
  border-radius: 10px;
  padding: 16px;
  box-shadow: var(--shadow-card);
}

.side-card h4 {
  margin: 0 0 12px;
  font-size: 15px;
}

.hot-list li {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 7px 0;
  cursor: pointer;
  font-size: 14px;
  color: var(--text-2);
}

.hot-list li:hover {
  color: var(--bili-pink);
}

.hot-no {
  width: 18px;
  text-align: center;
  font-weight: 700;
  font-size: 13px;
  color: var(--text-3);
}

.hot-no.top3 {
  color: var(--bili-pink);
}

.login-card {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  text-align: center;
  background: linear-gradient(160deg, #fff3f7, #eaf7ff);
}

.lc-emoji {
  font-size: 40px;
}

.login-card p {
  margin: 10px 0 14px;
  font-size: 13px;
  color: var(--text-2);
}

/* ---------- 视频网格 ---------- */
.feed-head {
  display: flex;
  align-items: baseline;
  gap: 12px;
  margin-bottom: 14px;
}

.feed-head h3 {
  margin: 0;
  font-size: 20px;
}

.feed-sub {
  font-size: 12px;
  color: var(--text-3);
}

.grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(240px, 1fr));
  gap: 20px 16px;
}

.sk-cover-el {
  width: 100%;
  aspect-ratio: 16 / 9;
  border-radius: 8px;
}

.sk-lines {
  padding: 12px 0;
}

.more {
  display: flex;
  justify-content: center;
  margin: 32px 0 8px;
}

.more.muted {
  color: var(--text-3);
  font-size: 13px;
}

@media (max-width: 1100px) {
  .hero {
    grid-template-columns: 1fr;
  }

  .side {
    flex-direction: row;
  }

  .side-card {
    flex: 1;
  }
}

@media (max-width: 720px) {
  .banner {
    height: 220px;
  }

  .banner-art {
    display: none;
  }

  .side {
    flex-direction: column;
  }
}
</style>
