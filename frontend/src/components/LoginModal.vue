<script setup>
/**
 * 登录弹窗（AiModal 外壳 + a哩a哩 自己的内容）。
 * 三种登录方式都在这里：账号密码 + 图形验证码 / 邮箱验证码 / 扫码登录。
 */
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { useUserStore } from '../stores/user'
import CaptchaImage from './CaptchaImage.vue'
import EmailCodeField from './EmailCodeField.vue'
import QrLoginPanel from './QrLoginPanel.vue'

const emit = defineEmits(['close'])
const router = useRouter()
const userStore = useUserStore()

const tab = ref('qr')
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

const devMode = computed(() => userStore.authStatus?.devMode !== false)

/** 演示账号一键登录只在开发模式出现：标准模式下走真实登录方式 */
const isDev = import.meta.env.DEV

const close = () => {
  userStore.closeLogin()
  emit('close')
}

async function doPasswordLogin() {
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
      close()
      goRedirect()
    }
  } catch (e) {
    message.value = '网络异常，请稍后再试'
  } finally {
    loading.value = false
  }
}

async function doEmailLogin() {
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
      close()
      goRedirect()
    }
  } catch (e) {
    message.value = '网络异常，请稍后再试'
  } finally {
    loading.value = false
  }
}

async function onQrSuccess(payload) {
  await userStore.loginWithQr(payload)
  close()
  goRedirect()
}

/** 登录成功后跳转：优先回到调用登录的页面 */
function goRedirect() {
  const redirect = userStore.loginRedirect
  if (!redirect) return
  userStore.loginRedirect = ''
  const path = redirect.startsWith('#') ? redirect.slice(1) : redirect
  router.push(path || '/')
}

async function demoLogin() {
  loading.value = true
  try {
    await userStore.login()
    close()
  } finally {
    loading.value = false
  }
}

onMounted(() => userStore.loadAuthStatus())
</script>

<template>
  <AiModal class="modal" :width="760" :show-close="false" @close="close">
    <div class="login-body">
      <div class="left">
        <div class="title">
          <span class="logo">a哩a哩</span>
          <span class="sub">登录</span>
          <button class="close-btn" title="关闭" @click="close">
            <AiIcon><Close /></AiIcon>
          </button>
        </div>

        <div class="tabs">
          <button :class="{ on: tab === 'qr' }" @click="tab = 'qr'">扫码登录</button>
          <button :class="{ on: tab === 'password' }" @click="tab = 'password'">密码登录</button>
          <button :class="{ on: tab === 'email' }" @click="tab = 'email'">邮箱登录</button>
        </div>

        <!-- 扫码登录 -->
        <div v-if="tab === 'qr'" class="panel">
          <QrLoginPanel @success="onQrSuccess" />
        </div>

        <!-- 密码登录 -->
        <form v-else-if="tab === 'password'" class="panel" @submit.prevent="doPasswordLogin">
          <input v-model.trim="form.account" type="text" placeholder="请输入账号 / 邮箱" autocomplete="username" />
          <input v-model="form.password" type="password" placeholder="请输入密码" autocomplete="current-password" />
          <CaptchaImage
            ref="captchaRef"
            v-model="form.captchaCode"
            v-model:captcha-key="form.captchaKey"
            purpose="LOGIN"
            @submit="doPasswordLogin"
          />
          <button class="primary" type="submit" :disabled="loading">
            {{ loading ? '登录中…' : '登 录' }}
          </button>
          <div class="links">
            <router-link to="/register" @click="close">注册账号</router-link>
            <span>|</span>
            <router-link to="/forgot" @click="close">忘记密码</router-link>
            <template v-if="isDev">
              <span>|</span>
              <button type="button" class="link-btn" @click="demoLogin">一键体验</button>
            </template>
          </div>
        </form>

        <!-- 邮箱登录 -->
        <form v-else class="panel" @submit.prevent="doEmailLogin">
          <EmailCodeField
            v-model:email="form.email"
            v-model="form.emailCode"
            purpose="LOGIN"
            :dev-mode="devMode"
            @submit="doEmailLogin"
          />
          <button class="primary" type="submit" :disabled="loading">
            {{ loading ? '登录中…' : '登录 / 注册' }}
          </button>
          <p class="tip">邮箱验证通过后，没有账号会自动创建。</p>
        </form>

        <p v-if="message" class="error">{{ message }}</p>

        <div class="agreement">
          登录即代表同意 <a href="javascript:void(0)">用户协议</a> 和
          <a href="javascript:void(0)">隐私政策</a>
        </div>
      </div>

      <div class="right">
        <div class="art"><AiIcon :size="46"><Monitor /></AiIcon></div>
        <h3>欢迎来到a哩a哩</h3>
        <p>一起看番、一起追更、一起干杯</p>
        <div class="tags">
          <span>弹幕</span><span>追番</span><span>三连</span>
        </div>


        <!-- 演示入口：开发模式下放在右栏，切到哪个标签页都能一键进去 -->
        <button v-if="isDev" class="demo-btn" type="button" :disabled="loading" @click="demoLogin">
          <AiIcon :size="15"><Lightning /></AiIcon>
          {{ loading ? '正在进入…' : '一键体验演示账号' }}
        </button>
        <p v-if="isDev" class="demo-hint">免注册，直接进站体验</p>
      </div>
    </div>
  </AiModal>
