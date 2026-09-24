<script setup>
/**
 * 手机端「扫码确认登录」页面。
 * 二维码里写入的就是 /#/qr/<qrId>，手机扫码后打开这个页面：
 *   1. 进入页面自动上报「已扫描」；
 *   2. 已登录用户点「确认登录」即可；未登录则输入账号密码确认；
 *   3. 桌面端轮询到 CONFIRMED 后自动登录。
 */
import { onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { api, setToken } from '../api'
import { useUserStore } from '../stores/user'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

const qrId = ref(String(route.params.qrId || ''))
const status = ref('WAITING')
const message = ref('正在读取二维码状态…')
const loading = ref(false)
const done = ref(false)
const error = ref('')
const form = reactive({ account: '', password: '' })

/** 测试账号提示只在开发模式出现 */
const isDev = import.meta.env.DEV

async function scan() {
  if (!qrId.value) return
  try {
    const res = await api.auth.qrScan(qrId.value)
    status.value = res.status
    message.value = res.message
    const info = status.value === 'EXPIRED' ? res : null
    if (info && !info.success) message.value = info.message
  } catch (e) {
    message.value = '二维码状态获取失败'
  }
}

async function confirm() {
  if (!qrId.value) return
  loading.value = true
  error.value = ''
  try {
    const payload = { qrId: qrId.value }
    if (!userStore.isLogin) {
      if (!form.account || !form.password) {
        error.value = '请输入账号和密码'
        loading.value = false
        return
      }
      payload.account = form.account
      payload.password = form.password
    }
    const res = await api.auth.qrConfirm(payload)
    if (!res.success) {
      error.value = res.message || '确认失败'
      return
    }
    done.value = true
    status.value = 'CONFIRMED'
    message.value = '已确认登录，请回到电脑上查看';
    // 手机上顺便也登录一下，方便继续使用
    if (res.user && !userStore.isLogin) {
      try {
        setToken('')
        userStore.setUser(res.user)
      } catch (e) {
        /* ignore */
      }
    }
  } catch (e) {
    error.value = '网络异常，请重试'
  } finally {
    loading.value = false
  }
}

function cancel() {
  api.auth.qrCancel(qrId.value).finally(() => {
    message.value = '已取消本次登录'
    status.value = 'CANCELED'
  })
}

onMounted(scan)
</script>

<template>
  <div class="qr-confirm">
    <div class="card">
      <div class="phone"><AiIcon :size="46"><Cellphone /></AiIcon></div>
      <h2>扫码确认登录</h2>
      <p class="status" :class="status.toLowerCase()">{{ message }}</p>

      <div v-if="done" class="done">
        <p><AiIcon><CircleCheck /></AiIcon> 电脑端已登录成功</p>
        <button class="btn btn-ghost btn-round" @click="router.push('/')">随便逛逛</button>
      </div>

      <template v-else-if="status === 'WAITING' || status === 'SCANNED'">
        <div v-if="userStore.isLogin" class="who">
          <span class="face">
            <img v-if="userStore.user.faceUrl" :src="userStore.user.faceUrl" alt="头像" />
            <span v-else-if="userStore.user.face">{{ userStore.user.face }}</span>
            <AiIcon v-else><UserFilled /></AiIcon>
          </span>
          <div>
            <div class="name">{{ userStore.user.name }}</div>
            <div class="sub">将使用当前账号在电脑端登录</div>
          </div>
        </div>
        <div v-else class="form">
          <input v-model.trim="form.account" type="text" placeholder="账号 / 邮箱" />
          <input v-model="form.password" type="password" placeholder="密码" @keyup.enter="confirm" />
          <p v-if="isDev" class="hint">测试账号：bili_demo / bili123456（可在设置页修改密码）</p>
        </div>

        <button class="btn btn-primary btn-round big" :disabled="loading" @click="confirm">
          {{ loading ? '确认中…' : '确认登录' }}
        </button>
        <button class="link" @click="cancel">取消登录</button>
      </template>

      <template v-else>
        <p class="hint">二维码已失效，请回到电脑端刷新二维码后重新扫描。</p>
        <button class="btn btn-ghost btn-round" @click="router.push('/login?tab=qr')">去登录页</button>
      </template>

      <p v-if="error" class="error">{{ error }}</p>
    </div>
  </div>
</template>

<style scoped>
.qr-confirm {
  display: flex;
  justify-content: center;
  padding: 40px 16px;
}

.card {
  width: 100%;
  max-width: 380px;
  background: #fff;
  border-radius: 14px;
  box-shadow: 0 12px 40px rgba(0, 0, 0, 0.1);
  padding: 30px 26px;
  text-align: center;
}

.phone {
  font-size: 46px;
}

h2 {
  margin: 8px 0 6px;
  font-size: 20px;
}

.status {
  margin: 0 0 18px;
  font-size: 13px;
  color: var(--text-2);
}

.status.confirmed {
  color: var(--mint-600);
}

.form {
  display: flex;
  flex-direction: column;
  gap: 10px;
  margin-bottom: 16px;
}

.form input {
  height: 42px;
  padding: 0 14px;
  border: 1px solid var(--line);
  border-radius: 8px;
}

.who {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 14px;
  border-radius: 10px;
  background: var(--surface-0);
  margin-bottom: 16px;
  text-align: left;
}

.face {
  font-size: 30px;
}

.name {
  font-weight: 600;
}

.sub,
.hint {
  font-size: 12px;
  color: var(--text-3);
}

.big {
  width: 100%;
  height: 44px;
  font-size: 16px;
  margin-bottom: 10px;
}

.link {
  background: none;
  border: none;
  color: var(--text-3);
  font-size: 13px;
}

.error {
  margin: 12px 0 0;
  color: var(--rose-500);
  font-size: 13px;
}

.done p {
  margin: 0 0 14px;
  color: var(--mint-600);
}
</style>
