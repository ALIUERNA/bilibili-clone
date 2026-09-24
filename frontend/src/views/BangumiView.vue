<script setup>
import { computed, onMounted, ref } from 'vue'
import { api } from '../api'
import { formatCount } from '../utils/format'
import { useUserStore } from '../stores/user'

const userStore = useUserStore()

const loading = ref(true)
const list = ref([])
const area = ref('全部')
const status = ref('全部')

const areas = ['全部', '日本', '国产']
const statuses = ['全部', '连载中', '已完结']

const filtered = computed(() =>
  list.value.filter((b) => {
    const okArea = area.value === '全部' || b.area === area.value
    const okStatus = status.value === '全部' || b.status === status.value
    return okArea && okStatus
  })
)

async function load() {
  loading.value = true
  try {
    list.value = await api.bangumi()
  } finally {
    loading.value = false
  }
}

async function follow(b) {
  if (!userStore.requireLogin()) return
  const res = await api.followBangumi(b.id)
  b.followed = res.followed
  b.followers = res.followers
  userStore.showToast(res.followed ? `已追番《${b.title}》` : `已取消追番《${b.title}》`)
}

onMounted(load)
</script>

<template>
  <div class="bg-page container-wide fade-up">
    <section class="hero">
      <div class="hero-left">
        <h1>番剧</h1>
        <p>追番、看番、补番，一起在弹幕里干杯</p>
      </div>
      <div class="hero-emoji"><AiIcon :size="60"><Monitor /></AiIcon></div>
    </section>

    <div class="filters">
      <div class="group">
        <span class="label">地区</span>
        <button v-for="a in areas" :key="a" class="chip" :class="{ on: area === a }" @click="area = a">
          {{ a }}
        </button>
      </div>
      <div class="group">
        <span class="label">状态</span>
        <button
          v-for="s in statuses"
          :key="s"
          class="chip"
          :class="{ on: status === s }"
          @click="status = s"
        >
          {{ s }}
        </button>
      </div>
      <span class="count">共 {{ filtered.length }} 部</span>
    </div>

    <div v-if="loading" class="grid">
      <div v-for="i in 10" :key="i">
        <div class="skeleton sk-cover"></div>
        <div class="skeleton sk-line"></div>
      </div>
    </div>

    <div v-else class="grid">
      <article v-for="(b, i) in filtered" :key="b.id" class="bg-card" v-reveal="{ delay: Math.min(i, 9) * 30 }">
        <div
          class="bg-cover"
          :style="{ backgroundImage: `radial-gradient(circle at 25% 20%, rgba(255,255,255,.5), transparent 45%), linear-gradient(135deg, ${b.color1}, ${b.color2})` }"
        >
          <span class="bg-emoji">{{ b.emoji }}</span>
          <span class="bg-status" :class="{ finished: b.status === '已完结' }">{{ b.status }}</span>
          <span class="bg-ep">更新至第 {{ b.episode }} 集</span>
        </div>

        <div class="bg-info">
          <h3 class="ellipsis" :title="b.title">{{ b.title }}</h3>
          <p class="bg-desc clamp-2">{{ b.desc }}</p>
          <div class="bg-meta">
            <span class="score"><AiIcon><StarFilled /></AiIcon> {{ b.score.toFixed(1) }}</span>
            <span>{{ formatCount(b.followers) }}追番</span>
            <span class="tag">{{ b.area }}</span>
          </div>
          <div class="bg-bottom">
            <span class="bg-time">{{ b.pubTime }}</span>
            <button class="follow" :class="{ on: b.followed }" @click="follow(b)">
              {{ b.followed ? '已追番' : '+ 追番' }}
            </button>
          </div>
        </div>
      </article>
    </div>
  </div>
</template>

<style scoped>
.bg-page {
  padding-top: 20px;
}

.hero {
  display: flex;
  align-items: center;
  justify-content: space-between;
  background: linear-gradient(120deg, var(--cyan-50), var(--brand-50));
  border-radius: 12px;
  padding: 26px 32px;
  margin-bottom: 18px;
}

.hero-left h1 {
  margin: 0 0 6px;
  font-size: 26px;
}

.hero-left p {
  margin: 0;
  color: var(--text-2);
}

.hero-emoji {
  font-size: 58px;
}

.filters {
  display: flex;
  align-items: center;
  gap: 22px;
  flex-wrap: wrap;
  background: #fff;
  border-radius: 10px;
  padding: 12px 18px;
  margin-bottom: 18px;
}

.group {
  display: flex;
  align-items: center;
  gap: 8px;
}

.label {
  color: var(--text-3);
  font-size: 13px;
}

.chip {
  padding: 4px 14px;
  border-radius: 999px;
  background: var(--surface-sunken);
  color: var(--text-2);
  font-size: 13px;
}

.chip:hover {
  color: var(--bili-blue);
}

.chip.on {
  background: var(--bili-pink);
  color: #fff;
  font-weight: 600;
}

.count {
  margin-left: auto;
  font-size: 13px;
  color: var(--text-3);
}

.grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(200px, 1fr));
  gap: 20px 16px;
}

.bg-card {
  background: #fff;
  border-radius: 10px;
  overflow: hidden;
  transition: transform 0.25s, box-shadow 0.25s;
}

.bg-card:hover {
  transform: translateY(-4px);
  box-shadow: 0 10px 24px rgba(0, 0, 0, 0.1);
}

.bg-cover {
  position: relative;
  aspect-ratio: 3 / 4;
  display: flex;
  align-items: center;
  justify-content: center;
}

.bg-emoji {
  font-size: 56px;
  filter: drop-shadow(0 6px 16px rgba(0, 0, 0, 0.22));
}

.bg-status {
  position: absolute;
  left: 8px;
  top: 8px;
  background: rgba(110, 86, 248, 0.92);
  color: #fff;
  font-size: 11px;
  padding: 2px 7px;
  border-radius: 4px;
}

.bg-status.finished {
  background: rgba(0, 0, 0, 0.45);
}

.bg-ep {
  position: absolute;
  left: 8px;
  bottom: 8px;
  right: 8px;
  color: #fff;
  font-size: 12px;
  text-shadow: 0 1px 4px rgba(0, 0, 0, 0.5);
}

.bg-info {
  padding: 10px 12px 12px;
}

.bg-info h3 {
  margin: 0 0 6px;
  font-size: 15px;
}

.bg-desc {
  margin: 0 0 8px;
  font-size: 12px;
  line-height: 18px;
  color: var(--text-3);
  min-height: 36px;
}

.bg-meta {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 12px;
  color: var(--text-3);
  margin-bottom: 10px;
}

.score {
  color: var(--gold-500);
  font-weight: 600;
}

.bg-bottom {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.bg-time {
  font-size: 12px;
  color: var(--text-3);
}

.follow {
  height: 28px;
  padding: 0 12px;
  border-radius: 6px;
  background: var(--bili-pink);
  color: #fff;
  font-size: 12px;
}

.follow.on {
  background: var(--surface-sunken);
  color: var(--text-2);
}

.sk-cover {
  aspect-ratio: 3 / 4;
}

.sk-line {
  height: 16px;
  margin-top: 10px;
}
</style>
