<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { api } from '../api'
import { useUserStore } from '../stores/user'
import CaptchaImage from '../components/CaptchaImage.vue'
import EmailCodeField from '../components/EmailCodeField.vue'

const router = useRouter()
const userStore = useUserStore()

const step = ref(1)
const loading = ref(false)
const message = ref('')
const okMessage = ref('')
const captchaRef = ref(null)

const form = reactive({
  email: '',
  emailCode: '',
  newPassword: '',
  newPassword2: '',
  captchaCode: '',
  captchaKey: ''
})

const devMode = computed(() => userStore.authStatus?.devMode !== false)

async function submit() {
  message.value = ''
  okMessage.value = ''
  if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(form.email)) {
    message.value = '请输入注册时使用的邮箱'
    return
  }
  if (!form.emailCode) {
    message.value = '请输入邮箱验证码'
    return
  }
  if (!form.newPassword || form.newPassword.length < 6) {
    message.value = '新密码至少 6 位'
    return
  }
  if (form.newPassword !== form.newPassword2) {
    message.value = '两次输入的密码不一致'
    return
  }
  if (!form.captchaCode) {
    message.value = '请输入图形验证码'
    return
  }
  loading.value = true
  try {
    const res = await api.auth.resetPassword({
      email: form.email,
      code: form.emailCode,
      newPassword: form.newPassword,
      captchaKey: form.captchaKey,
      captchaCode: form.captchaCode
    })
    if (!res.success) {
      message.value = res.message || '重置失败'
      if (res.needCaptcha && captchaRef.value) captchaRef.value.refresh()
      return
    }
    step.value = 2
    okMessage.value = res.message || '密码已重置'
    setTimeout(() => router.replace('/login'), 1500)
  } catch (e) {
    message.value = '网络异常，请稍后再试'
  } finally {
    loading.value = false
  }
}

onMounted(() => userStore.loadAuthStatus())
</script>

<template>
  <div class="auth-page">
    <div class="auth-card">
      <h2 class="auth-title">找回密码</h2>
      <p class="auth-sub">通过注册邮箱接收验证码，验证通过后即可设置新密码</p>

      <ol class="steps">
        <li :class="{ on: step >= 1 }"><span>1</span> 验证邮箱</li>
        <li :class="{ on: step >= 2 }"><span>2</span> 设置新密码</li>
      </ol>

      <form v-if="step === 1" class="auth-form" @submit.prevent="submit">
        <label class="field">
          <span>邮箱</span>
          <EmailCodeField
            v-model:email="form.email"
            v-model="form.emailCode"
            purpose="RESET"
            :dev-mode="devMode"
            @submit="submit"
          />
        </label>

        <div class="two-cols">
          <label class="field">
            <span>新密码</span>
            <input v-model="form.newPassword" type="password" placeholder="至少 6 位" autocomplete="new-password" />
          </label>
          <label class="field">
            <span>确认新密码</span>
            <input v-model="form.newPassword2" type="password" placeholder="再填一次" autocomplete="new-password" />
          </label>
        </div>

        <label class="field">
          <span>图形验证码</span>
          <CaptchaImage
            ref="captchaRef"
            v-model="form.captchaCode"
            v-model:captcha-key="form.captchaKey"
            purpose="RESET"
            @submit="submit"
          />
        </label>

        <button class="auth-submit" type="submit" :disabled="loading">
          {{ loading ? '提交中…' : '重置密码' }}
        </button>
      </form>

      <div v-else class="done">
        <div class="done-emoji"><AiIcon :size="46"><CircleCheckFilled /></AiIcon></div>
        <p>{{ okMessage }}</p>
        <router-link class="btn btn-primary btn-round" to="/login">去登录</router-link>
      </div>

      <p v-if="message" class="auth-error">{{ message }}</p>

      <div class="auth-links">
        <router-link to="/login">返回登录</router-link>
        <span class="sep">|</span>
        <router-link to="/register">注册新账号</router-link>
      </div>
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
  width: 100%;
  max-width: 560px;
  background: #fff;
  border-radius: 14px;
  box-shadow: 0 12px 40px rgba(0, 0, 0, 0.08);
  padding: 32px 36px 28px;
}

.auth-title {
  margin: 0 0 6px;
  font-size: 24px;
}

.auth-sub {
  margin: 0 0 18px;
  font-size: 13px;
  color: var(--text-3);
}

.steps {
  display: flex;
  gap: 28px;
  margin-bottom: 20px;
  font-size: 13px;
  color: var(--text-3);
}

.steps li {
  display: flex;
  align-items: center;
  gap: 6px;
}

.steps span {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 20px;
  height: 20px;
  border-radius: 50%;
  background: var(--line);
  font-size: 12px;
}

.steps li.on {
  color: var(--bili-pink);
}

.steps li.on span {
  background: var(--bili-pink);
  color: #fff;
}

.auth-form {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.field {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.field > span {
  font-size: 13px;
  color: var(--text-2);
}

.field input[type='password'] {
  height: 42px;
  padding: 0 14px;
  border: 1px solid var(--line);
  border-radius: 8px;
}

.two-cols {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 14px;
}

.auth-submit {
  height: 44px;
  border-radius: 8px;
  background: var(--bili-pink);
  color: #fff;
  font-size: 16px;
  font-weight: 600;
}

.done {
  text-align: center;
  padding: 26px 0;
}

.done-emoji {
  font-size: 44px;
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
  margin-top: 18px;
  font-size: 13px;
  color: var(--text-2);
}

.auth-links a {
  color: var(--bili-blue);
}

.sep {
  margin: 0 10px;
  color: var(--line);
}

@media (max-width: 560px) {
  .auth-card {
    padding: 24px 20px;
  }

  .two-cols {
    grid-template-columns: 1fr;
  }
}
</style>
