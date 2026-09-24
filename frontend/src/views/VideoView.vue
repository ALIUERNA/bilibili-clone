<script setup>
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { api } from '../api'
import PlayerView from '../components/PlayerView.vue'
import VideoCard from '../components/VideoCard.vue'
import CommentItem from '../components/CommentItem.vue'
import UserAvatar from '../components/UserAvatar.vue'
import { formatCount, formatDate } from '../utils/format'
import { useUserStore } from '../stores/user'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

const loading = ref(true)
const notFound = ref(false)
const video = ref(null)
const related = ref([])
const danmakuList = ref([])
const comments = ref([])
const commentTotal = ref(0)
const commentText = ref('')
const commentSort = ref('hot')
const activeTab = ref('intro')

const actions = ref({
  likes: 0,
  coins: 0,
  favorites: 0,
  shares: 0,
  liked: false,
  coined: false,
  favored: false,
  followed: false
})

const upFans = ref(0)
/** 播放进度上报节流（每 15 秒写一次数据库，个人中心的「继续观看」用的就是它） */
let lastReportAt = 0

/** 播放器每 5 秒会 emit 一次 progress，这里节流后写回后端 */
function onProgress(sec) {
  const now = Date.now()
  if (now - lastReportAt < 15000 || !video.value?.id) return
  lastReportAt = now
  api.reportProgress(video.value.id, Math.round(sec)).catch(() => {})
}

async function load(id) {
  loading.value = true
  notFound.value = false
  activeTab.value = 'intro'
  try {
    const detail = await api.video(id)
    // 接口 200 但返回空数据（视频被删/数据异常）也按「视频不见了」处理
    if (!detail || !detail.id) {
      throw new Error('EMPTY_VIDEO')
    }
    video.value = detail
    actions.value = {
      likes: detail.likes,
      coins: detail.coins,
      favorites: detail.favorites,
      shares: detail.shares,
      liked: detail.liked,
      coined: detail.coined,
      favored: detail.favored,
      followed: detail.followed
    }
    danmakuList.value = detail.danmakuList || []
    if (!detail.followed) {
      const space = await api.space(detail.upId).catch(() => null)
      upFans.value = space?.up?.fans || 0
    }
    api.addView(id).catch(() => {})
    lastReportAt = 0

    const [rel, cmt] = await Promise.all([
      api.related(id, 14).catch(() => []),
      api.comments(id, { page: 1, size: 20 }).catch(() => ({ items: [], total: 0 }))
    ])
    related.value = rel || []
    comments.value = cmt?.items || []
    commentTotal.value = cmt?.total || 0
  } catch (e) {
    notFound.value = true
  } finally {
    loading.value = false
  }
}

onMounted(() => load(route.params.id))
watch(() => route.params.id, (id) => id && load(id))

async function doAction(type) {
  if (!userStore.requireLogin()) return
  try {
    const res = await api.action(video.value.id, type)
    actions.value.likes = res.likes
    actions.value.coins = res.coins
    actions.value.favorites = res.favorites
    actions.value.shares = res.shares
    actions.value.liked = res.liked
    actions.value.coined = res.coined
    actions.value.favored = res.favored
    // 互动会加经验，后端把最新等级经验一起返回了
    userStore.applyExp(res)
    if (type === 'follow') {
      actions.value.followed = res.followed
      upFans.value += res.followed ? 1 : -1
      userStore.showToast(res.followed ? '关注成功，感谢支持~' : '已取消关注')
      return
    }
    if (type === 'share') {
      userStore.showToast('链接已复制')
      return
    }
    userStore.showToast(
      type === 'like' ? (res.liked ? '点赞成功' : '已取消点赞') :
      type === 'coin' ? (res.coined ? '投币成功' : '已退回硬币') :
      res.favored ? '已收藏到默认收藏夹' : '已取消收藏'
    )
  } catch (e) {
    userStore.showToast('操作失败，请稍后再试')
  }
}

/** 一键三连成功时炸一圈小图标，纯装饰但很有「爽感」 */
const particles = ref([])

