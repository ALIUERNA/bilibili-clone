<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { api } from '../api'
import { useUserStore } from '../stores/user'
import CaptchaImage from '../components/CaptchaImage.vue'
import EmailCodeField from '../components/EmailCodeField.vue'

const router = useRouter()
const userStore = useUserStore()

const loading = ref(false)
const message = ref('')
const okMessage = ref('')
const agree = ref(true)
const captchaRef = ref(null)

const form = reactive({
  username: '',
  email: '',
  emailCode: '',
  password: '',
  password2: '',
  captchaCode: '',
  captchaKey: ''
})

const devMode = computed(() => userStore.authStatus?.devMode !== false)

function validate() {
  if (!/^[A-Za-z0-9_\u4e00-\u9fa5]{4,20}$/.test(form.username)) {
    return '账号需要 4~20 位中英文、数字或下划线'
  }
  if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(form.email)) {
    return '请输入正确的邮箱地址'
  }
  if (!form.emailCode) {
    return '请输入邮箱验证码'
  }
  if (!form.password || form.password.length < 6) {
    return '密码至少 6 位'
  }
  if (form.password !== form.password2) {
    return '两次输入的密码不一致'
  }
  if (!form.captchaCode) {
    return '请输入图形验证码'
  }
  if (!agree.value) {
    return '请先同意用户协议与隐私政策'
  }
  return ''
}

async function submit() {
  message.value = ''
  okMessage.value = ''
  const err = validate()
  if (err) {
    message.value = err
    return
  }
  loading.value = true
  try {
    const res = await api.auth.register({
      username: form.username,
      email: form.email,
      password: form.password,
      emailCode: form.emailCode,
      captchaKey: form.captchaKey,
      captchaCode: form.captchaCode
    })
    if (!res.success) {
      message.value = res.message || '注册失败'
      if (res.needCaptcha && captchaRef.value) captchaRef.value.refresh()
      return
    }
    userStore.setSession(res)
    okMessage.value = '注册成功，正在进入首页…'
    setTimeout(() => router.replace('/'), 800)
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
    <div class="auth-card single">
      <div class="auth-main">
        <h2 class="auth-title">注册账号</h2>
        <p class="auth-sub">注册成功后会自动登录</p>

        <form class="auth-form" @submit.prevent="submit">
          <label class="field">
            <span>账号</span>
            <input v-model.trim="form.username" type="text" placeholder="4~20 位中英文 / 数字 / 下划线" />
          </label>

          <label class="field">
            <span>邮箱</span>
            <EmailCodeField
              v-model:email="form.email"
              v-model="form.emailCode"
              purpose="REGISTER"
              :dev-mode="devMode"
              @submit="submit"
            />
          </label>

          <div class="two-cols">
            <label class="field">
              <span>密码</span>
              <input v-model="form.password" type="password" placeholder="至少 6 位" autocomplete="new-password" />
            </label>
            <label class="field">
              <span>确认密码</span>
              <input v-model="form.password2" type="password" placeholder="再填一次" autocomplete="new-password" />
            </label>
          </div>

          <label class="field">
            <span>图形验证码</span>
            <CaptchaImage
              ref="captchaRef"
              v-model="form.captchaCode"
              v-model:captcha-key="form.captchaKey"
              purpose="REGISTER"
              @submit="submit"
            />
          </label>

          <label class="agree">
            <input v-model="agree" type="checkbox" />
            <span>我已阅读并同意 <a href="javascript:void(0)">用户协议</a> 与 <a href="javascript:void(0)">隐私政策</a></span>
          </label>

          <button class="auth-submit" type="submit" :disabled="loading">
            {{ loading ? '注册中…' : '注 册' }}
          </button>
        </form>

        <p v-if="message" class="auth-error">{{ message }}</p>
        <p v-if="okMessage" class="auth-ok">{{ okMessage }}</p>

        <div class="auth-links">
          已经有账号了？<router-link to="/login">直接登录</router-link>
        </div>
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
}

.auth-main {
  padding: 32px 36px 28px;
}

.auth-title {
  margin: 0 0 6px;
  font-size: 24px;
}

.auth-sub {
  margin: 0 0 22px;
  font-size: 13px;
  color: var(--text-3);
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

.field input[type='text'],
.field input[type='password'] {
  height: 42px;
  padding: 0 14px;
  border: 1px solid var(--line);
  border-radius: 8px;
  background: #fff;
}

.field input:focus {
  border-color: var(--bili-pink);
  box-shadow: 0 0 0 3px rgba(110, 86, 248, 0.12);
}

.two-cols {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 14px;
}

.agree {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 12px;
  color: var(--text-2);
}

.agree a {
  color: var(--bili-blue);
}

.auth-submit {
  height: 44px;
  border-radius: 8px;
  background: var(--bili-pink);
  color: #fff;
  font-size: 16px;
  font-weight: 600;
}

.auth-submit:disabled {
  opacity: 0.65;
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

.auth-ok {
  margin: 14px 0 0;
  font-size: 13px;
  color: var(--mint-600);
  background: var(--mint-50);
  border: 1px solid var(--mint-200);
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

@media (max-width: 560px) {
  .auth-main {
    padding: 24px 20px;
  }

  .two-cols {
    grid-template-columns: 1fr;
  }
}
</style>
