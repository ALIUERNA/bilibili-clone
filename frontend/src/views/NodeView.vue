<script setup>
/**
 * 内容节点页（分区页 / 预留节点占位页）。
 *
 * 一个组件覆盖所有节点：
 *  - 有视频的节点（动画 / 音乐 / 游戏 / 科技 …）→ 左子分区 + 视频网格；
 *  - 预留节点（纪录片 / 直播 / 专栏 / 活动 / 社区中心 …）→ 占位内容 + 后续扩展说明，
 *    不会出现空白页或 404。
 */
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { api } from '../api'
import VideoCard from '../components/VideoCard.vue'
import { nodeIcon } from '../icons'

const props = defineProps({
  code: { type: String, default: '' }
})

const route = useRoute()
const router = useRouter()

const loading = ref(true)
const node = ref(null)
const videos = ref([])
const allNodes = ref([])
const sort = ref('hot')
const notFound = ref(false)

const nodeCode = computed(() => props.code || String(route.params.code || ''))

const childNodes = computed(() => node.value?.children || [])
const isPlaceholder = computed(() => !!node.value?.placeholder && !videos.value.length)

async function load() {
  loading.value = true
  notFound.value = false
  try {
    const res = await api.node(nodeCode.value)
    node.value = res?.node || null
    videos.value = res?.videos || []
    if (!node.value) notFound.value = true
  } catch (e) {
    notFound.value = true
  } finally {
    loading.value = false
  }
  if (!allNodes.value.length) {
    api
      .nodes()
      .then((list) => {
        allNodes.value = list || []
      })
      .catch(() => {})
  }
}

function sortedVideos() {
  const list = [...videos.value]
  switch (sort.value) {
    case 'new':
      return list.sort((a, b) => String(b.pubTime).localeCompare(String(a.pubTime)))
    case 'play':
      return list.sort((a, b) => b.views - a.views)
    case 'danmaku':
      return list.sort((a, b) => b.danmakus - a.danmakus)
    default:
      return list.sort((a, b) => b.views + b.likes * 12 - (a.views + a.likes * 12))
  }
}

const shownVideos = computed(() => sortedVideos())

onMounted(load)
watch(nodeCode, () => load())
</script>

<template>
  <div class="node-page container">
    <div v-if="loading" class="skeleton-wrap">
      <div class="skeleton sk-head"></div>
      <div class="grid">
        <div v-for="i in 8" :key="i" class="skeleton sk-card"></div>
      </div>
    </div>

    <div v-else-if="notFound" class="empty">
      <h2>这个节点还没开放</h2>
      <p>节点编码：<code>{{ nodeCode }}</code></p>
      <button class="btn btn-primary btn-round" @click="router.push('/')">回首页</button>
    </div>

    <template v-else>
      <!-- 节点头部 -->
      <header class="node-head" :class="{ placeholder: isPlaceholder }">
        <span class="node-emoji"><AiIcon :size="42"><component :is="nodeIcon(node?.code)" /></AiIcon></span>
        <div class="head-text">
          <h1>{{ node?.name }}</h1>
          <p>
            {{ node?.description || (node?.placeholder
              ? '这个分区的内容正在筹备中，先去看看其他分区吧。'
              : '这里汇总了该分区下的热门稿件，支持按热度 / 最新排序。') }}
          </p>
          <p class="meta">
            稿件数：{{ node?.videoCount ?? videos.length }}
          </p>
        </div>
      </header>

      <!-- 子节点（子分区） -->
      <nav v-if="childNodes.length" class="sub-nodes">
        <button v-for="c in childNodes" :key="c.code" class="sub-node" @click="router.push(c.routePath || `/node/${c.code}`)">
          <AiIcon><component :is="nodeIcon(c.code)" /></AiIcon>
          {{ c.name }}
        </button>
      </nav>

      <!-- 占位内容（预留节点） -->
      <section v-if="isPlaceholder" class="placeholder">
        <div class="ph-art">
          <span class="ph-emoji"><AiIcon :size="58"><Tools /></AiIcon></span>
          <div class="ph-ring"></div>
        </div>
        <h2>功能建设中</h2>
        <p class="ph-sub">这个分区还在筹备中，先去看看别的内容吧。</p>
        <div class="ph-actions">
          <button class="btn btn-primary btn-round" @click="router.push('/')">先看首页推荐</button>
          <button class="btn btn-ghost btn-round" @click="router.push('/ranking')">去排行榜</button>
        </div>
      </section>

      <!-- 视频列表 -->
      <section v-else class="feed">
        <header class="feed-head">
          <h3>{{ node?.name }} · 全部稿件</h3>
          <div class="sorts">
            <button :class="{ on: sort === 'hot' }" @click="sort = 'hot'">综合</button>
            <button :class="{ on: sort === 'new' }" @click="sort = 'new'">最新</button>
            <button :class="{ on: sort === 'play' }" @click="sort = 'play'">播放</button>
            <button :class="{ on: sort === 'danmaku' }" @click="sort = 'danmaku'">弹幕</button>
          </div>
        </header>

        <div v-if="shownVideos.length" class="grid">
          <VideoCard v-for="v in shownVideos" :key="v.id" :video="v" />
        </div>
        <div v-else class="empty-inline">
          <p>该节点下暂时还没有稿件</p>
          <button class="btn btn-primary btn-round" @click="router.push('/')">看看别的</button>
        </div>
      </section>

      <!-- 其它节点快捷入口 -->
      <section v-if="allNodes.length" class="other-nodes">
        <h3>其它内容节点</h3>
        <div class="chips">
          <button
            v-for="n in allNodes.filter((x) => x.code !== nodeCode)"
            :key="n.code"
            class="chip"
            @click="router.push(n.routePath || `/node/${n.code}`)"
          >
            <AiIcon><component :is="nodeIcon(n.code)" /></AiIcon> {{ n.name }}
          </button>
        </div>
      </section>
    </template>
  </div>
