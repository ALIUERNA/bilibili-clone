<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useUserStore } from '../stores/user'
import CaptchaImage from '../components/CaptchaImage.vue'
import EmailCodeField from '../components/EmailCodeField.vue'
import QrLoginPanel from '../components/QrLoginPanel.vue'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

const tab = ref('password') // password | email | qr
const loading = ref(false)
const message = ref('')
const captchaRef = ref(null)

const form = reactive({
  account: '',
  password: '',
  captchaCode: '',
  captchaKey: '',
  email: '',
  emailCode: ''
})

const redirect = computed(() => String(route.query.redirect || ''))
const devMode = computed(() => userStore.authStatus?.devMode !== false)

/** 演示账号一键登录只在开发模式出现：标准模式下走真实登录方式 */
const isDev = import.meta.env.DEV

function afterLogin() {
  const target = redirect.value || '/'
  router.replace(target.startsWith('/') ? target : '/')
}

async function submitPassword() {
  message.value = ''
  if (!form.account || !form.password) {
    message.value = '请输入账号和密码'
    return
  }
  if (!form.captchaCode) {
    message.value = '请输入图形验证码'
    return
  }
  loading.value = true
  try {
    const res = await userStore.loginWithPassword(form)
    if (!res.success) {
      message.value = res.message || '登录失败'
      if (res.needCaptcha && captchaRef.value) captchaRef.value.refresh()
    } else {
      afterLogin()
    }
  } catch (e) {
    message.value = '网络异常，请稍后再试'
  } finally {
    loading.value = false
  }
}

async function submitEmail() {
  message.value = ''
  if (!form.email || !form.emailCode) {
    message.value = '请输入邮箱和验证码'
    return
  }
  loading.value = true
  try {
    const res = await userStore.loginWithEmail({ email: form.email, code: form.emailCode })
    if (!res.success) {
      message.value = res.message || '登录失败'
    } else {
      afterLogin()
    }
  } catch (e) {
    message.value = '网络异常，请稍后再试'
  } finally {
    loading.value = false
  }
}

async function onQrSuccess(payload) {
  await userStore.loginWithQr(payload)
  afterLogin()
}

async function demoLogin() {
  loading.value = true
  try {
    await userStore.login()
    afterLogin()
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  userStore.loadAuthStatus()
  if (route.query.tab === 'qr' || route.query.tab === 'email') {
    tab.value = route.query.tab
  }
})
</script>

<template>
  <div class="auth-page">
    <div class="auth-card">
      <!-- 左侧：表单 -->
      <div class="auth-main">
        <h2 class="auth-title">登录</h2>

        <div class="auth-tabs">
          <button :class="{ on: tab === 'password' }" @click="tab = 'password'">密码登录</button>
          <button :class="{ on: tab === 'email' }" @click="tab = 'email'">邮箱登录</button>
          <button :class="{ on: tab === 'qr' }" @click="tab = 'qr'">扫码登录</button>
        </div>

        <!-- 1. 账号密码 + 图形验证码 -->
        <form v-if="tab === 'password'" class="auth-form" @submit.prevent="submitPassword">
          <input v-model.trim="form.account" type="text" placeholder="请输入账号 / 邮箱" autocomplete="username" />
          <input v-model="form.password" type="password" placeholder="请输入密码" autocomplete="current-password" />
          <CaptchaImage
            ref="captchaRef"
            v-model="form.captchaCode"
            v-model:captcha-key="form.captchaKey"
            purpose="LOGIN"
            @submit="submitPassword"
          />
          <button class="auth-submit" type="submit" :disabled="loading">
            {{ loading ? '登录中…' : '登 录' }}
          </button>
        </form>

        <!-- 2. 邮箱验证码 -->
        <form v-else-if="tab === 'email'" class="auth-form" @submit.prevent="submitEmail">
          <EmailCodeField
            v-model:email="form.email"
            v-model="form.emailCode"
            purpose="LOGIN"
            :dev-mode="devMode"
            @submit="submitEmail"
          />
          <button class="auth-submit" type="submit" :disabled="loading">
            {{ loading ? '登录中…' : '登录 / 注册' }}
          </button>
          <p class="auth-hint">没有账号？邮箱验证通过后会自动为你创建账号。</p>
        </form>

        <!-- 3. 二维码 -->
        <div v-else class="auth-form">
          <QrLoginPanel @success="onQrSuccess" />
        </div>

        <p v-if="message" class="auth-error">{{ message }}</p>

        <div class="auth-links">
          <router-link to="/register">注册账号</router-link>
          <span class="sep">|</span>
          <router-link to="/forgot">忘记密码</router-link>
          <template v-if="isDev">
            <span class="sep">|</span>
            <button class="link-btn" @click="demoLogin">体验账号一键登录</button>
          </template>
        </div>
      </div>

      <!-- 右侧：宣传区 -->
      <aside class="auth-side">
        <div class="side-emoji"><AiIcon :size="56"><Monitor /></AiIcon></div>
        <h3>欢迎回到a哩a哩</h3>
        <p>登录后可以发弹幕、评论、收藏视频、追番，还能投稿自己的作品。</p>
        <ul class="side-tags">
          <li><AiIcon><Platform /></AiIcon> 游戏</li>
          <li><AiIcon><VideoCamera /></AiIcon> 番剧</li>
          <li><AiIcon><Headset /></AiIcon> 音乐</li>
          <li><AiIcon><VideoPlay /></AiIcon> 直播</li>
          <li><AiIcon><EditPen /></AiIcon> 专栏</li>
        </ul>
        <button v-if="isDev" class="side-demo" type="button" :disabled="loading" @click="demoLogin">
          <AiIcon :size="16"><Lightning /></AiIcon>
          {{ loading ? '正在进入…' : '一键体验演示账号' }}
        </button>
        <p class="side-tip">三种登录方式：图形验证码 / 邮箱验证码 / 二维码</p>
      </aside>
    </div>
  </div>