function burst() {
  const particleIcons = ['StarFilled', 'Coin', 'Pointer', 'MagicStick']
  particles.value = Array.from({ length: 14 }, (_, i) => {
    const angle = (Math.PI * 2 * i) / 14 + Math.random() * 0.3
    const dist = 46 + Math.random() * 46
    return {
      id: i + Math.random(),
      x: Math.cos(angle) * dist,
      y: Math.sin(angle) * dist,
      icon: particleIcons[i % particleIcons.length]
    }
  })
  setTimeout(() => {
    particles.value = []
  }, 900)
}

async function triple() {
  if (!userStore.requireLogin()) return
  if (!actions.value.liked) await doAction('like')
  if (!actions.value.coined) await doAction('coin')
  if (!actions.value.favored) await doAction('fav')
  userStore.showToast('一键三连成功，感谢支持！')
  burst()
}

async function sendDanmaku(payload) {
  if (!payload) return
  if (!userStore.requireLogin()) return
  try {
    const res = await api.sendDanmaku(video.value.id, payload)
    if (res?.danmaku) {
      danmakuList.value = [...danmakuList.value, res.danmaku]
    }
    userStore.applyExp(res)   // 发弹幕 +3 经验
    userStore.showToast('弹幕已发送')
  } catch (e) {
    userStore.showToast('弹幕发送失败')
  }
}

async function submitComment() {
  if (!userStore.requireLogin()) return
  const content = commentText.value.trim()
  if (!content) {
    userStore.showToast('说点什么再发送吧~')
    return
  }
  try {
    const res = await api.addComment(video.value.id, {
      content,
      user: userStore.user.name,
      face: userStore.user.face
    })
    if (res?.comment) {
      comments.value = [res.comment, ...comments.value]
      commentTotal.value += 1
      commentText.value = ''
    }
    userStore.applyExp(res)   // 发评论 +5 经验
    userStore.showToast('评论发布成功')
  } catch (e) {
    userStore.showToast('评论失败，请稍后再试')
  }
}

/** 评论点赞：评论数据在父组件这里，改动它不会触发「修改 prop」的警告 */
function onCommentLike(comment) {
  comment.liked = !comment.liked
  comment.likes += comment.liked ? 1 : -1
}

const viewText = computed(() => formatCount(video.value?.views || 0))
const danmakuText = computed(() => formatCount(video.value?.danmakus || danmakuList.value.length))
const fansText = computed(() => formatCount(upFans.value))

function goUp() {
  if (video.value) router.push({ name: 'space', params: { id: video.value.upId } })
}
</script>

