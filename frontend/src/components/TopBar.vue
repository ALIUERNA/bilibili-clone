<script setup>
import { onUnmounted, ref, watch } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { useDebounceFn } from '@vueuse/core'
import { api } from '../api'
import { useUserStore } from '../stores/user'
import UserAvatar from './UserAvatar.vue'
import UserHoverCard from './UserHoverCard.vue'

const router = useRouter()
const route = useRoute()
const userStore = useUserStore()

const keyword = ref(route.query.keyword || '')
const suggestList = ref([])
const showSuggest = ref(false)
const showUserMenu = ref(false)

const navs = [
  { name: '首页', to: '/' },
  { name: '番剧', to: '/bangumi' },
  { name: '排行榜', to: '/ranking' },
  { name: '动态', to: '/dynamic' }
]

// 鼠标点一下头像：没登录就弹登录框，登录了就展开下拉菜单
function onAvatarClick() {
  if (!userStore.isLogin) {
    userStore.openLogin()
    return
  }
  showUserMenu.value = !showUserMenu.value
}

function goSpace() {
  showUserMenu.value = false
  router.push({ name: 'space', params: { id: 1000 } })
}

function openProfile() {
  showUserMenu.value = false
  userStore.openProfile()
}

function logout() {
  showUserMenu.value = false
  userStore.logout()
}

// ---------- 搜索联想（用 VueUse 的防抖，省掉手写 setTimeout）----------
const fetchSuggest = useDebounceFn(async (val) => {
  try {
    suggestList.value = await api.suggest(val)
    showSuggest.value = true
  } catch (e) {
    suggestList.value = []
  }
}, 220)

watch(keyword, (val) => {
  if (!val.trim()) {
    suggestList.value = []
    return
  }
  fetchSuggest(val.trim())
})

function doSearch(word) {
  const kw = (word ?? keyword.value).trim()
  if (!kw) return
  keyword.value = kw
  showSuggest.value = false
  router.push({ name: 'search', query: { keyword: kw } })
}

function pickSuggest(text) {
  keyword.value = text
  doSearch(text)
}

function goUpload() {
  if (userStore.requireLogin()) {
    userStore.showToast('演示项目：投稿功能暂未开放 (｡･ω･｡)')
  }
}

onUnmounted(() => {
  showSuggest.value = false
})
</script>

