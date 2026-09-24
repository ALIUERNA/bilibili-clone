<script setup>
/** 个人中心 - 设置（主题 / 播放 / 弹幕 / 隐私，全部存 MySQL user_settings 表） */
import { onMounted, reactive, ref } from 'vue'
import { api } from '../../api'
import { useUserStore } from '../../stores/user'

const userStore = useUserStore()

const loading = ref(true)
const saving = ref(false)
const form = reactive({
  theme: 'light',
  autoplay: true,
  defaultQuality: '1080P',
  danmakuOn: true,
  danmakuOpacity: 0.95,
  danmakuFontSize: 25,
  danmakuSpeed: 9,
  danmakuArea: 'all',
  showHistory: true,
  emailNotify: true
})

const pwd = reactive({ oldPassword: '', newPassword: '', newPassword2: '' })
const emailForm = reactive({ email: '', code: '' })
const emailTip = ref('')

async function load() {
  loading.value = true
  try {
    const res = await api.uc.settings()
    Object.assign(form, {
      theme: res.theme || 'light',
      autoplay: !!Number(res.autoplay ?? 1),
      defaultQuality: res.default_quality || '1080P',
      danmakuOn: !!Number(res.danmaku_on ?? 1),
      danmakuOpacity: Number(res.danmaku_opacity ?? 0.95),
      danmakuFontSize: Number(res.danmaku_fontsize ?? 25),
      danmakuSpeed: Number(res.danmaku_speed ?? 9),
      danmakuArea: res.danmaku_area || 'all',
      showHistory: !!Number(res.show_history ?? 1),
      emailNotify: !!Number(res.email_notify ?? 1)
    })
  } catch (e) {
    userStore.showToast(e?.friendlyMessage || '设置加载失败')
  } finally {
    loading.value = false
  }
}

async function save() {
  saving.value = true
  try {
    const res = await api.uc.saveSettings({ ...form })
    userStore.showToast(res?.message || '设置已保存')
    applyTheme(form.theme)
  } catch (e) {
    userStore.showToast('保存失败')
  } finally {
    saving.value = false
  }
}

function applyTheme(theme) {
  const dark = theme === 'dark'
  document.documentElement.classList.toggle('theme-dark', dark)
  try {
    localStorage.setItem('bili-theme', theme)
  } catch (e) {
    /* ignore */
  }
}

async function changePassword() {
  if (!pwd.newPassword || pwd.newPassword.length < 6) {
    userStore.showToast('新密码至少 6 位')
    return
  }
  if (pwd.newPassword !== pwd.newPassword2) {
    userStore.showToast('两次输入的新密码不一致')
    return
  }
  try {
    const res = await api.auth.changePassword({ oldPassword: pwd.oldPassword, newPassword: pwd.newPassword })
    userStore.showToast(res?.message || '密码已修改')
    if (res?.success) {
      pwd.oldPassword = pwd.newPassword = pwd.newPassword2 = ''
      setTimeout(() => userStore.logout(), 1200)
    }
  } catch (e) {
    userStore.showToast('修改失败')
  }
}

async function sendCode() {
  try {
    const res = await api.auth.sendEmailCode(emailForm.email, 'REGISTER')
    emailTip.value = res.message || ''
    if (res.devCode) emailTip.value += `（验证码：${res.devCode}）`
  } catch (e) {
    emailTip.value = '发送失败'
  }
}

async function bindEmail() {
  try {
    const res = await api.auth.bindEmail({ email: emailForm.email, code: emailForm.code })
    userStore.showToast(res?.message || '')
    if (res?.success) {
      userStore.setUser({ email: emailForm.email, hasEmail: true })
      emailForm.email = ''
      emailForm.code = ''
    }
  } catch (e) {
    userStore.showToast('绑定失败')
  }
}

onMounted(() => {
  load()
  let saved = 'light'
  try {
    saved = localStorage.getItem('bili-theme') || 'light'
  } catch (e) {
    /* ignore */
  }
  form.theme = saved
})
</script>