<template>
  <div class="video-page container-wide fade-up">
    <!-- 加载中 -->
    <div v-if="loading" class="loading-wrap">
      <div class="skeleton skeleton-player"></div>
      <div class="skeleton skeleton-line"></div>
      <div class="skeleton skeleton-line short"></div>
    </div>

    <!-- 视频不存在 / 加载失败 / 接口返回空 -->
    <div v-else-if="notFound || !video || !video.id" class="empty-wrap">
      <el-empty description="视频已经不见了OVO">
        <p class="empty-sub">可能已经被 UP 主删除，或者链接不对</p>
        <button class="btn btn-primary btn-round" @click="$router.push('/')">回首页看看</button>
      </el-empty>
    </div>

    <div v-else class="layout">
      <!-- ============ 左列 ============ -->
      <div class="main-col">
        <PlayerView
          :video="video"
          :danmaku-list="danmakuList"
          @send-danmaku="sendDanmaku"
          @progress="onProgress"
        />

        <!-- 封面帧 & 媒体状态 -->
        <div v-if="video.coverUrl" class="cover-strip">
          <img :src="video.coverUrl" class="cover-thumb" alt="视频封面帧" />
          <div class="cover-meta">
            <span class="tag tag-pink">封面帧</span>
            <span class="muted">
              {{ video.playable ? '在线播放 · 支持拖动进度' : '该稿件暂无视频文件，展示封面图' }}
            </span>
          </div>
        </div>

        <!-- 标题与统计 -->
        <h1 class="v-title">{{ video.title }}</h1>
        <div class="v-stats">
          <span><AiIcon><VideoPlay /></AiIcon> {{ viewText }} 播放</span>
          <span><AiIcon><ChatDotRound /></AiIcon> {{ danmakuText }} 弹幕</span>
          <span><AiIcon><Clock /></AiIcon> 发布于 {{ formatDate(video.pubTime) }}</span>
          <span class="bvid">{{ video.bvid }}</span>
          <span class="tag tag-pink">{{ video.category }}</span>
        </div>

        <!-- 一键三连 + UP 主 -->
        <div class="action-bar">
          <div class="triple">
            <button class="act-btn" :class="{ on: actions.liked }" @click="doAction('like')">
              <span class="act-ico"><AiIcon><Pointer /></AiIcon></span>
              <span class="act-num">{{ formatCount(actions.likes) }}</span>
            </button>
            <button class="act-btn" :class="{ on: actions.coined }" @click="doAction('coin')">
              <span class="act-ico"><AiIcon><Coin /></AiIcon></span>
              <span class="act-num">{{ formatCount(actions.coins) }}</span>
            </button>
            <button class="act-btn" :class="{ on: actions.favored }" @click="doAction('fav')">
              <span class="act-ico"><AiIcon><Star /></AiIcon></span>
              <span class="act-num">{{ formatCount(actions.favorites) }}</span>
            </button>
            <button class="act-btn" @click="doAction('share')">
              <span class="act-ico"><AiIcon><Share /></AiIcon></span>
              <span class="act-num">{{ formatCount(actions.shares) }}</span>
            </button>
            <span class="triple-wrap">
              <button class="triple-btn" @click="triple">一键三连</button>
              <span class="particles">
                <AiIcon
                  v-for="p in particles"
                  :key="p.id"
                  class="particle"
                  :style="{ '--x': p.x + 'px', '--y': p.y + 'px' }"
                ><component :is="p.icon" /></AiIcon>
              </span>
            </span>
          </div>

          <div class="up-row">
            <div class="up-face" @click="goUp">{{ video.upFace }}</div>
            <div class="up-meta" @click="goUp">
              <div class="up-name">{{ video.upName }}</div>
              <div class="up-fans">{{ fansText }} 粉丝</div>
            </div>
            <button class="follow-btn" :class="{ on: actions.followed }" @click="doAction('follow')">
              {{ actions.followed ? '已关注' : '+ 关注' }}
            </button>
          </div>
        </div>

        <!-- 简介 / 标签 -->
        <div class="intro-card">
          <div class="intro-text">{{ video.desc }}</div>
          <div class="tags">
            <span v-for="t in video.tags" :key="t" class="tag tag-blue" @click="$router.push({ name: 'search', query: { keyword: t } })">
              #{{ t }}
            </span>
          </div>
        </div>

        <!-- 评论区 -->
        <section class="comment-section">
          <header class="cmt-head">
            <h3>评论 <span class="cmt-total">{{ formatCount(commentTotal) }}</span></h3>
            <div class="cmt-sort">
              <button :class="{ on: commentSort === 'hot' }" @click="commentSort = 'hot'">最热</button>
              <button :class="{ on: commentSort === 'new' }" @click="commentSort = 'new'">最新</button>
            </div>
          </header>

          <div class="cmt-input">
            <UserAvatar
              v-if="userStore.isLogin"
              :face="userStore.user.face"
              :face-url="userStore.user.faceUrl"
              :size="40"
            />
            <div v-else class="cmt-avatar"><AiIcon :size="20"><UserFilled /></AiIcon></div>
            <div class="cmt-field">
              <textarea
                v-model="commentText"
                rows="2"
                maxlength="300"
                :placeholder="userStore.isLogin ? '发一条友好的评论吧~' : '请先登录后发表评论'"
                @focus="!userStore.isLogin && userStore.openLogin()"
              ></textarea>
              <div class="cmt-actions">
                <button class="btn btn-primary" @click="submitComment">发布</button>
              </div>
            </div>
          </div>

          <CommentItem v-for="c in comments" :key="c.id" :comment="c" @like="onCommentLike" />

          <div v-if="!comments.length" class="no-comment">还没有评论，快来抢沙发吧~</div>
        </section>
      </div>

      <!-- ============ 右列 ============ -->
      <aside class="side-col">
        <div class="side-block">
          <h4 class="side-title">相关推荐</h4>
          <VideoCard v-for="v in related" :key="v.id" :video="v" layout="list" />
        </div>

        <div class="side-block tips">
          <h4 class="side-title">小提示</h4>
          <ul>
            <li>空格键：播放 / 暂停</li>
            <li>左右方向键：快退 / 快进 5 秒</li>
            <li>F：全屏，Esc 退出</li>
            <li>在弹幕框输入内容即可发弹幕</li>
          </ul>
        </div>
      </aside>
    </div>
  </div>
