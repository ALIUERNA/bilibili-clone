<script setup>
/** 个人中心 - 消息中心（系统通知 / 回复我的 / 收到的赞） */
import { computed, onMounted, ref } from 'vue'
import { api } from '../../api'
import { useUserStore } from '../../stores/user'
import { formatDateTime } from '../../utils/format'

const userStore = useUserStore()

const loading = ref(true)
const items = ref([])
const type = ref('ALL')
const unread = ref(0)

const types = [
  { key: 'ALL', label: '全部' },
  { key: 'SYSTEM', label: '系统通知' },
  { key: 'REPLY', label: '回复我的' },
  { key: 'LIKE', label: '收到的赞' },
  { key: 'AT', label: '@我的' }
]

const shown = computed(() =>
  type.value === 'ALL' ? items.value : items.value.filter((m) => m.type === type.value)
)

const ICONS = { SYSTEM: 'Bell', REPLY: 'ChatDotRound', LIKE: 'Pointer', AT: 'Promotion' }

async function load() {
  loading.value = true
  try {
    const res = await api.uc.messages('ALL')
    items.value = res.items || []
    unread.value = res.unread || 0
  } catch (e) {
    userStore.showToast(e?.friendlyMessage || '加载失败')
  } finally {
    loading.value = false
  }
}

async function readAll() {
  await api.uc.readMessages(0)
  items.value = items.value.map((m) => ({ ...m, is_read: 1 }))
  unread.value = 0
  userStore.showToast('已全部标记为已读')
}

async function readOne(msg) {
  if (msg.is_read) return
  await api.uc.readMessages(msg.id)
  msg.is_read = 1
  unread.value = Math.max(0, unread.value - 1)
}

onMounted(load)
</script>

<template>
  <div class="panel">
    <header class="head">
      <h2>消息中心 <span v-if="unread" class="badge">{{ unread }}</span></h2>
      <button class="btn btn-ghost btn-round" :disabled="!unread" @click="readAll">全部已读</button>
    </header>

    <nav class="tabs">
      <button v-for="t in types" :key="t.key" :class="{ on: type === t.key }" @click="type = t.key">
        {{ t.label }}
      </button>
    </nav>

    <div v-if="loading" class="skeleton sk-line"></div>

    <ul v-else-if="shown.length" class="msgs">
      <li v-for="m in shown" :key="m.id" class="msg" :class="{ unread: !m.is_read }" @click="readOne(m)">
        <span class="ico"><AiIcon><component :is="ICONS[m.type] || 'Bell'" /></AiIcon></span>
        <div class="body">
          <div class="title-row">
            <h3>{{ m.title }}</h3>
            <span class="time">{{ formatDateTime(m.created_at) }}</span>
          </div>
          <p>{{ m.content }}</p>
        </div>
      </li>
    </ul>

    <div v-else class="empty">还没有消息，去互动一下吧～</div>
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
  display: flex;
  align-items: center;
  gap: 8px;
}

.badge {
  font-size: 12px;
  background: var(--bili-pink);
  color: #fff;
  border-radius: 999px;
  padding: 1px 8px;
}

.tabs {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}

.tabs button {
  padding: 5px 14px;
  border-radius: 999px;
  background: #fff;
  border: 1px solid var(--line);
  font-size: 13px;
  color: var(--text-2);
}

.tabs button.on {
  background: var(--bili-pink);
  border-color: var(--bili-pink);
  color: #fff;
}

.msgs {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.msg {
  display: flex;
  gap: 12px;
  padding: 14px 16px;
  background: #fff;
  border-radius: 10px;
  box-shadow: var(--shadow-card);
  cursor: pointer;
}

.msg.unread {
  border-left: 3px solid var(--bili-pink);
}

.ico {
  font-size: 22px;
  flex-shrink: 0;
}

.body {
  flex: 1;
  min-width: 0;
}

.title-row {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 10px;
}

.title-row h3 {
  margin: 0;
  font-size: 15px;
}

.time {
  font-size: 12px;
  color: var(--text-3);
  flex-shrink: 0;
}

.body p {
  margin: 6px 0 0;
  font-size: 13px;
  color: var(--text-2);
  line-height: 20px;
}

.empty {
  text-align: center;
  padding: 40px 0;
  color: var(--text-3);
}

.sk-line {
  height: 90px;
}
</style>
