<script setup>
import { computed, onMounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { api } from '../api'
import VideoCard from '../components/VideoCard.vue'
import { coverStyle } from '../utils/format'
import { nodeIcon } from '../icons'
import { useUserStore } from '../stores/user'

const router = useRouter()
const userStore = useUserStore()

const loading = ref(true)
const banners = ref([])
const categories = ref([])
const nodes = ref([])
const hotSearch = ref([])
const onlineCount = ref(0)
const videos = ref([])
const activeCategory = ref('recommend')
const page = ref(1)
const hasMore = ref(false)
const loadingMore = ref(false)
const bannerIndex = ref(0)
const failedBanners = ref({})

const size = 24

const activeName = computed(
  () => categories.value.find((c) => c.key === activeCategory.value)?.name || '推荐'
)

/** 导航用的节点（一级），排除「推荐」 */
const navNodes = computed(() => nodes.value.filter((n) => n.code !== 'recommend'))

function hasCover(b) {
  return !!b.coverUrl && !failedBanners.value[b.id]
}

function onBannerError(b) {
  failedBanners.value = { ...failedBanners.value, [b.id]: true }
}

async function loadHome() {
  const res = await api.home()
  banners.value = (res.banners || []).map((b) => ({ ...b, coverText: b.coverText }))
  categories.value = res.categories || []
  nodes.value = res.nodes || []
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
}

/** 点击内容节点：有视频的节点进分区页，预留节点进占位页（都不会 404） */
function goNode(node) {
  if (node.code === 'recommend') {
    activeCategory.value = 'recommend'
    loadVideos(true)
    return
  }
  if (node.code === 'hot') {
    activeCategory.value = 'hot'
    loadVideos(true)
    return
  }
  const path = node.routePath || `/node/${node.code}`
  router.push(path)
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

/** banner 的兜底渐变（图片加载失败时用） */
function bannerStyle(b) {
  return coverStyle({ coverColor1: b.color1, coverColor2: b.color2 })
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
        v-for="c in categories.slice(0, 16)"
        :key="c.key"
        class="channel"
        :class="{ on: activeCategory === c.key }"
        @click="switchCategory(c.key)"
      >
        <span class="ch-emoji"><AiIcon><component :is="nodeIcon(c.key)" /></AiIcon></span>
        <span class="ch-name">{{ c.name }}</span>
      </button>
      <div class="online"><AiIcon><Histogram /></AiIcon> {{ (onlineCount / 10000).toFixed(1) }}万人在线</div>
    </nav>

    <!-- 轮播 + 热搜 -->
    <section class="hero">
      <div class="banner-wrap">
        <el-carousel
          v-if="banners.length"
          class="banner"
          height="100%"
          :interval="4500"
          :pause-on-hover="true"
          arrow="hover"
          indicator-position="inside"
          @change="(i) => (bannerIndex = i)"
        >
          <el-carousel-item v-for="b in banners" :key="b.id">
            <div class="banner-slide" :style="hasCover(b) ? {} : bannerStyle(b)" @click="goBanner(b)">
              <!-- 真实封面帧：铺满整个轮播项 -->
              <img
                v-if="hasCover(b)"
                class="banner-img"
                :src="b.coverUrl"
                :alt="b.title"
                loading="lazy"
                decoding="async"
                @error="onBannerError(b)"
              />
              <!-- 遮罩：让文字在任何图片上都清晰 -->
              <div class="banner-veil"></div>

              <div class="banner-info">
                <span class="banner-tag">{{ b.tag }}</span>
                <h2 class="clamp-2">{{ b.title }}</h2>
                <p class="clamp-2">{{ b.subtitle }}</p>
                <span class="banner-btn">立即观看 <AiIcon><VideoPlay /></AiIcon></span>
              </div>

              <span v-if="!hasCover(b)" class="banner-emoji">{{ b.emoji }}</span>
              <span v-else class="banner-uptag">{{ b.upName }}</span>
            </div>
          </el-carousel-item>
        </el-carousel>
        <div v-else class="banner-empty skeleton"></div>
      </div>

      <aside class="side">
        <div class="side-card">
          <h4><AiIcon><Search /></AiIcon> 大家都在搜</h4>
          <ul class="hot-list">
            <li v-for="(h, i) in hotSearch" :key="h" @click="doSearch(h)">
              <span class="hot-no" :class="{ top3: i < 3 }">{{ i + 1 }}</span>
              <span class="ellipsis">{{ h }}</span>
            </li>
          </ul>
        </div>
        <div v-if="!userStore.isLogin" class="side-card login-card">
          <div class="lc-emoji"><AiIcon><Present /></AiIcon></div>
          <p>登录后可以发弹幕、评论、收藏视频</p>
          <button class="btn btn-primary btn-round" @click="userStore.openLogin()">立即登录</button>
        </div>
        <div v-else class="side-card login-card">
          <div class="lc-emoji"><AiIcon><UserFilled /></AiIcon></div>
          <p>欢迎回来，<b>{{ userStore.user.name }}</b></p>
          <button class="btn btn-primary btn-round" @click="$router.push('/user/overview')">进入个人中心</button>
        </div>
      </aside>
    </section>

    <!-- 内容节点入口：视频 / 游戏 / 直播 / 番剧 / 专栏 / 活动 / 社区中心 … -->
    <section class="nodes">
      <header class="nodes-head">
        <h3>内容节点</h3>
      </header>
      <div class="node-grid">
        <button
          v-for="n in navNodes"
          :key="n.code"
          class="node-card"
          :class="{ placeholder: n.placeholder }"
          @click="goNode(n)"
        >
          <span class="node-emoji"><AiIcon><component :is="nodeIcon(n.code)" /></AiIcon></span>
          <span class="node-name">{{ n.name }}</span>
          <span class="node-count">{{ n.placeholder ? '即将开放' : n.videoCount + ' 个视频' }}</span>
        </button>
      </div>
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
  -webkit-overflow-scrolling: touch;
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
  background: var(--brand-50);
}

.channel.on {
  background: var(--grad-brand);
  color: #fff;
  font-weight: 600;
  box-shadow: 0 3px 10px rgba(110, 86, 248, 0.35);
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
  grid-template-columns: minmax(0, 1fr) 300px;
  gap: 16px;
  margin-bottom: 22px;
  /* 关键：不要拉伸轮播容器，否则右侧更高时会露出大片空白 */
  align-items: start;
}

.banner-wrap {
  position: relative;
  width: 100%;
  border-radius: 10px;
  overflow: hidden;
  box-shadow: var(--shadow-card);
  background: linear-gradient(135deg, var(--brand-200), var(--cyan-100));
  /* 固定 16:6.4 比例，任何屏幕下都不会出现空白 */
  aspect-ratio: 16 / 6.4;
  min-height: 190px;
  max-height: 320px;
}

/* 老浏览器不支持 aspect-ratio 时用 padding 撑高 */
.banner-wrap::before {
  content: '';
  display: block;
  padding-top: 40%;
}

.banner-wrap > * {
  position: absolute;
  inset: 0;
}

.banner,
.banner :deep(.el-carousel),
.banner :deep(.el-carousel__container) {
  height: 100% !important;
}

.banner-empty {
  width: 100%;
  height: 100%;
}

.banner :deep(.el-carousel__item) {
  border-radius: 10px;
  overflow: hidden;
  background: var(--surface-sunken);
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
  background: rgba(255, 255, 255, 0.72);
  opacity: 1;
  transition: all 0.25s;
}

.banner :deep(.el-carousel__indicator.is-active button) {
  width: 20px;
  border-radius: 999px;
  background: var(--bili-pink);
}

.banner :deep(.el-carousel__arrow) {
  background: rgba(0, 0, 0, 0.28);
  font-size: 14px;
}

.banner :deep(.el-carousel__arrow:hover) {
  background: rgba(110, 86, 248, 0.9);
}

.banner-slide {
  position: relative;
  display: flex;
  height: 100%;
  width: 100%;
  cursor: pointer;
  overflow: hidden;
}

/* 封面图：铺满 + object-fit cover，永不空白 */
.banner-img {
  position: absolute;
  inset: 0;
  width: 100%;
  height: 100%;
  object-fit: cover;
  object-position: center;
  display: block;
  z-index: 0;
}

.banner-veil {
  position: absolute;
  inset: 0;
  z-index: 1;
  background: linear-gradient(90deg, rgba(255, 255, 255, 0.96) 0%, rgba(255, 255, 255, 0.86) 38%, rgba(255, 255, 255, 0.35) 62%, rgba(0, 0, 0, 0.22) 100%);
}

.banner-info {
  position: relative;
  z-index: 2;
  flex: 1;
  max-width: 62%;
  padding: 28px 30px;
  display: flex;
  flex-direction: column;
  justify-content: center;
  min-width: 0;
}

.banner-tag {
  align-self: flex-start;
  background: rgba(110, 86, 248, 0.14);
  color: var(--bili-pink);
  font-size: 12px;
  padding: 2px 8px;
  border-radius: 4px;
  margin-bottom: 10px;
}

.banner-info h2 {
  margin: 0 0 8px;
  font-size: clamp(17px, 2.1vw, 26px);
  line-height: 1.35;
}

.banner-info p {
  margin: 0 0 16px;
  color: var(--text-2);
  font-size: 13px;
  line-height: 20px;
}

.banner-btn {
  align-self: flex-start;
  display: inline-flex;
  align-items: center;
  height: 34px;
  padding: 0 20px;
  border-radius: 999px;
  background: var(--bili-pink);
  color: #fff;
  font-size: 13px;
  font-weight: 500;
  transition: background 0.2s, transform 0.2s;
}

.banner-slide:hover .banner-btn {
  background: var(--bili-pink-hover);
  transform: translateX(2px);
}

.banner-emoji {
  position: absolute;
  right: 6%;
  top: 50%;
  transform: translateY(-50%);
  font-size: clamp(46px, 7vw, 86px);
  filter: drop-shadow(0 10px 24px rgba(0, 0, 0, 0.22));
  z-index: 2;
}

.banner-uptag {
  position: absolute;
  right: 16px;
  bottom: 16px;
  z-index: 2;
  font-size: 12px;
  color: #fff;
  background: rgba(0, 0, 0, 0.42);
  padding: 2px 10px;
  border-radius: 999px;
}

/* ---------- 内容节点 ---------- */
.nodes {
  margin-bottom: 24px;
}

.nodes-head {
  display: flex;
  align-items: baseline;
  gap: 12px;
  margin-bottom: 12px;
}

.nodes-head h3 {
  margin: 0;
  font-size: 18px;
}

.node-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(140px, 1fr));
  gap: 10px;
}