<template>
  <header class="topbar">
    <div class="topbar-inner">
      <!-- 左侧：logo + 导航 -->
      <a class="logo" href="#/" title="哔哩哔哩 (゜-゜)つロ 干杯~">
        <span class="logo-icon">
          <svg viewBox="0 0 100 100" width="26" height="26">
            <rect width="100" height="100" rx="24" fill="#FB7299" />
            <path
              d="M30 40h40a6 6 0 0 1 6 6v22a6 6 0 0 1-6 6H30a6 6 0 0 1-6-6V46a6 6 0 0 1 6-6z"
              fill="#fff"
            />
            <path d="M32 30l12 10M68 30L56 40" stroke="#fff" stroke-width="6" stroke-linecap="round" />
            <circle cx="40" cy="55" r="4" fill="#FB7299" />
            <circle cx="60" cy="55" r="4" fill="#FB7299" />
          </svg>
        </span>
        <span class="logo-text">哔哩哔哩</span>
      </a>

      <nav class="nav">
        <router-link
          v-for="item in navs"
          :key="item.to"
          :to="item.to"
          class="nav-item"
          :class="{ active: route.path === item.to }"
        >
          {{ item.name }}
        </router-link>
        <span class="nav-item muted" title="演示项目，未实现">直播</span>
        <span class="nav-item muted" title="演示项目，未实现">游戏中心</span>
        <span class="nav-item muted" title="演示项目，未实现">会员购</span>
      </nav>

      <!-- 中间：搜索框 -->
      <div class="search-wrap" @mouseleave="showSuggest = false">
        <div class="search-box">
          <input
            v-model="keyword"
            type="text"
            placeholder="雾都食肆  9.2分"
            @keyup.enter="doSearch()"
            @focus="showSuggest = suggestList.length > 0"
          />
          <button class="search-btn" title="搜索" @click="doSearch()">
            <svg viewBox="0 0 20 20" width="18" height="18">
              <circle cx="8.5" cy="8.5" r="5.5" fill="none" stroke="#fff" stroke-width="2" />
              <path d="M13 13l4 4" stroke="#fff" stroke-width="2" stroke-linecap="round" />
            </svg>
          </button>
        </div>
        <transition name="fade">
          <ul v-if="showSuggest && suggestList.length" class="suggest">
            <li v-for="(s, i) in suggestList" :key="i" @mousedown.prevent="pickSuggest(s)">
              <span class="dot">🔍</span>
              <span class="ellipsis">{{ s }}</span>
            </li>
          </ul>
        </transition>
      </div>

      <!-- 右侧：用户区 -->
      <div class="right">
        <el-tooltip content="演示项目：消息中心仅作展示" placement="bottom" :show-after="400">
          <button class="icon-btn" @click="userStore.showToast('演示项目：消息中心仅作展示~')">
            <span class="ico">✉️</span><span class="icon-text">消息</span>
          </button>
        </el-tooltip>

        <el-tooltip content="演示项目：历史记录仅作展示" placement="bottom" :show-after="400">
          <button class="icon-btn" @click="userStore.showToast('演示项目：历史记录仅作展示~')">
            <span class="ico">🕘</span><span class="icon-text">历史</span>
          </button>
        </el-tooltip>

        <el-tooltip content="演示项目：创作中心仅作展示" placement="bottom" :show-after="400">
          <button class="icon-btn" @click="userStore.showToast('演示项目：创作中心仅作展示~')">
            <span class="ico">✨</span><span class="icon-text">创作中心</span>
          </button>
        </el-tooltip>

        <button class="vip-btn" @click="userStore.showToast('演示项目：大会员页面未开放~')">
          <span>大会员</span>
        </button>

        <!-- 头像：鼠标悬停弹出资料卡，点击展开菜单 -->
        <div class="avatar-wrap">
          <el-popover
            placement="bottom-end"
            :width="330"
            :show-after="150"
            :hide-after="180"
            :offset="12"
            :disabled="!userStore.isLogin || showUserMenu"
            popper-class="user-hover-popover"
            trigger="hover"
          >
            <template #reference>
              <div class="avatar-hit" @click="onAvatarClick">
                <UserAvatar
                  v-if="userStore.isLogin"
                  :face="userStore.user.face"
                  :face-url="userStore.user.faceUrl"
                  :size="38"
                  :level="0"
                  hover-zoom
                  ring
                />
                <span v-else class="avatar guest"><span>👤</span></span>
              </div>
            </template>

            <UserHoverCard v-if="userStore.isLogin" :user="userStore.user" />
          </el-popover>

          <!-- 点击下拉菜单 -->
          <transition name="fade">
            <div v-if="showUserMenu && userStore.isLogin" class="user-menu">
              <div class="user-menu-head">
                <UserAvatar :face="userStore.user.face" :face-url="userStore.user.faceUrl" :size="44" :level="userStore.user.level" />
                <div class="head-text">
                  <div class="name ellipsis">{{ userStore.user.name }}</div>
                  <div class="sub">Lv{{ userStore.user.level }} · {{ userStore.user.vipLabel || '普通用户' }}</div>
                  <div class="mini-bar">
                    <div class="mini-fill" :style="{ width: userStore.expPercent + '%' }"></div>
                  </div>
                  <div class="mini-text">{{ userStore.user.exp }}/{{ userStore.user.expMax }} 经验</div>
                </div>
              </div>
              <div class="user-menu-body">
                <button @click="goSpace">个人空间</button>
                <button @click="openProfile">编辑资料</button>
                <button @click="logout">退出登录</button>
              </div>
            </div>
          </transition>

          <!-- 经验飘字：点赞 / 发弹幕 / 签到 之后会往上飘 -->
          <div class="float-layer">
            <transition-group name="float">
              <span v-for="f in userStore.expFloats" :key="f.id" class="exp-float">
                {{ f.text }}
              </span>
            </transition-group>
          </div>
        </div>

        <button class="upload-btn" @click="goUpload">投稿</button>
      </div>
    </div>

    <!-- 升级时的全屏光晕特效 -->
    <transition name="fade">
      <div v-if="userStore.levelUp" class="levelup-glow"></div>
    </transition>
  </header>
</template>

<style scoped>
.topbar {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  height: var(--header-height);
  background: #fff;
  box-shadow: 0 1px 4px rgba(0, 0, 0, 0.08);
  z-index: 1000;
}

.topbar-inner {
  max-width: 1600px;
  height: 100%;
  margin: 0 auto;
  padding: 0 24px;
  display: flex;
  align-items: center;
  gap: 18px;
}

/* ---------- logo ---------- */
.logo {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-shrink: 0;
}

.logo-icon {
  display: flex;
  transition: transform 0.4s cubic-bezier(0.34, 1.56, 0.64, 1);
}

.logo:hover .logo-icon {
  transform: rotate(-8deg) scale(1.12);
}

.logo-text {
  font-size: 19px;
  font-weight: 700;
  color: var(--bili-pink);
  letter-spacing: 1px;
}

