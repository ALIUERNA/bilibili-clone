<script setup>
/**
 * 个人中心（左侧菜单 + 右侧内容）。
 * 路由：/user/:tab   →  overview | history | favorite | like | coin | follow | message | settings | upload | profile
 */
import { computed, onMounted, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useUserStore } from '../stores/user'
import UserAvatar from '../components/UserAvatar.vue'
import OverviewPanel from './uc/OverviewPanel.vue'
import VideoListPanel from './uc/VideoListPanel.vue'
import FollowPanel from './uc/FollowPanel.vue'
import MessagePanel from './uc/MessagePanel.vue'
import SettingsPanel from './uc/SettingsPanel.vue'
import UploadsPanel from './uc/UploadsPanel.vue'
import ProfilePanel from './uc/ProfilePanel.vue'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

const MENUS = [
  { key: 'overview', label: '我的主页', icon: 'HomeFilled' },
  { key: 'history', label: '观看历史', icon: 'Clock' },
  { key: 'favorite', label: '我的收藏', icon: 'Star' },
  { key: 'like', label: '点赞过', icon: 'Pointer' },
  { key: 'coin', label: '投币过', icon: 'Coin' },
  { key: 'follow', label: '我的关注', icon: 'UserFilled' },
  { key: 'message', label: '消息中心', icon: 'Message' },
  { key: 'upload', label: '我的投稿', icon: 'VideoCamera' },
  { key: 'profile', label: '编辑资料', icon: 'Edit' },
  { key: 'settings', label: '设置', icon: 'Setting' }
]

const tab = computed(() => {
  const t = String(route.params.tab || 'overview')
  return MENUS.some((m) => m.key === t) ? t : 'overview'
})

const current = computed(() => MENUS.find((m) => m.key === tab.value))

function go(key) {
  router.push(`/user/${key}`)
}

onMounted(() => {
  userStore.refresh()
  if (route.params.tab && !MENUS.some((m) => m.key === route.params.tab)) {
    router.replace('/user/overview')
  }
})

watch(tab, () => {
  window.scrollTo({ top: 0, behavior: 'smooth' })
})
</script>

<template>
  <div class="uc container">
    <!-- 左侧菜单 -->
    <aside class="side">
      <div class="me">
        <UserAvatar
          :face="userStore.user?.face"
          :face-url="userStore.user?.faceUrl"
          :size="64"
          :level="userStore.user?.level"
        />
        <div class="me-text">
          <div class="name ellipsis">{{ userStore.user?.name || '未登录' }}</div>
          <div class="sub">Lv{{ userStore.user?.level || 0 }} · {{ userStore.user?.vipLabel || '普通用户' }}</div>
        </div>
      </div>
      <nav class="menu">
        <button v-for="m in MENUS" :key="m.key" :class="{ on: tab === m.key }" @click="go(m.key)">
          <AiIcon><component :is="m.icon" /></AiIcon>
          {{ m.label }}
        </button>
      </nav>
      <div class="side-foot">
        <button class="btn btn-ghost btn-round" @click="router.push('/upload')">
          <AiIcon><Plus /></AiIcon> 投稿
        </button>
        <button class="btn btn-ghost btn-round" @click="userStore.logout()">退出登录</button>
      </div>
    </aside>

    <!-- 右侧内容 -->
    <main class="content">
      <AiBreadcrumb separator="/" class="crumb">
        <AiBreadcrumbItem :to="{ path: '/user/overview' }">个人中心</AiBreadcrumbItem>
        <AiBreadcrumbItem>{{ current?.label }}</AiBreadcrumbItem>
      </AiBreadcrumb>

      <OverviewPanel v-if="tab === 'overview'" />
      <VideoListPanel v-else-if="tab === 'history'" mode="history" />
      <VideoListPanel v-else-if="tab === 'favorite'" mode="favorite" />
      <VideoListPanel v-else-if="tab === 'like'" mode="like" />
      <VideoListPanel v-else-if="tab === 'coin'" mode="coin" />
      <FollowPanel v-else-if="tab === 'follow'" />
      <MessagePanel v-else-if="tab === 'message'" />
      <UploadsPanel v-else-if="tab === 'upload'" />
      <ProfilePanel v-else-if="tab === 'profile'" />
      <SettingsPanel v-else-if="tab === 'settings'" />
    </main>
  </div>
</template>

<style scoped>
.uc {
  display: grid;
  grid-template-columns: 220px minmax(0, 1fr);
  gap: 20px;
  padding: 20px 20px 50px;
  align-items: start;
}

.side {
  position: sticky;
  top: calc(var(--header-height) + 16px);
  background: #fff;
  border-radius: 12px;
  padding: 16px;
  box-shadow: var(--shadow-card);
}

.me {
  display: flex;
  align-items: center;
  gap: 12px;
  padding-bottom: 14px;
  border-bottom: 1px solid var(--line);
}

.me-text {
  min-width: 0;
}

.name {
  font-size: 15px;
  font-weight: 600;
}

.sub {
  font-size: 12px;
  color: var(--text-3);
}

.menu {
  display: flex;
  flex-direction: column;
  gap: 2px;
  margin: 12px 0;
}

.menu button {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 9px 12px;
  border-radius: 8px;
  font-size: 13px;
  color: var(--text-2);
  text-align: left;
  transition: background 0.18s, color 0.18s;
}

.menu button:hover {
  background: var(--surface-0);
  color: var(--bili-pink);
}

.menu button.on {
  background: rgba(110, 86, 248, 0.1);
  color: var(--bili-pink);
  font-weight: 600;
}

.ico {
  font-size: 15px;
}

.side-foot {
  display: flex;
  flex-direction: column;
  gap: 8px;
  padding-top: 12px;
  border-top: 1px solid var(--line);
}

.content {
  min-width: 0;
}

.crumb {
  margin-bottom: 14px;
}

@media (max-width: 900px) {
  .uc {
    grid-template-columns: 1fr;
    padding: 14px 14px 40px;
  }

  .side {
    position: static;
  }

  .menu {
    flex-direction: row;
    overflow-x: auto;
    padding-bottom: 4px;
  }

  .menu button {
    white-space: nowrap;
  }

  .side-foot {
    flex-direction: row;
  }
}
</style>
