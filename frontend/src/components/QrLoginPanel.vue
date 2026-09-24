<script setup>
/**
 * 二维码登录面板。
 *
 * 真实流程：
 *   1. 桌面端 POST /api/auth/qr/create 拿到 qrId + 二维码图片（zxing 生成，手机真的能扫）；
 *   2. 桌面端每 1.5 秒轮询 GET /api/auth/qr/poll?qrId=xxx；
 *   3. 手机扫码后打开 /#/qr/<qrId> 确认页（或用下面的「模拟手机」按钮），状态变成 SCANNED；
 *   4. 手机点确认 → CONFIRMED，轮询拿回 token，前端直接登录。
 * 状态：WAITING（待扫描）/ SCANNED（已扫描）/ CONFIRMED（已确认）/ EXPIRED（已过期）/ CANCELED（已取消）
 */
import { onMounted, onUnmounted, ref } from 'vue'
import { api, setToken } from '../api'

const emit = defineEmits(['success', 'status'])

/** 模拟手机端只在开发模式出现：标准模式请用真实手机扫码 */
const isDev = import.meta.env.DEV

const qrId = ref('')
const image = ref('')
const scanUrl = ref('')
const status = ref('WAITING')
const message = ref('正在生成二维码…')
const loading = ref(true)
const leftSeconds = ref(0)
const showSimulate = ref(false)
const account = ref('bili_demo')
const password = ref('bili123456')
const confirmTip = ref('')
const confirming = ref(false)

let pollTimer = null
let countTimer = null

const STATUS_TEXT = {
  WAITING: '请使用「a哩a哩客户端」扫码登录',
  SCANNED: '已扫码，请在手机上点击确认',
  CONFIRMED: '已确认，正在登录…',
  EXPIRED: '二维码已过期，点击刷新',
  CANCELED: '本次登录已取消'
}

async function create() {
  loading.value = true
  status.value = 'WAITING'
  message.value = '正在生成二维码…'
  confirmTip.value = ''
  stopPoll()
  try {
    const res = await api.auth.qrCreate()
    qrId.value = res.qrId
    image.value = res.image
    scanUrl.value = res.scanUrl
    leftSeconds.value = res.expiresIn || 120
    message.value = res.message || STATUS_TEXT.WAITING
    startPoll()
    startCountdown()
  } catch (e) {
    message.value = '二维码生成失败，请点击刷新重试'
    status.value = 'EXPIRED'
  } finally {
    loading.value = false
  }
}

function startPoll() {
  stopPoll()
  pollTimer = setInterval(async () => {
    if (!qrId.value) return
    try {
      const res = await api.auth.qrPoll(qrId.value)
      status.value = res.status
      message.value = res.message || STATUS_TEXT[res.status] || ''
      emit('status', res)
      if (res.leftSeconds != null) leftSeconds.value = Math.max(0, res.leftSeconds)
      if (res.status === 'CONFIRMED' && res.token) {
        stopPoll()
        await finish(res.token)
      } else if (res.status === 'EXPIRED' || res.status === 'CANCELED') {
        stopPoll()
      }
    } catch (e) {
      /* 网络抖动就继续轮询 */
    }
  }, 1500)
}

/** 轮询拿到 token 后，拉一次用户信息再交给 store */
async function finish(token) {
  setToken(token)
  let user = null
  try {
    const me = await api.me()
    user = me?.user || null
  } catch (e) {
    /* 忽略 */
  }
  emit('success', { token, user })
}

function stopPoll() {
  clearInterval(pollTimer)
  pollTimer = null
}

function startCountdown() {
  clearInterval(countTimer)
  countTimer = setInterval(() => {
    leftSeconds.value -= 1
    if (leftSeconds.value <= 0) {
      clearInterval(countTimer)
      if (status.value === 'WAITING' || status.value === 'SCANNED') {
        status.value = 'EXPIRED'
        message.value = STATUS_TEXT.EXPIRED
      }
    }
  }, 1000)
}

/** 模拟手机扫码（真实场景是手机摄像头） */
async function simulateScan() {
  if (!qrId.value) return
  const res = await api.auth.qrScan(qrId.value)
  status.value = res.status
  message.value = res.message
  showSimulate.value = true
}

/** 模拟手机确认登录 */
async function simulateConfirm() {
  confirming.value = true
  confirmTip.value = ''
  try {
    const res = await api.auth.qrConfirm({
      qrId: qrId.value,
      account: account.value,
      password: password.value
    })
    confirmTip.value = res.message || ''
    if (res.success) {
      status.value = 'CONFIRMED'
      // 轮询会拿到 token，这里主动触发一次更快
      const poll = await api.auth.qrPoll(qrId.value)
      if (poll.status === 'CONFIRMED' && poll.token) {
        stopPoll()
        await finish(poll.token)
      }
    }
  } catch (e) {
    confirmTip.value = '确认失败，请重试'
  } finally {
    confirming.value = false
  }
}

