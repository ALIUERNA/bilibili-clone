<script setup>
import { computed, ref, watch } from 'vue'
import { useUserStore } from '../stores/user'
import UserAvatar from './UserAvatar.vue'

/**
 * 编辑资料弹窗：改昵称、改签名、上传头像。
 *
 * 外壳用 Element Plus 的 Dialog，上传用 Element Plus 的 Upload（自带拖拽、点击选择、文件校验），
 * 头像选好后立刻上传，页面上马上就能看到效果。
 */
const emit = defineEmits(['close'])
const store = useUserStore()

const uploadRef = ref(null)
const name = ref(store.user?.name || '')
const sign = ref(store.user?.sign || '')
const previewUrl = ref('')
const uploading = ref(false)
const dragging = ref(false)
const saving = ref(false)

// 打开弹窗时同步一次最新资料
watch(
  () => store.user,
  (u) => {
    if (!u) return
    if (!name.value) name.value = u.name || ''
    if (!sign.value) sign.value = u.sign || ''
  },
  { immediate: true, deep: true }
)

const nameLeft = computed(() => 20 - (name.value || '').length)
const signLeft = computed(() => 60 - (sign.value || '').length)
const avatarSrc = computed(() => previewUrl.value || store.user?.faceUrl || '')

/** Element Plus Upload 选完文件后回调 */
function onFileChange(uploadFile) {
  const file = uploadFile?.raw
  if (file) handleFile(file)
}

async function handleFile(file) {
  if (!file.type.startsWith('image/')) {
    store.showToast('只能上传图片文件哦（png / jpg / gif / webp）')
    return
  }
  if (file.size > 5 * 1024 * 1024) {
    store.showToast('图片太大了，请选择 5MB 以内的图片')
    return
  }
  // 先用浏览器给的临时地址本地预览，再上传到服务器
  previewUrl.value = URL.createObjectURL(file)
  uploading.value = true
  try {
    await store.uploadAvatar(file)
  } catch (err) {
    previewUrl.value = ''
    store.showToast(err?.friendlyMessage || '头像上传失败，请重试')
  } finally {
    uploading.value = false
  }
}

/** 改回 emoji 头像（同时通知后端把图片删掉） */
async function resetAvatar() {
  previewUrl.value = ''
  try {
    await store.clearAvatar()
  } catch (e) {
    store.showToast('操作失败，请稍后重试')
  }
}

async function save() {
  const trimmed = name.value.trim()
  if (!trimmed) {
    store.showToast('昵称不能为空')
    return
  }
  if (trimmed.length > 20) {
    store.showToast('昵称最多 20 个字')
    return
  }
  saving.value = true
  try {
    await store.updateProfile({ name: trimmed, sign: sign.value.trim() })
    emit('close')
  } catch (err) {
    store.showToast(err?.friendlyMessage || '保存失败，请重试')
  } finally {
    saving.value = false
  }
}
</script>

<template>
  <el-dialog
    :model-value="true"
    class="modal"
    width="700px"
    align-center
    append-to-body
    :show-close="false"
    @close="$emit('close')"
    @update:model-value="(v) => !v && $emit('close')"
  >
    <div class="profile-body">
      <button class="close" @click="$emit('close')">✕</button>
      <h3 class="title">编辑我的资料</h3>
      <p class="sub">头像和昵称会保存在服务器上，刷新、重启都不会丢</p>

      <div class="body">
        <!-- 头像上传（Element Plus Upload：支持点击选择 + 拖拽） -->
        <div class="avatar-col">
          <el-upload
            ref="uploadRef"
            class="avatar-uploader"
            :auto-upload="false"
            :show-file-list="false"
            accept="image/*"
            drag
            :on-change="onFileChange"
          >
            <div class="drop" :class="{ dragging, uploading }">
              <UserAvatar :face="store.user?.face" :face-url="avatarSrc" :size="110" hover-zoom />
              <div class="overlay">
                <span v-if="uploading">上传中…</span>
                <span v-else>点击 / 拖拽<br />更换头像</span>
              </div>
            </div>
          </el-upload>

          <div class="hint">
            支持 png / jpg / gif / webp<br />
            大小不超过 5MB
          </div>
          <button v-if="avatarSrc" class="btn btn-round reset" :disabled="uploading" @click="resetAvatar">
            用回表情头像
          </button>
        </div>

        <!-- 昵称 / 签名 -->
        <div class="field-col">
          <label class="field">
            <span class="label">昵称</span>
            <input v-model="name" type="text" maxlength="20" placeholder="给自己起个好听的名字" />
            <span class="counter" :class="{ warn: nameLeft < 5 }">还可以输入 {{ nameLeft }} 字</span>
          </label>

          <label class="field">
            <span class="label">个性签名</span>
            <textarea v-model="sign" rows="3" maxlength="60" placeholder="介绍一下自己吧~"></textarea>
            <span class="counter" :class="{ warn: signLeft < 10 }">还可以输入 {{ signLeft }} 字</span>
          </label>

          <div class="preview">
            <div class="preview-title">预览效果（鼠标移到头像上还能看到资料卡）</div>
            <div class="preview-card">
              <UserAvatar :face="store.user?.face" :face-url="avatarSrc" :size="48" :level="store.user?.level" />
              <div class="pv-info">
                <div class="pv-name">{{ name || '你的昵称' }}</div>
                <div class="pv-sign ellipsis">{{ sign || '这个人很懒，什么都没写~' }}</div>
              </div>
            </div>
          </div>
        </div>
      </div>

      <div class="footer">
        <button class="btn btn-round" @click="$emit('close')">取消</button>
        <button class="btn btn-primary btn-round" :disabled="saving || uploading" @click="save">
          {{ saving ? '保存中…' : '保存修改' }}
        </button>
      </div>
    </div>
  </el-dialog>
