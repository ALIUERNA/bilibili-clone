<script setup>
/** 个人中心 - 编辑资料（昵称 / 签名 / 头像） */
import { onMounted, ref } from 'vue'
import { useUserStore } from '../../stores/user'
import UserAvatar from '../../components/UserAvatar.vue'

const userStore = useUserStore()

const name = ref('')
const sign = ref('')
const saving = ref(false)
const fileInput = ref(null)
const uploading = ref(false)

onMounted(() => {
  name.value = userStore.user?.name || ''
  sign.value = userStore.user?.sign || ''
})

async function save() {
  saving.value = true
  try {
    await userStore.updateProfile({ name: name.value, sign: sign.value })
  } finally {
    saving.value = false
  }
}

function pickFile() {
  fileInput.value?.click()
}

async function onFile(e) {
  const file = e.target.files?.[0]
  if (!file) return
  if (file.size > 5 * 1024 * 1024) {
    userStore.showToast('图片太大了，请上传 5MB 以内的图片')
    return
  }
  uploading.value = true
  try {
    await userStore.uploadAvatar(file)
  } catch (err) {
    userStore.showToast(err?.friendlyMessage || '头像上传失败')
  } finally {
    uploading.value = false
    e.target.value = ''
  }
}

async function resetAvatar() {
  await userStore.clearAvatar()
}
</script>

<template>
  <div class="panel">
    <h2>编辑资料</h2>

    <section class="block">
      <h3>头像</h3>
      <div class="avatar-row">
        <UserAvatar
          :face="userStore.user?.face"
          :face-url="userStore.user?.faceUrl"
          :size="84"
          :level="userStore.user?.level"
        />
        <div class="avatar-ops">
          <button class="btn btn-primary btn-round" :disabled="uploading" @click="pickFile">
            {{ uploading ? '上传中…' : '上传新头像' }}
          </button>
          <button class="btn btn-ghost btn-round" @click="resetAvatar">恢复表情头像</button>
          <p class="hint">支持 png / jpg / gif / webp，5MB 以内</p>
        </div>
        <input ref="fileInput" type="file" accept="image/*" hidden @change="onFile" />
      </div>
    </section>

    <section class="block">
      <h3>基本资料</h3>
      <label class="field">
        <span>昵称（最多 20 字）</span>
        <input v-model.trim="name" type="text" maxlength="20" placeholder="给自己起个名字" />
      </label>
      <label class="field">
        <span>个性签名（最多 60 字）</span>
        <textarea v-model="sign" rows="3" maxlength="60" placeholder="介绍一下自己吧~"></textarea>
      </label>
      <button class="btn btn-primary btn-round" :disabled="saving" @click="save">
        {{ saving ? '保存中…' : '保存修改' }}
      </button>
    </section>

    <section class="block">
      <h3>账号信息（不可修改）</h3>
      <ul class="kv">
        <li><span>账号</span><b>{{ userStore.user?.username || '—' }}</b></li>
        <li><span>邮箱</span><b>{{ userStore.user?.email || '未绑定' }}</b></li>
        <li><span>等级</span><b>Lv{{ userStore.user?.level }}</b></li>
      </ul>
    </section>
  </div>
</template>

<style scoped>
.panel {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.panel h2 {
  margin: 0;
  font-size: 18px;
}

.block {
  background: #fff;
  border-radius: 12px;
  padding: 18px;
  box-shadow: var(--shadow-card);
}

.block h3 {
  margin: 0 0 14px;
  font-size: 15px;
}

.avatar-row {
  display: flex;
  align-items: center;
  gap: 20px;
  flex-wrap: wrap;
}

.avatar-ops {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.field {
  display: flex;
  flex-direction: column;
  gap: 8px;
  margin-bottom: 14px;
}

.field span {
  font-size: 13px;
  color: var(--text-2);
}

.field input,
.field textarea {
  padding: 10px 14px;
  border: 1px solid var(--line);
  border-radius: 8px;
  resize: vertical;
}

.field input:focus,
.field textarea:focus {
  border-color: var(--bili-pink);
  box-shadow: 0 0 0 3px rgba(110, 86, 248, 0.12);
}

.kv {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(220px, 1fr));
  gap: 8px 18px;
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

.hint {
  margin: 0;
  font-size: 12px;
  color: var(--text-3);
}
</style>