/* ---------- 导航 ---------- */
.nav {
  display: flex;
  align-items: center;
  gap: 4px;
  flex-shrink: 0;
}

.nav-item {
  position: relative;
  padding: 6px 10px;
  border-radius: 6px;
  color: var(--text-1);
  font-size: 15px;
  transition: background 0.2s, color 0.2s, transform 0.2s;
  white-space: nowrap;
}

.nav-item::after {
  content: '';
  position: absolute;
  left: 50%;
  bottom: 0;
  width: 0;
  height: 2px;
  background: var(--bili-pink);
  border-radius: 2px;
  transform: translateX(-50%);
  transition: width 0.25s ease;
}

.nav-item:hover {
  color: var(--bili-pink);
  background: #fff5f8;
  transform: translateY(-1px);
}

.nav-item:hover::after {
  width: 60%;
}

.nav-item.active {
  color: var(--bili-pink);
  font-weight: 600;
}

.nav-item.active::after {
  width: 60%;
}

.nav-item.muted {
  color: var(--text-3);
  cursor: default;
}

.nav-item.muted::after {
  display: none;
}

.nav-item.muted:hover {
  color: var(--text-3);
  background: #f6f7f8;
  transform: none;
}

/* ---------- 搜索 ---------- */
.search-wrap {
  position: relative;
  flex: 1;
  max-width: 500px;
  margin: 0 auto;
}

.search-box {
  display: flex;
  align-items: center;
  height: 40px;
  border-radius: 8px;
  background: #f1f2f3;
  padding-left: 14px;
  transition: background 0.2s, box-shadow 0.2s, transform 0.2s;
}

.search-box:focus-within {
  background: #fff;
  box-shadow: 0 0 0 2px rgba(0, 174, 236, 0.35);
  transform: scale(1.015);
}

.search-box input {
  flex: 1;
  border: none;
  background: transparent;
  height: 100%;
  font-size: 14px;
  color: var(--text-1);
}

.search-box input::placeholder {
  color: var(--text-3);
}

.search-btn {
  width: 48px;
  height: 34px;
  margin-right: 3px;
  border-radius: 6px;
  background: var(--bili-blue);
  display: flex;
  align-items: center;
  justify-content: center;
  transition: background 0.2s, transform 0.15s;
}

.search-btn:hover {
  background: var(--bili-blue-hover);
}

.search-btn:active {
  transform: scale(0.94);
}

.suggest {
  position: absolute;
  top: 46px;
  left: 0;
  right: 0;
  background: #fff;
  border-radius: 8px;
  box-shadow: 0 6px 20px rgba(0, 0, 0, 0.14);
  padding: 6px 0;
  max-height: 380px;
  overflow: auto;
  z-index: 20;
}

.suggest li {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 14px;
  cursor: pointer;
  font-size: 14px;
  color: var(--text-2);
  transition: background 0.15s, padding-left 0.15s;
}

.suggest li:hover {
  background: #f4f5f7;
  color: var(--bili-blue);
  padding-left: 18px;
}

.suggest .dot {
  font-size: 12px;
  opacity: 0.6;
}

/* ---------- 右侧 ---------- */
.right {
  display: flex;
  align-items: center;
  gap: 6px;
  flex-shrink: 0;
}

.icon-btn {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 0 8px;
  height: 44px;
  border-radius: 6px;
  color: var(--text-2);
  transition: background 0.2s, color 0.2s;
}

.icon-btn .ico {
  font-size: 16px;
  line-height: 1;
  transition: transform 0.3s cubic-bezier(0.34, 1.56, 0.64, 1);
}

.icon-btn:hover {
  background: #f4f5f7;
  color: var(--bili-pink);
}

.icon-btn:hover .ico {
  transform: translateY(-3px) scale(1.15) rotate(-6deg);
}

.icon-btn .icon-text {
  font-size: 12px;
  margin-top: 2px;
  white-space: nowrap;
}

