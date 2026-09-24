<script setup>
import { computed, ref } from 'vue'
import { useRouter } from 'vue-router'
import UserAvatar from './UserAvatar.vue'
import { useUserStore } from '../stores/user'
import { formatCount } from '../utils/format'

/**
 * 鼠标移到头像上弹出来的「个人信息卡」。
 * 里面有：放大版头像、昵称、等级、经验进度条、勋章、关注/粉丝/获赞、签到按钮。
 */
const props = defineProps({
  user: { type: Object, required: true }
})

const emit = defineEmits(['close'])
const router = useRouter()
const store = useUserStore()
const checkinLoading = ref(false)

const expPercent = computed(() => {
  const u = props.user
  if (!u?.expMax) return 0
  return Math.min(100, Math.round((u.exp / u.expMax) * 1000) / 10)
})

const expLeft = computed(() => Math.max(0, (props.user.expMax || 0) - (props.user.exp || 0)))

async function doCheckin() {
  if (checkinLoading.value) return
  checkinLoading.value = true
  try {
    await store.checkin()
  } finally {
    checkinLoading.value = false
  }
}

function goSpace() {
  emit('close')
  const id = store.user?.id
  if (!id) {
    store.showToast('请先登录哦~')
    store.openLogin()
    return
  }
  router.push({ name: 'space', params: { id } })
}
</script>

<template>
  <div class="user-card">
    <div class="top">
      <div class="avatar-wrap">
        <UserAvatar
          :face="user.face"
          :face-url="user.faceUrl"
          :size="86"
          :level="user.level"
          ring
          hover-zoom
        />
      </div>
      <div class="who">
        <div class="name-row">
          <span class="name ellipsis" :title="user.name">{{ user.name }}</span>
          <span v-if="user.vip" class="vip">大会员</span>
        </div>
        <div class="sign ellipsis-2">{{ user.sign || '这个人很懒，什么都没写~' }}</div>
        <div class="badges">
          <span class="medal"><AiIcon><Medal /></AiIcon> {{ user.medal || '见习会员' }}</span>
          <span class="join">加入 {{ user.joinDays }} 天</span>
        </div>
      </div>
    </div>

    <!-- 经验进度：用自研的 AiProgress，填充色走品牌渐变 -->
    <div class="exp-block">
      <div class="exp-head">
        <span class="lv">Lv{{ user.level }}</span>
        <span class="exp-text">{{ user.exp }} / {{ user.expMax }} 经验</span>
        <span class="exp-left">还差 {{ expLeft }} 升级</span>
      </div>
      <AiProgress
        :percentage="expPercent"
        :stroke-width="7"
        :show-text="false"
        class="exp-progress"
      />
    </div>

    <!-- 数据统计 -->
    <div class="stats">
      <div class="stat">
        <b>{{ formatCount(user.following) }}</b>
        <span>关注</span>
      </div>
      <div class="stat">
        <b>{{ formatCount(user.followers) }}</b>
        <span>粉丝</span>
      </div>
      <div class="stat">
        <b>{{ formatCount(user.likes) }}</b>
        <span>获赞</span>
      </div>
      <div class="stat">
        <b>{{ user.coins }}</b>
        <span>硬币</span>
      </div>
    </div>

    <div class="actions">
      <button class="btn btn-primary btn-round act" @click="store.openProfile(); emit('close')">
        <AiIcon><Edit /></AiIcon> 编辑资料
      </button>
      <button class="btn btn-round act" @click="goSpace"><AiIcon><HomeFilled /></AiIcon> 个人空间</button>
      <button
        class="btn btn-round act checkin"
        :class="{ done: user.checkedToday }"
        :disabled="user.checkedToday || checkinLoading"
        @click="doCheckin"
      >
        <AiIcon><CircleCheck v-if="user.checkedToday" /><Calendar v-else /></AiIcon>
        {{ user.checkedToday ? '今日已签到' : checkinLoading ? '签到中...' : '每日签到 +10' }}
      </button>
    </div>
  </div>
</template>

<style scoped>
.user-card {
  width: 330px;
  background: #fff;
  border-radius: 12px;
  padding: 16px;
  box-shadow: 0 10px 34px rgba(0, 0, 0, 0.16);
  animation: cardIn 0.22s cubic-bezier(0.34, 1.4, 0.64, 1);
}

@keyframes cardIn {
  from {
    opacity: 0;
    transform: translateY(-8px) scale(0.97);
  }
}

/* 顶部：放大头像 + 昵称 */
.top {
  display: flex;
  gap: 14px;
  align-items: flex-start;
  padding-bottom: 14px;
  background: radial-gradient(circle at 12% 0%, var(--brand-50), transparent 60%);
}

.avatar-wrap {
  padding: 3px;
}

.who {
  flex: 1;
  min-width: 0;
  padding-top: 4px;
}

.name-row {
  display: flex;
  align-items: center;
  gap: 6px;
}

.name {
  font-size: 17px;
  font-weight: 700;
  max-width: 150px;
}

.vip {
  font-size: 11px;
  color: #fff;
  background: var(--grad-brand);
  padding: 1px 6px;
  border-radius: 4px;
  white-space: nowrap;
}

.sign {
  margin: 6px 0 8px;
  font-size: 12px;
  line-height: 18px;
  color: var(--text-2);
  display: -webkit-box;
  -webkit-line-clamp: 2;
  line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.badges {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}

.medal {
  font-size: 11px;
  color: var(--gold-600);
  background: #fff7e6;
  padding: 1px 6px;
  border-radius: 4px;
}

.join {
  font-size: 11px;
  color: var(--text-3);
}

/* 经验条 */
.exp-block {
  margin-bottom: 14px;
}

.exp-head {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 11px;
  color: var(--text-3);
  margin-bottom: 6px;
}

.lv {
  color: #fff;
  background: var(--grad-level);
  border-radius: 4px;
  padding: 0 6px;
  font-weight: 700;
  font-size: 11px;
  line-height: 16px;
}

.exp-text {
  color: var(--text-2);
}

.exp-left {
  margin-left: auto;
}

.exp-progress {
  margin-top: 2px;
}

/* 经验条：轨道压成很浅的灰，填充用横向品牌渐变。
   注意选择器要对准 AiProgress 自己的类名（.ai-progress-*），
   以前这里是 .el-progress-bar__*，换组件之后就一直是空转的死样式。 */
.exp-progress :deep(.ai-progress-track) {
  background: var(--surface-sunken);
  border-radius: 999px;
}

.exp-progress :deep(.ai-progress-fill) {
  background: var(--grad-fill);
  border-radius: 999px;
}

/* 统计 */
.stats {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 6px;
  padding: 12px 0;
  border-top: 1px solid var(--surface-sunken);
  border-bottom: 1px solid var(--surface-sunken);
  margin-bottom: 12px;
}

.stat {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 2px;
  border-radius: 8px;
  padding: 4px 0;
  transition: background 0.2s, transform 0.2s;
  cursor: default;
}

.stat:hover {
  background: var(--brand-50);
  transform: translateY(-2px);
}

.stat b {
  font-size: 15px;
}

.stat span {
  font-size: 11px;
  color: var(--text-3);
}

/* 按钮 */
.actions {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}

.act {
  height: 30px;
  padding: 0 12px;
  font-size: 12px;
  flex: 1;
}

.act.checkin {
  flex-basis: 100%;
}

.act.checkin.done {
  background: var(--surface-sunken);
  color: var(--text-3);
  cursor: default;
}

.ellipsis-2 {
  display: -webkit-box;
  -webkit-line-clamp: 2;
  line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}
</style>
