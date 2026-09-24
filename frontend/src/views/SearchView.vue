<script setup>
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { api } from '../api'
import VideoCard from '../components/VideoCard.vue'
import { formatCount } from '../utils/format'

const route = useRoute()
const router = useRouter()

const loading = ref(true)
const keyword = ref(route.query.keyword || '')
const items = ref([])
const users = ref([])
const total = ref(0)
const hotSearch = ref([])
const tab = ref('all')
const input = ref(keyword.value)

const resultText = computed(() => `找到 ${total.value} 个与「${keyword.value}」相关的视频`)

async function load() {
  keyword.value = String(route.query.keyword || '')
  input.value = keyword.value
  if (!keyword.value) {
    items.value = []
    users.value = []
    total.value = 0
    loading.value = false
    return
  }
  loading.value = true
  try {
    const res = await api.search({ keyword: keyword.value, page: 1, size: 32 })
    items.value = res.items || []
    users.value = res.users || []
    total.value = res.total || 0
  } finally {
    loading.value = false
  }
}

async function loadHot() {
  try {
    hotSearch.value = await api.suggest('')
  } catch (e) {
    hotSearch.value = []
  }
}

function search(word) {
  router.push({ name: 'search', query: { keyword: word } })
}

onMounted(async () => {
  await loadHot()
  await load()
})

watch(() => route.query.keyword, load)
</script>

<template>
  <div class="search-page container-wide fade-up">
    <!-- 搜索栏 -->
    <div class="search-head">
      <div class="box">
        <input
          v-model="input"
          type="text"
          placeholder="搜索视频、UP主、关键词"
          @keyup.enter="search(input)"
        />
        <button @click="search(input)">搜索</button>
      </div>
      <div class="hot">
        <span class="hot-label">热搜：</span>
        <button v-for="h in hotSearch.slice(0, 6)" :key="h" class="hot-item" @click="search(h)">{{ h }}</button>
      </div>
    </div>

    <div v-if="keyword" class="tabs">
      <button :class="{ on: tab === 'all' }" @click="tab = 'all'">综合</button>
      <button :class="{ on: tab === 'video' }" @click="tab = 'video'">视频 {{ total }}</button>
      <button :class="{ on: tab === 'user' }" @click="tab = 'user'">用户 {{ users.length }}</button>
    </div>

    <!-- 没有输入关键词 -->
    <div v-if="!keyword" class="empty-wrap">
      <AiEmpty description="输入关键词，开始发现有趣的内容">
        <div class="suggest-tags">
          <button v-for="h in hotSearch" :key="h" class="tag" @click="search(h)">{{ h }}</button>
        </div>
      </AiEmpty>
    </div>

    <template v-else>
      <div class="result-tip">{{ resultText }}</div>

      <div v-if="loading" class="grid">
        <div v-for="i in 8" :key="i">
          <AiSkeleton>
            <AiSkeletonItem variant="image" class="sk-cover-el" />
            <div class="sk-lines">
              <AiSkeletonItem variant="text" width="85%" />
              <AiSkeletonItem variant="text" width="50%" style="margin-top: 10px" />
            </div>
          </AiSkeleton>
        </div>
      </div>

      <template v-else>
        <!-- 相关用户 -->
        <section v-if="tab !== 'video' && users.length" class="user-row">
          <h3>相关用户</h3>
          <div class="user-list">
            <div v-for="u in users" :key="u.id" class="user-card" @click="$router.push({ name: 'space', params: { id: u.id } })">
              <span class="u-face">{{ u.face }}</span>
              <div class="u-info">
                <div class="u-name">{{ u.name }}</div>
                <div class="u-meta">{{ formatCount(u.fans) }} 粉丝 · {{ u.videoCount }} 投稿</div>
              </div>
            </div>
          </div>
        </section>

        <!-- 视频结果 -->
        <section v-if="tab !== 'user'">
          <div v-if="items.length" class="grid">
            <VideoCard
              v-for="(v, i) in items"
              :key="v.id"
              :video="v"
              v-reveal="{ delay: Math.min(i, 11) * 28 }"
            />
          </div>
          <div v-else class="empty-wrap">
            <AiEmpty description="没有找到相关视频，换个关键词试试？" />
          </div>
        </section>
      </template>
    </template>
  </div>
</template>

<style scoped>
.search-page {
  padding-top: 20px;
}

.search-head {
  background: #fff;
  border-radius: 10px;
  padding: 18px 20px;
  margin-bottom: 16px;
}

.box {
  display: flex;
  max-width: 640px;
  height: 44px;
  border-radius: 8px;
  background: var(--surface-sunken);
  overflow: hidden;
  padding-left: 16px;
}

.box input {
  flex: 1;
  border: none;
  background: transparent;
  font-size: 15px;
}

.box button {
  width: 96px;
  background: var(--bili-blue);
  color: #fff;
  font-size: 15px;
}

.box button:hover {
  background: var(--bili-blue-hover);
}

.hot {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-top: 12px;
  flex-wrap: wrap;
}

.hot-label {
  color: var(--text-3);
  font-size: 13px;
}

.hot-item {
  color: var(--text-2);
  font-size: 13px;
}

.hot-item:hover {
  color: var(--bili-pink);
}

.tabs {
  display: flex;
  gap: 8px;
  margin-bottom: 14px;
}

.tabs button {
  height: 34px;
  padding: 0 18px;
  border-radius: 999px;
  background: #fff;
  color: var(--text-2);
  font-size: 14px;
}

.tabs button.on {
  background: var(--bili-pink);
  color: #fff;
  font-weight: 600;
}

.result-tip {
  font-size: 13px;
  color: var(--text-3);
  margin-bottom: 14px;
}

.user-row {
  margin-bottom: 22px;
}

.user-row h3 {
  margin: 0 0 12px;
  font-size: 16px;
}

.user-list {
  display: flex;
  gap: 14px;
  flex-wrap: wrap;
}

.user-card {
  display: flex;
  align-items: center;
  gap: 10px;
  background: #fff;
  border-radius: 10px;
  padding: 12px 18px;
  min-width: 240px;
  cursor: pointer;
  transition: box-shadow 0.2s, transform 0.2s;
}

.user-card:hover {
  box-shadow: var(--shadow-card);
  transform: translateY(-2px);
}

.u-face {
  width: 44px;
  height: 44px;
  border-radius: 50%;
  background: var(--surface-sunken);
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 22px;
}

.u-name {
  font-size: 14px;
  font-weight: 600;
}

.u-meta {
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

.empty-wrap {
  padding: 40px 0;
  background: #fff;
  border-radius: 10px;
}

.suggest-tags {
  display: flex;
  gap: 10px;
  justify-content: center;
  flex-wrap: wrap;
  margin-top: 18px;
}

.suggest-tags .tag {
  padding: 6px 14px;
  border-radius: 999px;
  background: #fff;
  cursor: pointer;
  font-size: 13px;
}

.suggest-tags .tag:hover {
  color: var(--bili-pink);
}
</style>