</template>

<style scoped>
:deep(.el-dialog) {
  border-radius: 14px;
  padding: 0;
  overflow: visible;
  box-shadow: 0 24px 60px rgba(0, 0, 0, 0.28);
}

:deep(.el-dialog__header) {
  display: none;
}

:deep(.el-dialog__body) {
  padding: 0;
}

.profile-body {
  position: relative;
  padding: 24px 28px 20px;
  max-height: 86vh;
  overflow: auto;
}

.close {
  position: absolute;
  right: 16px;
  top: 14px;
  font-size: 16px;
  color: var(--text-3);
}

.close:hover {
  color: var(--bili-pink);
}

.title {
  margin: 0 0 4px;
  font-size: 19px;
}

.sub {
  margin: 0 0 20px;
  font-size: 12px;
  color: var(--text-3);
}

.body {
  display: grid;
  grid-template-columns: 190px 1fr;
  gap: 26px;
}

/* 头像上传区：把 Element Plus 的拖拽框样式覆盖成圆形 */
.avatar-col {
  text-align: center;
}

.avatar-uploader :deep(.el-upload) {
  width: auto;
  height: auto;
  border: none;
  border-radius: 0;
  padding: 0;
  transition: none;
}

.avatar-uploader :deep(.el-upload-dragger) {
  width: auto;
  height: auto;
  padding: 0;
  border: none;
  background: transparent;
  border-radius: 0;
}

.avatar-uploader :deep(.el-upload-dragger:hover) {
  border: none;
}

.drop {
  position: relative;
  width: 130px;
  height: 130px;
  margin: 0 auto;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  border: 2px dashed #e3e5e7;
  transition: border-color 0.2s, background 0.2s, transform 0.2s;
}

.drop:hover,
.drop.dragging {
  border-color: var(--bili-pink);
  background: #fff5f8;
}

.drop.dragging {
  transform: scale(1.04);
}

.drop.uploading {
  opacity: 0.75;
}

.overlay {
  position: absolute;
  inset: 0;
  border-radius: 50%;
  background: rgba(0, 0, 0, 0.5);
  color: #fff;
  font-size: 12px;
  line-height: 17px;
  display: flex;
  align-items: center;
  justify-content: center;
  text-align: center;
  opacity: 0;
  transition: opacity 0.2s;
}

.drop:hover .overlay,
.drop.dragging .overlay,
.drop.uploading .overlay {
  opacity: 1;
}

.hint {
  margin-top: 10px;
  font-size: 11px;
  line-height: 17px;
  color: var(--text-3);
}

.reset {
  margin-top: 8px;
  height: 26px;
  padding: 0 12px;
  font-size: 12px;
}

/* 表单 */
.field {
  display: block;
  margin-bottom: 16px;
  position: relative;
}

.label {
  display: block;
  font-size: 13px;
  color: var(--text-2);
  margin-bottom: 6px;
}

.field input,
.field textarea {
  width: 100%;
  border: 1px solid var(--line);
  border-radius: 8px;
  padding: 9px 12px;
  font-size: 14px;
  transition: border-color 0.2s, box-shadow 0.2s;
  resize: vertical;
  font-family: inherit;
  outline: none;
}

.field input:focus,
.field textarea:focus {
  border-color: var(--bili-pink);
  box-shadow: 0 0 0 3px rgba(251, 114, 153, 0.14);
}

.counter {
  position: absolute;
  right: 0;
  top: 0;
  font-size: 11px;
  color: var(--text-3);
}

.counter.warn {
  color: var(--bili-pink);
}

/* 预览 */
.preview-title {
  font-size: 12px;
  color: var(--text-3);
  margin-bottom: 8px;
}

.preview-card {
  display: flex;
  align-items: center;
  gap: 12px;
  background: linear-gradient(120deg, #fff6f9, #f0f9ff);
  border-radius: 10px;
  padding: 12px 14px;
}

.pv-name {
  font-weight: 600;
  font-size: 15px;
}

.pv-sign {
  font-size: 12px;
  color: var(--text-2);
  max-width: 330px;
}

.footer {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
  margin-top: 22px;
}

.footer .btn {
  height: 36px;
  padding: 0 22px;
}

.footer .btn-primary:disabled {
  opacity: 0.7;
  cursor: default;
}

@media (max-width: 640px) {
  .body {
    grid-template-columns: 1fr;
  }
}
</style>
