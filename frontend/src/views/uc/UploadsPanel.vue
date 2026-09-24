<script setup>
/** 个人中心 - 我的投稿（video_uploads 表 + 已发布视频） */
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { api } from '../../api'
import { useUserStore } from '../../stores/user'
import { formatDateTime } from '../../utils/format'

const router = useRouter()
const userStore = useUserStore()

const loading = ref(true)
const items = ref([])

const STATUS_TEXT = {
  DRAFT: '草稿',
  UPLOADING: '上传中',
  REVIEWING: '审核中',
  PUBLISHED: '已发布',
  REJECTED: '未通过'
}

async function load() {
  loading.value = true
  try {
    const res = await api.uc.uploads()
    items.value = res.items || []
  } catch (e) {
    userStore.showToast(e?.friendlyMessage || '加载失败')
  } finally {
    loading.value = false
  }
}

async function remove(item) {
  try {
    await api.uc.deleteUpload(item.id)
    items.value = items.value.filter((i) => i.id !== item.id)
    userStore.showToast('已删除该投稿记录')
  } catch (e) {
    userStore.showToast('删除失败')
  }
}

onMounted(load)
</script>

<template>
  <div class="panel">
    <header class="head">
      <h2>我的投稿</h2>
      <button class="btn btn-primary btn-round" @click="router.push('/upload')">＋ 新的投稿</button>
    </header>

    <div v-if="loading" class="skeleton sk-line"></div>

    <ul v-else-if="items.length" class="list">
      <li v-for="item in items" :key="item.id" class="item">
        <div class="thumb" @click="item.video_id && router.push({ name: 'video', params: { id: item.video_id } })">
          <img
            v-if="item.cover_path"
            :src="'/api/files/cover/' + String(item.cover_path).replace('covers/', '')"
            :alt="item.title"
            loading="lazy"
          />
          <span v-else class="thumb-emoji"><AiIcon :size="26"><Film /></AiIcon></span>
        </div>
        <div class="info">
          <h3>{{ item.title }}</h3>
          <p class="meta">
            <span class="status" :class="item.status?.toLowerCase()">{{ STATUS_TEXT[item.status] || item.status }}</span>
            <span>{{ item.category_name || '未分类' }}</span>
            <span>·</span>
            <span>{{ formatDateTime(item.created_at) }}</span>
          </p>
          <p class="meta">文件名：{{ item.file_name || '—' }}</p>
        </div>
        <div class="ops">
          <button
            v-if="item.video_id"
            class="btn btn-ghost btn-round"
            @click="router.push({ name: 'video', params: { id: item.video_id } })"
          >
            播放
          </button>
          <button class="btn btn-ghost btn-round" @click="remove(item)">删除</button>
        </div>
      </li>
    </ul>

    <div v-else class="empty">
      <p>还没有投稿记录</p>
      <button class="btn btn-primary btn-round" @click="router.push('/upload')">去投稿</button>
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

.list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.item {
  display: flex;
  gap: 14px;
  padding: 12px;
  background: #fff;
  border-radius: 10px;
  box-shadow: var(--shadow-card);
  align-items: center;
}

.thumb {
  width: 140px;
  flex-shrink: 0;
  aspect-ratio: 16 / 9;
  border-radius: 8px;
  overflow: hidden;
  background: var(--surface-sunken);
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
}

.thumb img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  display: block;
}

.thumb-emoji {
  font-size: 28px;
}

.info {
  flex: 1;
  min-width: 0;
}

.info h3 {
  margin: 0 0 6px;
  font-size: 15px;
}

.meta {
  margin: 0 0 4px;
  font-size: 12px;
  color: var(--text-3);
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
  align-items: center;
}

.status {
  padding: 1px 8px;
  border-radius: 999px;
  background: var(--surface-sunken);
}

.status.published {
  background: rgba(18, 183, 214, 0.12);
  color: var(--bili-blue);
}

.status.reviewing {
  background: rgba(255, 153, 0, 0.14);
  color: var(--gold-600);
}

.ops {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.empty {
  text-align: center;
  padding: 40px 0;
  color: var(--text-3);
}

.sk-line {
  height: 100px;
}

@media (max-width: 640px) {
  .item {
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