.vip-btn {
  height: 32px;
  padding: 0 12px;
  border-radius: 6px;
  background: linear-gradient(90deg, #ff7fa6, #ff5c8d);
  color: #fff;
  font-size: 13px;
  font-weight: 600;
  transition: transform 0.2s, box-shadow 0.2s, filter 0.2s;
}

.vip-btn:hover {
  transform: translateY(-1px);
  box-shadow: 0 4px 12px rgba(255, 92, 141, 0.4);
  filter: brightness(1.06);
}

/* ---------- 头像 ---------- */
.avatar-wrap {
  position: relative;
  padding: 4px 0;
}

.avatar-hit {
  cursor: pointer;
}

.avatar.guest {
  width: 38px;
  height: 38px;
  border-radius: 50%;
  background: #f1f2f3;
  color: var(--text-3);
  display: inline-flex;
  align-items: center;
  justify-content: center;
  font-size: 18px;
  transition: transform 0.25s cubic-bezier(0.34, 1.56, 0.64, 1), background 0.2s;
}

.avatar.guest:hover {
  transform: scale(1.14);
  background: #ffe9f0;
}

.hover-card {
  /* 现在由 Element Plus 的 Popover 负责定位，这里只是占位保留 */
  display: block;
}

.user-menu {
  position: absolute;
  top: 52px;
  right: 0;
  width: 236px;
  background: #fff;
  border-radius: 10px;
  box-shadow: 0 6px 24px rgba(0, 0, 0, 0.14);
  overflow: hidden;
  z-index: 60;
}

.user-menu-head {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 14px;
  background: linear-gradient(120deg, #fff0f5, #eef8ff);
}

.head-text {
  flex: 1;
  min-width: 0;
}

.user-menu-head .name {
  font-weight: 600;
  font-size: 14px;
}

.user-menu-head .sub {
  font-size: 11px;
  color: var(--text-3);
  margin-bottom: 6px;
}

.mini-bar {
  height: 4px;
  border-radius: 999px;
  background: #fff;
  overflow: hidden;
}

.mini-fill {
  height: 100%;
  background: linear-gradient(90deg, #ffd0e0, #fb7299);
  border-radius: 999px;
  transition: width 0.6s ease;
}

.mini-text {
  font-size: 10px;
  color: var(--text-3);
  margin-top: 3px;
}

.user-menu-body {
  display: flex;
  flex-direction: column;
  padding: 6px 0;
}

.user-menu-body button {
  text-align: left;
  padding: 9px 16px;
  font-size: 14px;
  color: var(--text-2);
  transition: background 0.15s, padding-left 0.15s, color 0.15s;
}

.user-menu-body button:hover {
  background: #f4f5f7;
  color: var(--bili-pink);
  padding-left: 20px;
}

/* ---------- 经验飘字 ---------- */
.float-layer {
  position: absolute;
  left: 50%;
  top: 44px;
  transform: translateX(-50%);
  pointer-events: none;
  z-index: 70;
}

.exp-float {
  position: absolute;
  left: 50%;
  transform: translateX(-50%);
  white-space: nowrap;
  font-size: 12px;
  font-weight: 700;
  color: var(--bili-pink);
  background: rgba(255, 255, 255, 0.96);
  border: 1px solid #ffd7e3;
  border-radius: 999px;
  padding: 2px 10px;
  box-shadow: 0 4px 12px rgba(251, 114, 153, 0.25);
}

.float-enter-active {
  animation: floatUp 1.6s ease-out forwards;
}

.float-leave-active {
  transition: opacity 0.2s;
  opacity: 0;
}

@keyframes floatUp {
  0% {
    opacity: 0;
    transform: translate(-50%, 6px) scale(0.8);
  }
  25% {
    opacity: 1;
    transform: translate(-50%, -6px) scale(1.05);
  }
  100% {
    opacity: 0;
    transform: translate(-50%, -34px) scale(1);
  }
}

/* ---------- 升级光晕 ---------- */
.levelup-glow {
  position: fixed;
  inset: 0;
  pointer-events: none;
  background: radial-gradient(circle at 88% 3%, rgba(251, 114, 153, 0.35), transparent 32%);
  animation: glow 2.6s ease-out forwards;
}

@keyframes glow {
  0% {
    opacity: 0;
  }
  20% {
    opacity: 1;
  }
  100% {
    opacity: 0;
  }
}

.upload-btn {
  height: 36px;
  padding: 0 18px;
  border-radius: 8px;
  background: var(--bili-pink);
  color: #fff;
  font-size: 14px;
  font-weight: 500;
  transition: background 0.2s, transform 0.2s, box-shadow 0.2s;
}

.upload-btn:hover {
  background: var(--bili-pink-hover);
  transform: translateY(-1px);
  box-shadow: 0 5px 14px rgba(251, 114, 153, 0.4);
}

.upload-btn:active {
  transform: scale(0.96);
}

/* ---------- 过渡 ---------- */
.fade-enter-active,
.fade-leave-active {
  transition: opacity 0.16s;
}

.fade-enter-from,
.fade-leave-to {
  opacity: 0;
}

.card-enter-active {
  transition: opacity 0.2s, transform 0.2s;
}

.card-leave-active {
  transition: opacity 0.14s, transform 0.14s;
}

.card-enter-from,
.card-leave-to {
  opacity: 0;
  transform: translateY(-6px);
}

@media (max-width: 1100px) {
  .nav,
  .icon-btn .icon-text {
    display: none;
  }
}
</style>