<template>
  <div class="panel">
    <header class="head">
      <h2>设置</h2>
      <button class="btn btn-primary btn-round" :disabled="saving" @click="save">
        {{ saving ? '保存中…' : '保存设置' }}
      </button>
    </header>

    <div v-if="loading" class="skeleton sk-line"></div>

    <template v-else>
      <section class="block">
        <h3>外观</h3>
        <label class="row">
          <span>主题</span>
          <select v-model="form.theme" @change="applyTheme(form.theme)">
            <option value="light">浅色</option>
            <option value="dark">深色</option>
          </select>
        </label>
      </section>

      <section class="block">
        <h3>播放</h3>
        <label class="row">
          <span>自动播放</span>
          <input v-model="form.autoplay" type="checkbox" />
        </label>
        <label class="row">
          <span>默认清晰度</span>
          <select v-model="form.defaultQuality">
            <option>1080P</option>
            <option>720P</option>
            <option>480P</option>
          </select>
        </label>
      </section>

      <section class="block">
        <h3>弹幕</h3>
        <label class="row">
          <span>默认开启弹幕</span>
          <input v-model="form.danmakuOn" type="checkbox" />
        </label>
        <label class="row">
          <span>不透明度 {{ form.danmakuOpacity }}</span>
          <input v-model.number="form.danmakuOpacity" type="range" min="0.2" max="1" step="0.05" />
        </label>
        <label class="row">
          <span>字号 {{ form.danmakuFontSize }}</span>
          <input v-model.number="form.danmakuFontSize" type="range" min="16" max="40" step="1" />
        </label>
        <label class="row">
          <span>速度 {{ form.danmakuSpeed }}</span>
          <input v-model.number="form.danmakuSpeed" type="range" min="5" max="14" step="1" />
        </label>
        <label class="row">
          <span>显示区域</span>
          <select v-model="form.danmakuArea">
            <option value="all">全部</option>
            <option value="top">仅顶部</option>
            <option value="bottom">仅底部</option>
          </select>
        </label>
      </section>

      <section class="block">
        <h3>隐私与通知</h3>
        <label class="row">
          <span>公开观看历史</span>
          <input v-model="form.showHistory" type="checkbox" />
        </label>
        <label class="row">
          <span>接收邮件通知</span>
          <input v-model="form.emailNotify" type="checkbox" />
        </label>
      </section>

      <section class="block">
        <h3>修改密码</h3>
        <div class="inline-form">
          <input v-model="pwd.oldPassword" type="password" placeholder="原密码（没有密码可留空）" />
          <input v-model="pwd.newPassword" type="password" placeholder="新密码（至少 6 位）" />
          <input v-model="pwd.newPassword2" type="password" placeholder="确认新密码" />
          <button class="btn btn-primary btn-round" @click="changePassword">修改密码</button>
        </div>
        <p class="hint">修改密码后，其它设备上的登录状态会失效。</p>
      </section>

      <section class="block">
        <h3>绑定 / 换绑邮箱</h3>
        <div class="inline-form">
          <input v-model.trim="emailForm.email" type="email" placeholder="新邮箱" />
          <input v-model.trim="emailForm.code" type="text" placeholder="邮箱验证码" />
          <button class="btn btn-ghost btn-round" @click="sendCode">发送验证码</button>
          <button class="btn btn-primary btn-round" @click="bindEmail">绑定</button>
        </div>
        <p v-if="emailTip" class="hint">{{ emailTip }}</p>
        <p class="hint">当前邮箱：{{ userStore.user?.email || '未绑定' }}</p>
      </section>
    </template>
  </div>
</template>

<style scoped>
.panel {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.head {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.head h2 {
  margin: 0;
  font-size: 18px;
}

.block {
  background: #fff;
  border-radius: 12px;
  padding: 16px 18px;
  box-shadow: var(--shadow-card);
}

.block h3 {
  margin: 0 0 12px;
  font-size: 15px;
}

.row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 8px 0;
  font-size: 13px;
  color: var(--text-2);
  border-bottom: 1px dashed var(--line);
}

.row:last-child {
  border-bottom: none;
}

.row input[type='range'] {
  width: 180px;
}

.row select {
  height: 32px;
  border: 1px solid var(--line);
  border-radius: 8px;
  padding: 0 8px;
}

.inline-form {
  display: flex;
  gap: 10px;
  flex-wrap: wrap;
}

.inline-form input {
  flex: 1;
  min-width: 160px;
  height: 36px;
  padding: 0 12px;
  border: 1px solid var(--line);
  border-radius: 8px;
}

.hint {
  margin: 10px 0 0;
  font-size: 12px;
  color: var(--text-3);
}

.sk-line {
  height: 120px;
}
</style>
