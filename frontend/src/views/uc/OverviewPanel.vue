<script setup>
/** 个人中心 - 主页：资料卡 + 数据统计 + 最近观看 */
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { api } from '../../api'
import { useUserStore } from '../../stores/user'
import UserAvatar from '../../components/UserAvatar.vue'
import { formatCount, formatDateTime, rowToVideo } from '../../utils/format'
import VideoCard from '../../components/VideoCard.vue'

const router = useRouter()
const userStore = useUserStore()

const loading = ref(true)
const counts = ref({})
const recent = ref([])
const checkinTip = ref('')

async function load() {
  loading.value = true
  try {
    const res = await api.uc.overview()
    counts.value = res.counts || {}
    recent.value = (res.videos || []).map(rowToVideo).filter(Boolean)
    if (res.user) userStore.setUser(res.user)
  } catch (e) {
    userStore.showToast(e?.friendlyMessage || '加载失败')
  } finally {
    loading.value = false
  }
}

async function checkin() {
  const res = await userStore.checkin()
  checkinTip.value = res?.message || ''
  if (res?.success) load()
}

onMounted(load)
</script>

<template>
  <div class="panel">
    <div class="profile-card">
      <UserAvatar
        :face="userStore.user?.face"
        :face-url="userStore.user?.faceUrl"
        :size="72"
        :level="userStore.user?.level"
      />
      <div class="info">
        <h2>{{ userStore.user?.name }}</h2>
        <p class="sign">{{ userStore.user?.sign || '这个人很懒，什么都没写~' }}</p>
        <div class="exp">
          <div class="exp-bar">
            <div class="exp-fill" :style="{ width: userStore.expPercent + '%' }"></div>
          </div>
          <span class="exp-text">
            Lv{{ userStore.user?.level }} · {{ userStore.user?.exp }}/{{ userStore.user?.expMax }}
            （还差 {{ userStore.expLeft }} 升级）
          </span>
        </div>
      </div>
      <div class="actions">
        <button class="btn btn-primary btn-round" @click="checkin">
          {{ userStore.user?.checkedToday ? '今日已签到' : '每日签到 +10 经验' }}
        </button>
        <button class="btn btn-ghost btn-round" @click="router.push('/user/profile')">编辑资料</button>
      </div>
    </div>

    <p v-if="checkinTip" class="tip">{{ checkinTip }}</p>

    <div class="stats">
      <button class="stat" @click="router.push('/user/history')">
        <b>{{ counts.history ?? 0 }}</b><span>观看历史</span>
      </button>
      <button class="stat" @click="router.push('/user/favorite')">
        <b>{{ counts.favorites ?? 0 }}</b><span>我的收藏</span>
      </button>
      <button class="stat" @click="router.push('/user/follow')">
        <b>{{ counts.following ?? 0 }}</b><span>我的关注</span>
      </button>
      <button class="stat" @click="router.push('/user/upload')">
        <b>{{ counts.uploads ?? 0 }}</b><span>我的投稿</span>
      </button>
      <button class="stat" @click="router.push('/user/message')">
        <b>{{ counts.unread ?? 0 }}</b><span>未读消息</span>
      </button>
      <div class="stat static">
        <b>{{ userStore.user?.coins ?? 0 }}</b><span>硬币</span>
      </div>
    </div>

    <section class="block">
      <header>
        <h3>最近在看的视频</h3>
        <router-link to="/user/history" class="more-link">查看全部 <AiIcon><ArrowRight /></AiIcon></router-link>
      </header>
      <div v-if="loading" class="skeleton sk-line"></div>
      <div v-else-if="recent.length" class="grid">
        <VideoCard v-for="v in recent.slice(0, 8)" :key="v.id" :video="v" :show-up="false" />
      </div>
      <p v-else class="empty">还没有观看记录，去首页挑一个视频看看吧～</p>
    </section>

    <section class="block">
      <header><h3>账号信息</h3></header>
      <ul class="kv">
        <li><span>账号</span><b>{{ userStore.user?.username || '—' }}</b></li>
        <li><span>邮箱</span><b>{{ userStore.user?.email || '未绑定' }}</b></li>
        <li><span>注册时间</span><b>{{ formatDateTime(userStore.user?.createdAt) || '—' }}</b></li>
        <li><span>最后登录</span><b>{{ formatDateTime(userStore.user?.lastLoginAt) || '—' }}</b></li>
        <li><span>硬币 / B币</span><b>{{ formatCount(userStore.user?.coins || 0) }} / {{ formatCount(userStore.user?.bCoins || 0) }}</b></li>
      </ul>
    </section>
  </div>
</template>

<style scoped>
.panel {
  display: flex;
  flex-direction: column;
  gap: 18px;
}

.profile-card {
  display: flex;
  align-items: center;
  gap: 18px;
  padding: 20px 22px;
  border-radius: 12px;
  background: linear-gradient(120deg, var(--brand-50), var(--cyan-50));
  flex-wrap: wrap;
}

.info {
  flex: 1;
  min-width: 200px;
}

.info h2 {
  margin: 0 0 6px;
  font-size: 20px;
}

.sign {
  margin: 0 0 10px;
  font-size: 13px;
  color: var(--text-2);
}

.exp-bar {
  width: 100%;
  max-width: 320px;
  height: 8px;
  border-radius: 999px;
  background: var(--line);
  overflow: hidden;
}

.exp-fill {
  height: 100%;
  border-radius: 999px;
  background: var(--grad-level);
  transition: width 0.4s;
}

.exp-text {
  font-size: 12px;
  color: var(--text-3);
}

.actions {
  display: flex;
  gap: 10px;
  flex-wrap: wrap;
}

.tip {
  margin: 0;
  font-size: 13px;
  color: var(--bili-pink);
}

.stats {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(120px, 1fr));
  gap: 12px;
}

.stat {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 4px;
  padding: 14px 8px;
  border-radius: 10px;
  background: #fff;
  box-shadow: var(--shadow-card);
  transition: transform 0.18s;
}

.stat:not(.static):hover {
  transform: translateY(-2px);
}

.stat b {
  font-size: 20px;
  color: var(--bili-pink);
}

.stat span {
  font-size: 12px;
  color: var(--text-3);
}

.block {
  background: #fff;
  border-radius: 12px;
  padding: 18px 20px;
  box-shadow: var(--shadow-card);
}

.block header {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  margin-bottom: 14px;
}

.block h3 {
  margin: 0;
  font-size: 16px;
}

.more-link {
  font-size: 13px;
  color: var(--bili-blue);
}

.grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(200px, 1fr));
  gap: 16px 14px;
}

.empty {
  margin: 0;
  font-size: 13px;
  color: var(--text-3);
}

.sk-line {
  height: 120px;
}

.kv {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(240px, 1fr));
  gap: 10px 18px;
}

.kv li {
  display: flex;
  justify-content: space-between;
  padding: 8px 0;
  border-bottom: 1px dashed var(--line);
  font-size: 13px;
}

.kv span {
  color: var(--text-3);
}

@media (max-width: 560px) {
  .profile-card {
    padding: 16px;
  }

  .grid {
    grid-template-columns: repeat(auto-fill, minmax(150px, 1fr));
  }
}
</style>