</template>

<style scoped>
.auth-page {
  display: flex;
  justify-content: center;
  padding: 40px 16px 60px;
}

.auth-card {
  display: flex;
  width: 100%;
  max-width: 900px;
  min-height: 460px;
  background: #fff;
  border-radius: 14px;
  box-shadow: 0 12px 40px rgba(0, 0, 0, 0.08);
  overflow: hidden;
}

.auth-main {
  flex: 1;
  padding: 36px 40px;
  min-width: 0;
}

.auth-title {
  margin: 0 0 18px;
  font-size: 24px;
  color: var(--text-1);
}

.auth-tabs {
  display: flex;
  gap: 22px;
  border-bottom: 1px solid var(--line);
  margin-bottom: 22px;
}

.auth-tabs button {
  padding: 8px 2px 12px;
  font-size: 15px;
  color: var(--text-2);
  border-bottom: 2px solid transparent;
  margin-bottom: -1px;
}

.auth-tabs button.on {
  color: var(--bili-pink);
  border-color: var(--bili-pink);
  font-weight: 600;
}

.auth-form {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.auth-form > input {
  height: 42px;
  padding: 0 14px;
  border: 1px solid var(--line);
  border-radius: 8px;
  background: #fff;
  transition: border-color 0.2s, box-shadow 0.2s;
}

.auth-form > input:focus {
  border-color: var(--bili-pink);
  box-shadow: 0 0 0 3px rgba(110, 86, 248, 0.12);
}

.auth-submit {
  height: 44px;
  border-radius: 8px;
  background: var(--bili-pink);
  color: #fff;
  font-size: 16px;
  font-weight: 600;
  transition: background 0.2s, transform 0.15s;
}

.auth-submit:hover:not(:disabled) {
  background: var(--bili-pink-hover);
}

.auth-submit:disabled {
  opacity: 0.65;
  cursor: not-allowed;
}

.auth-hint,
.auth-note {
  margin: 0;
  font-size: 12px;
  color: var(--text-3);
  line-height: 18px;
}

.auth-error {
  margin: 14px 0 0;
  font-size: 13px;
  color: var(--rose-500);
  background: var(--rose-50);
  border: 1px solid var(--rose-200);
  border-radius: 8px;
  padding: 8px 12px;
}

.auth-links {
  display: flex;
  align-items: center;
  gap: 10px;
  margin: 20px 0 12px;
  font-size: 13px;
  color: var(--text-2);
  flex-wrap: wrap;
}

.auth-links a:hover,
.link-btn:hover {
  color: var(--bili-pink);
}

.link-btn {
  background: none;
  border: none;
  color: var(--bili-blue);
  font-size: 13px;
  padding: 0;
}

.sep {
  color: var(--line);
}

.auth-side {
  width: 330px;
  flex-shrink: 0;
  padding: 40px 30px;
  background: linear-gradient(160deg, var(--brand-50) 0%, var(--cyan-50) 100%);
  display: flex;
  flex-direction: column;
  justify-content: center;
  text-align: center;
}

.side-emoji {
  font-size: 56px;
}

.auth-side h3 {
  margin: 14px 0 10px;
  font-size: 18px;
}

.auth-side p {
  margin: 0 0 16px;
  font-size: 13px;
  color: var(--text-2);
  line-height: 20px;
}

.side-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  justify-content: center;
}

.side-tags li {
  padding: 4px 10px;
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.8);
  font-size: 12px;
  color: var(--text-2);
}

.side-demo {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 7px;
  height: 40px;
  margin-top: 22px;
  padding: 0 22px;
  border-radius: var(--r-full);
  background: var(--grad-brand);
  color: #fff;
  font-size: var(--fs-base);
  font-weight: var(--fw-medium);
  box-shadow: var(--sd-brand);
  transition: transform var(--dur-base) var(--ease-out), box-shadow var(--dur-base),
    opacity var(--dur-fast);
}

.side-demo:hover:not(:disabled) {
  transform: translateY(-1px);
  box-shadow: 0 12px 28px rgba(110, 86, 248, 0.36);
}

.side-demo:disabled {
  opacity: 0.7;
  cursor: not-allowed;
}

.side-tip {
  margin-top: 18px !important;
  font-size: 12px !important;
  color: var(--text-3) !important;
}

@media (max-width: 860px) {
  .auth-card {
    flex-direction: column;
    max-width: 520px;
  }

  .auth-side {
    width: 100%;
    padding: 26px 24px;
  }

  .auth-main {
    padding: 26px 22px;
  }
}
</style>
