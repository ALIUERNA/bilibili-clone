<script setup>
/** 个人中心 - 关注列表 */
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { api } from '../../api'
import { useUserStore } from '../../stores/user'
import { formatCount } from '../../utils/format'

const router = useRouter()
const userStore = useUserStore()

const loading = ref(true)
const items = ref([])

async function load() {
  loading.value = true
  try {
    const res = await api.uc.following()
    items.value = res.items || []
  } catch (e) {
    userStore.showToast(e?.friendlyMessage || '加载失败')
  } finally {
    loading.value = false
  }
}

async function unfollow(up) {
  try {
    const res = await api.uc.follow(up.id)
    if (!res.followed) {
      items.value = items.value.filter((i) => i.id !== up.id)
      userStore.showToast('已取消关注')
    }
  } catch (e) {
    userStore.showToast('操作失败')
  }
}

onMounted(load)
</script>

<template>
  <div class="panel">
    <header class="head">
      <h2>我的关注 <span class="count">{{ items.length }}</span></h2>
      <button class="btn btn-ghost btn-round" @click="load">刷新</button>
    </header>

    <div v-if="loading" class="skeleton sk-line"></div>

    <ul v-else-if="items.length" class="list">
      <li v-for="up in items" :key="up.id" class="up">
        <span class="face">
          <span v-if="up.avatar_emoji">{{ up.avatar_emoji }}</span>
          <AiIcon v-else :size="20"><UserFilled /></AiIcon>
        </span>
        <div class="info">
          <h3>{{ up.nickname }}</h3>
          <p>{{ up.sign || '这个人很懒~' }}</p>
          <span class="fans">{{ formatCount(up.follower_count || 0) }} 粉丝</span>
        </div>
        <div class="ops">
          <button class="btn btn-ghost btn-round" @click="router.push({ name: 'space', params: { id: up.id } })">
            进入空间
          </button>
          <button class="btn btn-ghost btn-round" @click="unfollow(up)">取消关注</button>
        </div>
      </li>
    </ul>

    <div v-else class="empty">还没有关注任何人</div>
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

.count {
  font-size: 13px;
  color: var(--text-3);
}

.list {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(300px, 1fr));
  gap: 12px;
}

.up {
  display: flex;
  gap: 12px;
  align-items: center;
  padding: 14px;
  background: #fff;
  border-radius: 10px;
  box-shadow: var(--shadow-card);
}

.face {
  font-size: 30px;
}

.info {
  flex: 1;
  min-width: 0;
}

.info h3 {
  margin: 0 0 4px;
  font-size: 15px;
}

.info p {
  margin: 0 0 4px;
  font-size: 12px;
  color: var(--text-3);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.fans {
  font-size: 12px;
  color: var(--text-3);
}

.ops {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.empty {
  text-align: center;
  padding: 40px 0;
  color: var(--text-3);
}

.sk-line {
  height: 80px;
}

@media (max-width: 560px) {
  .up {
    flex-wrap: wrap;
  }

  .ops {
    flex-direction: row;
  }
}
</style>
