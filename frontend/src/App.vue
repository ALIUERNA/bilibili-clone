<script setup>
import { onMounted } from 'vue'
import zhCn from 'element-plus/es/locale/lang/zh-cn'
import { useUserStore } from './stores/user'
import TopBar from './components/TopBar.vue'
import AppFooter from './components/AppFooter.vue'
import LoginModal from './components/LoginModal.vue'
import ProfileModal from './components/ProfileModal.vue'

const userStore = useUserStore()

onMounted(() => {
  // 页面打开时同步一次用户资料（等级、经验、头像可能是上次改过的）
  userStore.refresh()
})
</script>

<template>
  <!-- ConfigProvider 统一 Element Plus 组件的语言（中文）和尺寸 -->
  <el-config-provider :locale="zhCn" size="default">
    <div class="app">
      <TopBar />

      <main class="app-main">
        <router-view v-slot="{ Component }">
          <transition name="page" mode="out-in">
            <component :is="Component" />
          </transition>
        </router-view>
      </main>

      <AppFooter />

      <LoginModal v-if="userStore.loginVisible" @close="userStore.loginVisible = false" />
      <ProfileModal v-if="userStore.profileVisible" @close="userStore.closeProfile()" />

      <!-- 回到顶部：用 Element Plus 的 Backtop，自带滚动监听和过渡 -->
      <el-backtop :right="28" :bottom="80" :visibility-height="500" />
    </div>
  </el-config-provider>
</template>

<style scoped>
.app {
  min-height: 100vh;
  display: flex;
  flex-direction: column;
}

.app-main {
  flex: 1;
  padding-top: var(--header-height);
}

/* 路由切换动画 */
.page-enter-active,
.page-leave-active {
  transition: opacity 0.22s ease, transform 0.22s ease;
}

.page-enter-from {
  opacity: 0;
  transform: translateY(10px);
}

.page-leave-to {
  opacity: 0;
}
</style>
