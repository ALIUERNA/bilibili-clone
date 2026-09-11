<script setup>
import { computed, onMounted, ref } from 'vue'
import { api } from '../api'
import VideoCard from '../components/VideoCard.vue'

const loading = ref(true)
const type = ref('all')
const category = ref('全部')
const list = ref([])

const types = [
  { key: 'all', name: '综合排行', emoji: '🏆' },
  { key: 'like', name: '最多点赞', emoji: '👍' },
  { key: 'coin', name: '最多投币', emoji: '🪙' },
  { key: 'fav', name: '最多收藏', emoji: '⭐' },
  { key: 'danmaku', name: '最多弹幕', emoji: '💬' }
]

const categories = computed(() => {
  const set = new Set(list.value.map((v) => v.category))
  return ['全部', ...set]
})

const filtered = computed(() =>
  category.value === '全部' ? list.value : list.value.filter((v) => v.category === category.value)
)

async function load() {
  loading.value = true
  try {
    list.value = await api.rankings({ type: type.value, limit: 100 })
  } finally {
    loading.value = false
  }
}

function switchType(key) {
  if (type.value === key) return
  type.value = key
  load()
}

onMounted(load)
</script>

<template>
  <div class="rank-page container fade-up">
    <section class="hero">
      <div class="hero-text">
        <h1>排行榜</h1>
        <p>根据播放、点赞、投币、收藏等数据综合计算，看看今天大家都在看什么</p>
      </div>
      <div class="hero-art">🏆</div>
    </section>

    <div class="type-tabs">
      <button v-for="t in types" :key="t.key" :class="{ on: type === t.key }" @click="switchType(t.key)">
        <span>{{ t.emoji }}</span>
        {{ t.name }}
      </button>
    </div>

    <div class="cat-chips">
      <button
        v-for="c in categories"
        :key="c"
        class="chip"
        :class="{ on: category === c }"
        @click="category = c"
      >
        {{ c }}
      </button>
    </div>

    <div v-if="loading" class="list">
      <div v-for="i in 8" :key="i" class="sk-row">
        <div class="skeleton sk-thumb"></div>
        <div class="sk-lines">
          <div class="skeleton sk-l1"></div>
          <div class="skeleton sk-l2"></div>
        </div>
      </div>
    </div>

    <div v-else class="list card">
      <VideoCard
        v-for="(v, i) in filtered"
        :key="v.id"
        :video="v"
        layout="rank"
        :rank="i + 1"
        v-reveal="{ delay: Math.min(i, 15) * 22 }"
      />
      <el-empty v-if="!filtered.length" description="该分区暂时没有上榜内容~" />
    </div>
  </div>
</template>

<style scoped>
.rank-page {
  padding-top: 20px;
}

.hero {
  display: flex;
  align-items: center;
  justify-content: space-between;
  background: linear-gradient(120deg, #fff1f6, #eaf7ff);
  border-radius: 12px;
  padding: 28px 32px;
  margin-bottom: 18px;
}

.hero-text h1 {
  margin: 0 0 8px;
  font-size: 28px;
}

.hero-text p {
  margin: 0;
  color: var(--text-2);
  font-size: 14px;
}

.hero-art {
  font-size: 64px;
}

.type-tabs {
  display: flex;
  gap: 10px;
  margin-bottom: 14px;
  flex-wrap: wrap;
}

.type-tabs button {
  display: flex;
  align-items: center;
  gap: 6px;
  height: 38px;
  padding: 0 18px;
  border-radius: 999px;
  background: #fff;
  color: var(--text-2);
  font-size: 14px;
  transition: all 0.2s;
}

.type-tabs button:hover {
  color: var(--bili-pink);
}

.type-tabs button.on {
  background: var(--bili-pink);
  color: #fff;
  font-weight: 600;
  box-shadow: 0 4px 12px rgba(251, 114, 153, 0.35);
}

.cat-chips {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
  margin-bottom: 16px;
}

.chip {
  padding: 5px 14px;
  border-radius: 999px;
  background: #fff;
  color: var(--text-2);
  font-size: 13px;
}

.chip:hover {
  color: var(--bili-blue);
}

.chip.on {
  background: #eaf7ff;
  color: var(--bili-blue);
  font-weight: 600;
}

.list {
  background: #fff;
  border-radius: 10px;
  padding: 8px;
  min-height: 300px;
}

.sk-row {
  display: flex;
  gap: 16px;
  padding: 12px 16px;
}

.sk-thumb {
  width: 160px;
  aspect-ratio: 16 / 9;
}

.sk-lines {
  flex: 1;
}

.sk-l1 {
  height: 18px;
  width: 70%;
  margin-bottom: 12px;
}

.sk-l2 {
  height: 14px;
  width: 40%;
}

.empty {
  padding: 60px 0;
  text-align: center;
  color: var(--text-3);
}
</style>
