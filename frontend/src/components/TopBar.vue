<script setup>
import { onUnmounted, ref, watch } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { useDebounceFn } from '@vueuse/core'
import { api } from '../api'
import { useUserStore } from '../stores/user'
import AiLogo from '../ui/AiLogo.vue'
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
  { name: '游戏', to: '/node/game' },
  { name: '直播', to: '/live' },
  { name: '专栏', to: '/column' },
  { name: '社区中心', to: '/community' },
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
  const id = userStore.user?.id
  if (!id) {
    userStore.showToast('请先登录哦~')
    userStore.openLogin()
    return
  }
  router.push({ name: 'space', params: { id } })
}

/** 个人中心各标签页 */
function goUserCenter(tab = 'overview') {
  showUserMenu.value = false
  if (!userStore.requireLogin(`/user/${tab}`)) return
  router.push(`/user/${tab}`)
}

function openProfile() {
  showUserMenu.value = false
  userStore.openProfile()
}

async function logout() {
  showUserMenu.value = false
  await userStore.logout()
  router.push('/')
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
  if (!userStore.requireLogin('/upload')) return
  router.push('/upload')
}

onUnmounted(() => {
  showSuggest.value = false
})
</script>

<template>
  <header class="topbar">
    <div class="topbar-inner">
      <!-- 左侧：品牌标志 + 导航 -->
      <a class="logo" href="#/" title="a哩a哩 (゜-゜)つロ 干杯~">
        <AiLogo class="logo-mark" :size="30" />
        <span class="logo-text">a哩a哩</span>
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
        <span
          v-for="n in [
            { name: '动画', to: '/node/anime' },
            { name: '音乐', to: '/node/music' },
            { name: '科技', to: '/node/tech' },
            { name: '活动', to: '/activity' }
          ]"
          :key="n.to"
          class="nav-item extra"
          @click="$router.push(n.to)"
        >
          {{ n.name }}
        </span>
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
            <AiIcon color="#fff" :size="18"><Search /></AiIcon>
          </button>
        </div>
        <transition name="fade">
          <ul v-if="showSuggest && suggestList.length" class="suggest">
            <li v-for="(s, i) in suggestList" :key="i" @mousedown.prevent="pickSuggest(s)">
              <span class="dot"><AiIcon><Search /></AiIcon></span>
              <span class="ellipsis">{{ s }}</span>
            </li>
          </ul>
        </transition>
      </div>

      <!-- 右侧：用户区 -->
      <div class="right">
        <el-tooltip content="消息中心" placement="bottom" :show-after="400">
          <button class="icon-btn" @click="goUserCenter('message')">
            <span class="ico"><AiIcon><Message /></AiIcon></span><span class="icon-text">消息</span>
          </button>
        </el-tooltip>

        <el-tooltip content="观看历史" placement="bottom" :show-after="400">
          <button class="icon-btn" @click="goUserCenter('history')">
            <span class="ico"><AiIcon><Clock /></AiIcon></span><span class="icon-text">历史</span>
          </button>
        </el-tooltip>

        <el-tooltip content="我的投稿" placement="bottom" :show-after="400">
          <button class="icon-btn" @click="goUserCenter('upload')">
            <span class="ico"><AiIcon><MagicStick /></AiIcon></span><span class="icon-text">创作中心</span>
          </button>
        </el-tooltip>

        <button class="vip-btn" @click="$router.push('/user/settings')">
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
                <span v-else class="avatar guest"><AiIcon :size="20"><User /></AiIcon></span>
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
                <button @click="goUserCenter('overview')">个人中心</button>
                <button @click="goUserCenter('favorite')">我的收藏</button>
                <button @click="goUserCenter('history')">观看历史</button>
                <button @click="goUserCenter('upload')">我的投稿</button>
                <button @click="goUserCenter('settings')">设置</button>
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

        <button v-if="!userStore.isLogin" class="login-btn" @click="$router.push('/login')">登录</button>
        <button v-if="!userStore.isLogin" class="signup-btn" @click="$router.push('/register')">注册</button>

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

/* 悬停时标志轻轻转一下：给品牌一点点"活着"的感觉 */
.logo-mark {
  transition: transform 0.45s var(--ease-spring);
}

.logo:hover .logo-mark {
  transform: rotate(-7deg) scale(1.08);
}

.logo-text {
  font-size: 19px;
  font-weight: 700;
  letter-spacing: 0.5px;
  /* 文字本身用极光渐变，和图形标志的配色呼应 */
  background: var(--grad-brand);
  -webkit-background-clip: text;
  background-clip: text;
  color: transparent;
  -webkit-text-fill-color: transparent;
}