</template>

<style scoped>
.video-page {
  padding-top: 20px;
}

.layout {
  display: grid;
  grid-template-columns: 1fr 360px;
  gap: 28px;
  align-items: start;
}

.main-col {
  min-width: 0;
}

/* 标题与统计 */
.v-title {
  margin: 16px 0 8px;
  font-size: 20px;
  line-height: 30px;
  font-weight: 600;
}

/* 封面帧条：展示 FFmpeg 截出来的真实封面 */
.cover-strip {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-top: 14px;
  padding: 10px 12px;
  background: #fff;
  border-radius: 10px;
  box-shadow: var(--shadow-card);
}

.cover-thumb {
  width: 132px;
  height: 74px;
  object-fit: cover;
  border-radius: 8px;
  display: block;
  background: var(--surface-sunken);
  flex-shrink: 0;
}

.cover-meta {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
  font-size: 13px;
}

.muted {
  color: var(--text-3);
}

@media (max-width: 560px) {
  .cover-thumb {
    width: 108px;
    height: 61px;
  }
}

.v-stats {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 14px;
  font-size: 13px;
  color: var(--text-3);
}

.bvid {
  font-family: Consolas, Monaco, monospace;
  color: var(--text-2);
}

/* 操作栏 */
.action-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  flex-wrap: wrap;
  margin: 16px 0;
  padding: 12px 16px;
  background: #fff;
  border-radius: 8px;
}

.triple {
  display: flex;
  align-items: center;
  gap: 6px;
}

.act-btn {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 6px 12px;
  border-radius: 6px;
  color: var(--text-2);
  font-size: 13px;
  transition: background 0.2s, color 0.2s;
}

.act-btn:hover {
  background: var(--surface-sunken);
  color: var(--bili-blue);
}

.act-btn.on {
  color: var(--bili-pink);
}

.act-ico {
  font-size: 18px;
}

.triple-btn {
  margin-left: 8px;
  height: 32px;
  padding: 0 16px;
  border-radius: 6px;
  background: var(--grad-brand);
  color: #fff;
  font-size: 13px;
  font-weight: 600;
}

.triple-btn:hover {
  filter: brightness(1.05);
  transform: translateY(-1px);
}

.triple-btn:active {
  transform: scale(0.94);
}

/* 三连成功时炸开的小图标 */
.triple-wrap {
  position: relative;
  display: inline-flex;
}

.particles {
  position: absolute;
  inset: 0;
  pointer-events: none;
}

.particle {
  position: absolute;
  left: 50%;
  top: 50%;
  font-size: 14px;
  animation: fly 0.85s cubic-bezier(0.22, 1, 0.36, 1) forwards;
}

@keyframes fly {
  0% {
    opacity: 1;
    transform: translate(-50%, -50%) scale(0.4);
  }
  100% {
    opacity: 0;
    transform: translate(calc(-50% + var(--x)), calc(-50% + var(--y))) scale(1.2);
  }
}