</template>

<style scoped>
.node-page {
  padding: 18px 20px 50px;
}

.node-head {
  display: flex;
  align-items: center;
  gap: 18px;
  padding: 22px 24px;
  border-radius: 12px;
  background: linear-gradient(120deg, var(--brand-50), var(--cyan-50));
  margin-bottom: 16px;
}

.node-head.placeholder {
  background: linear-gradient(120deg, var(--surface-0), var(--cyan-50));
}

.node-emoji {
  font-size: 46px;
  line-height: 1;
}

.head-text h1 {
  margin: 0 0 6px;
  font-size: 24px;
  display: flex;
  align-items: center;
  gap: 10px;
}

.head-text p {
  margin: 0 0 4px;
  font-size: 13px;
  color: var(--text-2);
  max-width: 780px;
  line-height: 20px;
}

.head-text .meta {
  font-size: 12px;
  color: var(--text-3);
}

.sub-nodes {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-bottom: 16px;
}

.sub-node {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 6px 12px;
  border-radius: 999px;
  background: #fff;
  border: 1px solid var(--line);
  font-size: 13px;
  color: var(--text-2);
}

.sub-node:hover {
  border-color: var(--bili-pink);
  color: var(--bili-pink);
}

.placeholder {
  background: #fff;
  border-radius: 12px;
  padding: 34px 26px;
  text-align: center;
  box-shadow: var(--shadow-card);
}

.ph-art {
  position: relative;
  width: 96px;
  height: 96px;
  margin: 0 auto 12px;
  display: flex;
  align-items: center;
  justify-content: center;
}

.ph-emoji {
  font-size: 46px;
  position: relative;
  z-index: 1;
}

.ph-ring {
  position: absolute;
  inset: 0;
  border-radius: 50%;
  border: 3px dashed rgba(110, 86, 248, 0.4);
  animation: spin 12s linear infinite;
}

@keyframes spin {
  to {
    transform: rotate(360deg);
  }
}

.placeholder h2 {
  margin: 6px 0 8px;
  font-size: 20px;
}

.ph-sub {
  margin: 0 auto 20px;
  max-width: 520px;
  font-size: 13px;
  color: var(--text-2);
  line-height: 20px;
}

.ph-actions {
  display: flex;
  gap: 12px;
  justify-content: center;
  flex-wrap: wrap;
}

.feed-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 14px;
  flex-wrap: wrap;
  gap: 10px;
}

.feed-head h3 {
  margin: 0;
  font-size: 18px;
}

.sorts {
  display: flex;
  gap: 6px;
}

.sorts button {
  padding: 4px 12px;
  border-radius: 999px;
  font-size: 13px;
  color: var(--text-2);
  background: #fff;
}

.sorts button.on {
  background: var(--bili-pink);
  color: #fff;
}

.grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(240px, 1fr));
  gap: 20px 16px;
}

.empty-inline {
  text-align: center;
  padding: 40px 0;
  color: var(--text-3);
}

.other-nodes {
  margin-top: 30px;
}

.other-nodes h3 {
  font-size: 16px;
  margin: 0 0 12px;
}

.chips {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.chip {
  padding: 6px 12px;
  border-radius: 999px;
  background: #fff;
  border: 1px solid var(--line);
  font-size: 13px;
  color: var(--text-2);
}

.chip:hover {
  border-color: var(--bili-pink);
  color: var(--bili-pink);
}

.empty {
  text-align: center;
  padding: 60px 0;
}

.skeleton-wrap {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.sk-head {
  height: 120px;
  border-radius: 12px;
}

.sk-card {
  aspect-ratio: 16 / 9;
  border-radius: 8px;
}

@media (max-width: 720px) {
  .node-page {
    padding: 14px 14px 40px;
  }

  .node-head {
    padding: 16px;
    gap: 12px;
  }

  .node-emoji {
    font-size: 34px;
  }

  .head-text h1 {
    font-size: 19px;
  }

  .grid {
    grid-template-columns: repeat(auto-fill, minmax(160px, 1fr));
    gap: 14px 12px;
  }
}
</style>
