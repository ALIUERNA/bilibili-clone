<script setup>
import { onMounted, onUnmounted } from 'vue'
import { getToken } from './api'
import { useUserStore } from './stores/user'
import { clearToasts } from './ui/toast'
import TopBar from './components/TopBar.vue'
import AppFooter from './components/AppFooter.vue'
import LoginModal from './components/LoginModal.vue'
import ProfileModal from './components/ProfileModal.vue'
import AiBacktop from './ui/AiBacktop.vue'
import AiToast from './ui/AiToast.vue'

const userStore = useUserStore()

/** 请求返回 401（token 过期 / 被重置）时清掉本地登录态 */
function onUnauthorized(event) {
  const firedToken = event?.detail?.token || ''
  // 不带 token 的游客请求、或已经被替换掉的旧 token，不能影响当前会话
  if (!firedToken || firedToken !== getToken()) return
  userStore.clearSession()
}

/**
 * 应用主题。
 * 支持 light / dark / auto 三种取值，统一写成 html[data-theme]，
 * 同时保留 .theme-dark 类，兼容还在用它的老样式。
 */
function applyTheme(value) {
  const root = document.documentElement
  const prefersDark =
    typeof window.matchMedia === 'function' &&
    window.matchMedia('(prefers-color-scheme: dark)').matches
  const dark = value === 'dark' || (value === 'auto' && prefersDark)

  root.classList.toggle('theme-dark', dark)
  root.setAttribute('data-theme', dark ? 'dark' : 'light')
  root.classList.toggle('theme-auto', value === 'auto')
}

let mediaQuery = null

function onSystemThemeChange() {
  try {
    applyTheme(localStorage.getItem('bili-theme') || 'light')
  } catch (e) {
    /* ignore */
  }
}

onMounted(() => {
  // 页面打开时同步一次用户资料（等级、经验、头像可能是上次改过的）
  userStore.refresh()
  userStore.loadAuthStatus()
  window.addEventListener('bili-unauthorized', onUnauthorized)

  try {
    applyTheme(localStorage.getItem('bili-theme') || 'light')
  } catch (e) {
    applyTheme('light')
  }

  // 选了「跟随系统」时要监听系统主题变化
  if (typeof window.matchMedia === 'function') {
    mediaQuery = window.matchMedia('(prefers-color-scheme: dark)')
    mediaQuery.addEventListener?.('change', onSystemThemeChange)
  }
})

onUnmounted(() => {
  window.removeEventListener('bili-unauthorized', onUnauthorized)
  mediaQuery?.removeEventListener?.('change', onSystemThemeChange)
  clearToasts()
})
</script>

<template>
  <div class="app">
    <!-- 无障碍：键盘用户第一个 Tab 就能跳到正文 -->
    <a class="skip-link" href="#main">跳到主要内容</a>

    <!-- 页面顶部的极光氛围光，顶栏的半透明玻璃就压在它上面 -->
    <div class="app-aurora" aria-hidden="true"></div>

    <TopBar />

    <main id="main" class="app-main">
      <router-view v-slot="{ Component }">
        <transition name="page" mode="out-in">
          <component :is="Component" />
        </transition>
      </router-view>
    </main>

    <AppFooter />

    <LoginModal v-if="userStore.loginVisible" @close="userStore.loginVisible = false" />
    <ProfileModal v-if="userStore.profileVisible" @close="userStore.closeProfile()" />

    <AiBacktop :right="30" :bottom="86" :visibility-height="520" />
    <AiToast />
  </div>
</template>

<style scoped>
.app {
  min-height: 100vh;
  display: flex;
  flex-direction: column;
  position: relative;
}

/* 极光氛围：固定在页面顶部，永远不参与鼠标事件 */
.app-aurora {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  height: min(56vh, 520px);
  z-index: 0;
  pointer-events: none;
  background-image: var(--grad-aurora);
}

.app-main {
  position: relative;
  z-index: 1;
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