/* 老浏览器 / 不支持 background-clip:text 时退回品牌紫，避免文字消失 */
@supports not ((-webkit-background-clip: text) or (background-clip: text)) {
  .logo-text {
    color: var(--brand-600);
    -webkit-text-fill-color: currentColor;
  }
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
  background: var(--brand-50);
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
  background: var(--surface-0);
  transform: none;
}

/* ---------- 搜索 ---------- */
.search-wrap {
  position: relative;
  flex: 1 1 auto;
  min-width: 0;
  max-width: 500px;
  margin: 0 auto;
}

.search-box {
  display: flex;
  align-items: center;
  height: 40px;
  border-radius: 8px;
  background: var(--surface-sunken);
  padding-left: 14px;
  transition: background 0.2s, box-shadow 0.2s, transform 0.2s;
}

.search-box:focus-within {
  background: #fff;
  box-shadow: 0 0 0 2px rgba(18, 183, 214, 0.35);
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
  border-radius: var(--r-sm);
  /* 搜索是顶栏里唯一的主动作，用品牌极光渐变，
     别让天青（信息色）在这里抢走品牌记忆点 */
  background: var(--grad-brand);
  background-size: 160% 160%;
  background-position: 0% 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: background-position var(--dur-base) var(--ease-out), transform 0.15s,
    box-shadow var(--dur-base);
}

.search-btn:hover {
  background-position: 100% 50%;
  box-shadow: var(--sd-brand);
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
  background: var(--surface-sunken);
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
  background: var(--surface-sunken);
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
  background: linear-gradient(90deg, var(--brand-400), var(--brand-500));
  color: #fff;
  font-size: 13px;
  font-weight: 600;
  transition: transform 0.2s, box-shadow 0.2s, filter 0.2s;
}

.vip-btn:hover {
  transform: translateY(-1px);
  box-shadow: 0 4px 12px rgba(110, 86, 248, 0.4);
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
  background: var(--surface-sunken);
  color: var(--text-3);
  display: inline-flex;
  align-items: center;
  justify-content: center;
  font-size: 18px;
  transition: transform 0.25s cubic-bezier(0.34, 1.56, 0.64, 1), background 0.2s;
}

.avatar.guest:hover {
  transform: scale(1.14);
  background: var(--brand-100);
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
  background: linear-gradient(120deg, var(--brand-50), var(--cyan-50));
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
  background: var(--grad-level);
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
  background: var(--surface-sunken);
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
  border: 1px solid var(--brand-200);
  border-radius: 999px;
  padding: 2px 10px;
  box-shadow: 0 4px 12px rgba(110, 86, 248, 0.25);
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
  background: radial-gradient(circle at 88% 3%, rgba(110, 86, 248, 0.35), transparent 32%);
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
  box-shadow: 0 5px 14px rgba(110, 86, 248, 0.4);
}

.upload-btn:active {
  transform: scale(0.96);
}

/* ---------- 未登录时的登录 / 注册按钮 ---------- */
.login-btn,
.signup-btn {
  height: 34px;
  padding: 0 16px;
  border-radius: 8px;
  font-size: 14px;
  transition: all 0.2s;
}

.login-btn {
  background: var(--bili-pink);
  color: #fff;
}

.login-btn:hover {
  background: var(--bili-pink-hover);
}

.signup-btn {
  border: 1px solid var(--bili-pink);
  color: var(--bili-pink);
  background: #fff;
}

.signup-btn:hover {
  background: var(--brand-50);
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

@media (max-width: 1440px) {
  /* 中等屏幕先收起次要导航，保证搜索框和右侧按钮不被挤出屏幕 */
  .nav-item.extra {
    display: none;
  }
}

@media (max-width: 1280px) {
  .nav {
    display: none;
  }
}

@media (max-width: 1100px) {
  .nav,
  .icon-btn .icon-text {
    display: none;
  }
}

@media (max-width: 720px) {
  .logo-text,
  .signup-btn,
  .vip-btn,
  .icon-btn {
    display: none;
  }

  .topbar-inner {
    padding: 0 12px;
    gap: 8px;
  }

  .search-wrap {
    max-width: none;
  }

  .upload-btn {
    padding: 0 12px;
    flex-shrink: 0;
  }

  .avatar-hit {
    width: 34px;
    height: 34px;
  }
}

@media (max-width: 420px) {
  .search-btn {
    width: 38px;
  }

  .search-box {
    padding-left: 10px;
  }

  .user-menu {
    right: -8px;
    max-width: calc(100vw - 24px);
  }
}
</style>