.node-card {
  position: relative;
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 2px;
  padding: 12px 14px;
  background: #fff;
  border-radius: 10px;
  box-shadow: 0 1px 4px rgba(0, 0, 0, 0.04);
  text-align: left;
  transition: transform 0.18s, box-shadow 0.18s, background 0.18s;
  overflow: hidden;
}

.node-card:hover {
  transform: translateY(-2px);
  box-shadow: 0 8px 20px rgba(110, 86, 248, 0.16);
  background: linear-gradient(135deg, var(--brand-50), var(--cyan-50));
}

.node-emoji {
  font-size: 20px;
  line-height: 1;
}

.node-name {
  font-size: 14px;
  font-weight: 600;
  color: var(--text-1);
}

.node-count {
  font-size: 12px;
  color: var(--text-3);
}

.node-card.placeholder {
  background: var(--surface-2);
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
  background: linear-gradient(160deg, var(--brand-50), var(--cyan-50));
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

  .banner-wrap {
    max-height: none;
  }

  .side {
    flex-direction: row;
  }

  .side-card {
    flex: 1;
  }
}

@media (max-width: 720px) {
  .banner-wrap {
    aspect-ratio: 16 / 9;
    min-height: 170px;
  }

  .banner-info {
    max-width: 100%;
    padding: 18px 18px;
    background: linear-gradient(90deg, rgba(255, 255, 255, 0.94), rgba(255, 255, 255, 0.72));
  }

  .banner-veil {
    background: linear-gradient(0deg, rgba(255, 255, 255, 0.9) 10%, rgba(255, 255, 255, 0.45) 70%, rgba(0, 0, 0, 0.12) 100%);
  }

  .banner-emoji,
  .banner-uptag {
    display: none;
  }

  .side {
    flex-direction: column;
  }

  .grid {
    grid-template-columns: repeat(auto-fill, minmax(160px, 1fr));
    gap: 14px 12px;
  }

  .node-grid {
    grid-template-columns: repeat(auto-fill, minmax(104px, 1fr));
  }
}
</style>