.up-row {
  display: flex;
  align-items: center;
  gap: 10px;
}

.up-face {
  width: 44px;
  height: 44px;
  border-radius: 50%;
  background: var(--surface-sunken);
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 22px;
  cursor: pointer;
  border: 2px solid transparent;
  transition: border-color 0.2s;
}

.up-face:hover {
  border-color: var(--bili-pink);
}

.up-meta {
  cursor: pointer;
}

.up-name {
  font-size: 14px;
  font-weight: 600;
}

.up-name:hover {
  color: var(--bili-pink);
}

.up-fans {
  font-size: 12px;
  color: var(--text-3);
}

.follow-btn {
  height: 32px;
  padding: 0 18px;
  border-radius: 6px;
  background: var(--bili-pink);
  color: #fff;
  font-size: 13px;
}

.follow-btn.on {
  background: var(--surface-sunken);
  color: var(--text-2);
}

.follow-btn:hover {
  filter: brightness(1.04);
}

/* 简介 */
.intro-card {
  background: #fff;
  border-radius: 8px;
  padding: 16px;
  margin-bottom: 20px;
}

.intro-text {
  white-space: pre-line;
  font-size: 14px;
  line-height: 24px;
  color: var(--text-1);
  margin-bottom: 12px;
}

.tags {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}

.tags .tag {
  cursor: pointer;
}

/* 评论区 */
.comment-section {
  background: #fff;
  border-radius: 8px;
  padding: 16px 20px 24px;
}

.cmt-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding-bottom: 12px;
  border-bottom: 1px solid var(--surface-sunken);
}

.cmt-head h3 {
  margin: 0;
  font-size: 18px;
}

.cmt-total {
  font-size: 13px;
  color: var(--text-3);
  font-weight: 400;
  margin-left: 6px;
}

.cmt-sort {
  display: flex;
  gap: 4px;
}

.cmt-sort button {
  padding: 4px 10px;
  border-radius: 6px;
  font-size: 13px;
  color: var(--text-3);
}

.cmt-sort button.on {
  background: var(--brand-50);
  color: var(--bili-pink);
}

.cmt-input {
  display: flex;
  gap: 12px;
  padding: 16px 0;
}

.cmt-avatar {
  width: 40px;
  height: 40px;
  border-radius: 50%;
  background: var(--surface-sunken);
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 20px;
  flex-shrink: 0;
}

.cmt-field {
  flex: 1;
  border: 1px solid var(--line);
  border-radius: 8px;
  overflow: hidden;
  transition: border-color 0.2s;
}

.cmt-field:focus-within {
  border-color: var(--bili-blue);
}

.cmt-field textarea {
  width: 100%;
  border: none;
  padding: 10px 12px;
  resize: vertical;
  font-size: 14px;
  line-height: 22px;
}

.cmt-actions {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 6px 10px;
  background: var(--surface-2);
}

.cmt-emoji {
  font-size: 14px;
  opacity: 0.7;
}

.no-comment {
  padding: 40px 0;
  text-align: center;
  color: var(--text-3);
  font-size: 14px;
}

/* 右列 */
.side-col {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.side-block {
  background: #fff;
  border-radius: 8px;
  padding: 14px 16px;
}

.side-title {
  margin: 0 0 10px;
  font-size: 15px;
}

.tips ul li {
  font-size: 13px;
  color: var(--text-2);
  line-height: 26px;
}

/* 加载与空状态 */
.loading-wrap {
  max-width: 900px;
}

.skeleton-player {
  width: 100%;
  aspect-ratio: 16 / 9;
}

.skeleton-line {
  height: 22px;
  margin-top: 16px;
}

.skeleton-line.short {
  width: 40%;
}

.empty-wrap {
  background: #fff;
  border-radius: 10px;
  padding: 60px 0;
}

.empty-sub {
  margin: -8px 0 16px;
  font-size: 13px;
  color: var(--text-3);
}

@media (max-width: 1100px) {
  .layout {
    grid-template-columns: 1fr;
  }
}
</style>
