<script setup>
/**
 * 个人中心 - 视频列表（观看历史 / 我的收藏 / 点赞过 / 投币过）。
 * 数据来自 MySQL：watch_history / favorite_items / user_actions。
 */
import { computed, onMounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { api } from '../../api'
import { useUserStore } from '../../stores/user'
import { formatCount, formatDateTime, formatDuration } from '../../utils/format'

const props = defineProps({
  mode: { type: String, default: 'history' } // history | favorite | like | coin
})

const emit = defineEmits(['count'])

const router = useRouter()
const userStore = useUserStore()

const loading = ref(true)
const rows = ref([])
const folders = ref([])
const activeFolder = ref(0)

const title = computed(
  () =>
    ({
      history: '观看历史',
      favorite: '我的收藏',
      like: '点赞过的视频',
      coin: '投币过的视频'
    })[props.mode] || '视频列表'
)

async function load() {
  loading.value = true
  try {
    if (props.mode === 'history') {
      const res = await api.uc.history(100)
      rows.value = res.items || []
    } else if (props.mode === 'favorite') {
      const res = await api.uc.favorites({ limit: 100, folderId: activeFolder.value })
      rows.value = res.items || []
      folders.value = res.folders || []
    } else {
      const res = await api.uc.actions(props.mode === 'like' ? 'LIKE' : 'COIN', 100)
      rows.value = res.items || []
    }
    emit('count', rows.value.length)
  } catch (e) {
    userStore.showToast(e?.friendlyMessage || '加载失败')
  } finally {
    loading.value = false
  }
}

async function removeItem(row) {
  const id = row.id ?? row.video_id
  try {
    if (props.mode === 'history') {
      if (row.video_id) await api.uc.deleteHistory(row.video_id)
      else await api.uc.deleteHistory(id)
    } else if (props.mode === 'favorite') {
      await api.uc.removeFavorite(id)
    } else {
      userStore.showToast('点赞 / 投币记录不支持删除')
      return
    }
    rows.value = rows.value.filter((r) => (r.id ?? r.video_id) !== id)
    userStore.showToast('已删除')
  } catch (e) {
    userStore.showToast('删除失败')
  }
}

async function clearAll() {
  if (props.mode !== 'history') return
  try {
    await api.uc.clearHistory()
    rows.value = []
    userStore.showToast('已清空观看历史')
  } catch (e) {
    userStore.showToast('清空失败')
  }
}

function open(row) {
  router.push({ name: 'video', params: { id: row.id ?? row.video_id } })
}

watch(activeFolder, () => load())
onMounted(load)
</script>

<template>
  <div class="panel">
    <header class="head">
      <h2>{{ title }}</h2>
      <div class="head-actions">
        <button v-if="props.mode === 'history' && rows.length" class="btn btn-ghost btn-round" @click="clearAll">
          清空历史
        </button>
        <button class="btn btn-ghost btn-round" @click="load">刷新</button>
      </div>
    </header>

    <div v-if="props.mode === 'favorite' && folders.length > 1" class="folders">
      <button
        v-for="f in folders"
        :key="f.id"
        class="folder"
        :class="{ on: activeFolder === f.id }"
        @click="activeFolder = f.id"
      >
        {{ f.name }} ({{ f.item_count }})
      </button>
    </div>

    <div v-if="loading" class="skeleton-wrap">
      <div v-for="i in 5" :key="i" class="skeleton sk-row"></div>
    </div>

    <ul v-else-if="rows.length" class="rows">
      <li v-for="row in rows" :key="row.id ?? row.video_id" class="row">
        <div class="thumb" @click="open(row)">
          <img
            v-if="row.coverUrl || row.cover_path"
            :src="row.coverUrl || '/api/files/cover/' + String(row.cover_path).replace('covers/', '')"
            :alt="row.title"
            loading="lazy"
          />
          <span v-if="row.cover_emoji" class="thumb-emoji">{{ row.cover_emoji }}</span>
          <span v-else class="thumb-emoji"><AiIcon :size="26"><Film /></AiIcon></span>
          <span class="duration">{{ formatDuration(row.duration) }}</span>
          <div class="progress-line">
            <div class="progress-fill" :style="{ width: Math.min(100, ((row.progress_sec || 0) / (row.duration || 1)) * 100) + '%' }"></div>
          </div>
        </div>

        <div class="info" @click="open(row)">
          <h3 class="clamp-2">{{ row.title }}</h3>
          <p class="meta">
            <span>{{ row.up_name || '未知 UP 主' }}</span>
            <span>·</span>
            <span><AiIcon><VideoPlay /></AiIcon> {{ formatCount(row.views || 0) }}</span>
            <span>·</span>
            <span><AiIcon><ChatDotRound /></AiIcon> {{ formatCount(row.danmaku_count || 0) }}</span>
          </p>
          <p class="time">
            <template v-if="props.mode === 'history'">
              看到第 {{ row.progress_sec || 0 }} 秒 · {{ formatDateTime(row.watched_at) }}
            </template>
            <template v-else-if="props.mode === 'favorite'">
              收藏于 {{ formatDateTime(row.fav_at) }}
            </template>
            <template v-else>操作于 {{ formatDateTime(row.created_at) }}</template>
          </p>
        </div>

        <div class="ops">
          <button class="btn btn-ghost btn-round" @click="open(row)">播放</button>
          <button class="btn btn-ghost btn-round" @click="removeItem(row)">
            {{ props.mode === 'favorite' ? '取消收藏' : '删除' }}
          </button>
        </div>
      </li>
    </ul>

    <div v-else class="empty">
      <p>{{ title }}还是空的</p>
      <button class="btn btn-primary btn-round" @click="router.push('/')">去首页逛逛</button>
    </div>
  </div>
</template>

<style scoped>
.panel {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.head {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.head h2 {
  margin: 0;
  font-size: 18px;
}

.head-actions {
  display: flex;
  gap: 8px;
}

.folders {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.folder {
  padding: 4px 12px;
  border-radius: 999px;
  background: #fff;
  border: 1px solid var(--line);
  font-size: 13px;
  color: var(--text-2);
}

.folder.on {
  background: var(--bili-pink);
  border-color: var(--bili-pink);
  color: #fff;
}

.rows {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.row {
  display: flex;
  gap: 14px;
  padding: 12px;
  background: #fff;
  border-radius: 10px;
  box-shadow: var(--shadow-card);
  align-items: center;
}

.thumb {
  position: relative;
  width: 168px;
  flex-shrink: 0;
  aspect-ratio: 16 / 9;
  border-radius: 8px;
  overflow: hidden;
  background: var(--surface-sunken);
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
}

.thumb img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  display: block;
}

.thumb-emoji {
  font-size: 30px;
}

.duration {
  position: absolute;
  right: 6px;
  bottom: 6px;
  font-size: 11px;
  color: #fff;
  background: rgba(0, 0, 0, 0.6);
  border-radius: 4px;
  padding: 0 5px;
}

.progress-line {
  position: absolute;
  left: 0;
  right: 0;
  bottom: 0;
  height: 3px;
  background: rgba(0, 0, 0, 0.35);
}

.progress-fill {
  height: 100%;
  background: var(--bili-pink);
}

.info {
  flex: 1;
  min-width: 0;
  cursor: pointer;
}

.info h3 {
  margin: 0 0 6px;
  font-size: 15px;
  font-weight: 500;
  line-height: 22px;
}

.info h3:hover {
  color: var(--bili-blue);
}

.meta,
.time {
  margin: 0;
  font-size: 12px;
  color: var(--text-3);
  display: flex;
  gap: 6px;
  flex-wrap: wrap;
}

.time {
  margin-top: 6px;
}

.ops {
  display: flex;
  flex-direction: column;
  gap: 8px;
  flex-shrink: 0;
}

.empty {
  text-align: center;
  padding: 40px 0;
  color: var(--text-3);
}

.skeleton-wrap {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.sk-row {
  height: 108px;
  border-radius: 10px;
}

@media (max-width: 720px) {
  .row {
    flex-direction: column;
    align-items: stretch;
  }

  .thumb {
    width: 100%;
  }

  .ops {
    flex-direction: row;
  }
}
</style>