</template>

<style scoped>
.login-body {
  display: flex;
}

.left {
  flex: 1;
  min-width: 0;
  padding: 24px 30px 22px;
}

.title {
  display: flex;
  align-items: baseline;
  gap: 8px;
}

.logo {
  font-size: 21px;
  font-weight: 700;
  color: var(--bili-pink);
}

.sub {
  font-size: 15px;
  color: var(--text-2);
}

.close-btn {
  margin-left: auto;
  font-size: 14px;
  color: var(--text-3);
  background: none;
  border: none;
}

.close-btn:hover {
  color: var(--bili-pink);
}

.tabs {
  display: flex;
  gap: 18px;
  margin: 16px 0 16px;
  border-bottom: 1px solid var(--line);
}

.tabs button {
  padding: 6px 2px 10px;
  font-size: 15px;
  color: var(--text-2);
  border-bottom: 2px solid transparent;
  margin-bottom: -1px;
}

.tabs button.on {
  color: var(--bili-pink);
  border-color: var(--bili-pink);
  font-weight: 600;
}

.panel {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.panel > input {
  height: 42px;
  padding: 0 14px;
  border: 1px solid var(--line);
  border-radius: 8px;
}

.panel > input:focus {
  border-color: var(--bili-pink);
  box-shadow: 0 0 0 3px rgba(110, 86, 248, 0.12);
}

.primary {
  height: 42px;
  border-radius: 8px;
  background: var(--bili-pink);
  color: #fff;
  font-size: 15px;
  font-weight: 600;
}

.primary:hover:not(:disabled) {
  background: var(--bili-pink-hover);
}

.primary:disabled {
  opacity: 0.65;
}

.links {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 13px;
  color: var(--text-3);
}

.links a {
  color: var(--bili-blue);
}

.link-btn {
  background: none;
  border: none;
  color: var(--bili-blue);
  font-size: 13px;
  padding: 0;
}

.tip {
  margin: 0;
  font-size: 12px;
  color: var(--text-3);
}

.error {
  margin: 12px 0 0;
  font-size: 13px;
  color: var(--rose-500);
  background: var(--rose-50);
  border: 1px solid var(--rose-200);
  border-radius: 8px;
  padding: 8px 12px;
}

.agreement {
  margin-top: 16px;
  font-size: 12px;
  color: var(--text-3);
}

.agreement a {
  color: var(--bili-blue);
}

.demo-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  height: 36px;
  margin-top: 18px;
  padding: 0 18px;
  border-radius: var(--r-full);
  background: var(--grad-brand);
  color: #fff;
  font-size: var(--fs-sm);
  font-weight: var(--fw-medium);
  box-shadow: var(--sd-brand);
  transition: transform var(--dur-base) var(--ease-out), box-shadow var(--dur-base),
    opacity var(--dur-fast);
}

.demo-btn:hover:not(:disabled) {
  transform: translateY(-1px);
  box-shadow: 0 10px 24px rgba(110, 86, 248, 0.34);
}

.demo-btn:disabled {
  opacity: 0.7;
  cursor: not-allowed;
}

.demo-hint {
  margin-top: 8px !important;
  font-size: var(--fs-xs) !important;
  color: var(--ink-4) !important;
}

.right {
  width: 260px;
  flex-shrink: 0;
  padding: 30px 24px;
  background: linear-gradient(160deg, var(--brand-50), var(--cyan-50));
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  text-align: center;
}

.art {
  font-size: 46px;
}

.right h3 {
  margin: 12px 0 8px;
  font-size: 16px;
}

.right p {
  margin: 0 0 14px;
  font-size: 13px;
  color: var(--text-2);
}

.tags {
  display: flex;
  gap: 8px;
}

.tags span {
  padding: 3px 10px;
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.85);
  font-size: 12px;
  color: var(--text-2);
}

@media (max-width: 720px) {
  .right {
    display: none;
  }

  .left {
    padding: 20px 18px;
  }
}
</style>