/** 在当前窗口打开手机端确认页（也可以用手机扫这个二维码打开） */
function openPhonePage() {
  if (!qrId.value) return
  window.open(`#/qr/${qrId.value}`, '_blank')
}

onMounted(create)
onUnmounted(() => {
  stopPoll()
  clearInterval(countTimer)
})

defineExpose({ refresh: create })
</script>

<template>
  <div class="qr-panel">
    <div class="qr-box">
      <img v-if="image" :src="image" alt="登录二维码" class="qr-img" />
      <div v-else class="qr-skeleton skeleton"></div>

      <!-- 状态遮罩 -->
      <div v-if="status === 'EXPIRED' || status === 'CANCELED'" class="qr-mask">
        <p>{{ status === 'EXPIRED' ? '二维码已过期' : '已取消登录' }}</p>
        <button class="btn btn-primary btn-round" @click="create">点击刷新</button>
      </div>
      <div v-else-if="status === 'SCANNED'" class="qr-mask scanned">
        <p><AiIcon><Cellphone /></AiIcon> 已扫描</p>
        <span>请在手机上确认登录</span>
      </div>
      <div v-else-if="status === 'CONFIRMED'" class="qr-mask scanned">
        <p><AiIcon><CircleCheck /></AiIcon> 已确认</p>
        <span>正在跳转…</span>
      </div>
      <span v-if="loading" class="qr-loading">生成中…</span>
    </div>

    <p class="qr-tip">{{ message }}</p>
    <p class="qr-sub">
      有效期剩余 {{ Math.max(0, leftSeconds) }} 秒
      <button class="link" @click="create">换一张</button>
    </p>

    <div class="qr-actions">
      <button v-if="isDev" class="btn btn-ghost btn-round" @click="simulateScan">
        <AiIcon><Cellphone /></AiIcon> 模拟手机扫码
      </button>
      <button class="link" @click="openPhonePage">在新窗口打开确认页</button>
    </div>

    <!-- 模拟手机端：开发模式下没有手机也能走完整流程 -->
    <div v-if="isDev && showSimulate && status !== 'CONFIRMED'" class="simulate">
      <p class="sim-title">模拟手机端确认（真实场景是手机 App）</p>
      <input v-model="account" type="text" placeholder="账号 / 邮箱" />
      <input v-model="password" type="password" placeholder="密码" @keyup.enter="simulateConfirm" />
      <button class="btn btn-primary" :disabled="confirming" @click="simulateConfirm">
        {{ confirming ? '确认中…' : '确认登录' }}
      </button>
      <p v-if="confirmTip" class="sim-tip">{{ confirmTip }}</p>
      <p class="sim-hint">测试账号：bili_demo / bili123456</p>
    </div>
  </div>
</template>

<style scoped>
.qr-panel {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
}

.qr-box {
  position: relative;
  width: 200px;
  height: 200px;
  border: 1px solid var(--line);
  border-radius: 10px;
  overflow: hidden;
  background: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
}

.qr-img {
  width: 100%;
  height: 100%;
  object-fit: contain;
  display: block;
}

.qr-skeleton {
  width: 100%;
  height: 100%;
}

.qr-mask {
  position: absolute;
  inset: 0;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 10px;
  background: rgba(255, 255, 255, 0.94);
  color: var(--text-2);
  font-size: 13px;
}

.qr-mask p {
  margin: 0;
  font-size: 15px;
  font-weight: 600;
  color: var(--text-1);
}

.qr-mask.scanned {
  background: rgba(255, 255, 255, 0.9);
}

.qr-loading {
  position: absolute;
  bottom: 8px;
  font-size: 12px;
  color: var(--text-3);
}

.qr-tip {
  margin: 4px 0 0;
  font-size: 13px;
  color: var(--text-1);
  text-align: center;
}

.qr-sub {
  margin: 0;
  font-size: 12px;
  color: var(--text-3);
}

.link {
  color: var(--bili-blue);
  font-size: 12px;
  padding: 0 2px;
  background: none;
  border: none;
}

.link:hover {
  text-decoration: underline;
}

.qr-actions {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-top: 4px;
  flex-wrap: wrap;
  justify-content: center;
}

.simulate {
  margin-top: 10px;
  width: 100%;
  max-width: 280px;
  display: flex;
  flex-direction: column;
  gap: 8px;
  padding: 12px;
  border: 1px dashed var(--line);
  border-radius: 10px;
  background: var(--surface-2);
}

.sim-title {
  margin: 0;
  font-size: 12px;
  color: var(--text-2);
}

.simulate input {
  height: 36px;
  padding: 0 10px;
  border: 1px solid var(--line);
  border-radius: 8px;
}

.sim-tip,
.sim-hint {
  margin: 0;
  font-size: 12px;
  color: var(--text-3);
}

@media (max-width: 480px) {
  .qr-box {
    width: 168px;
    height: 168px;
  }
}
</style>
