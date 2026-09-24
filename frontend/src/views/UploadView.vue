<script setup>
/** 投稿页：上传视频 → 后端 FFmpeg 自动截帧生成封面 → 直接发布 */
import { computed, ref } from 'vue'
import { useRouter } from 'vue-router'
import { api } from '../api'
import { useUserStore } from '../stores/user'
import { formatDurationCn } from '../utils/format'

const router = useRouter()
const userStore = useUserStore()

const file = ref(null)
const localUrl = ref('')
const title = ref('')
const node = ref('life')
const description = ref('')
const uploading = ref(false)
const percent = ref(0)
const result = ref(null)
const error = ref('')
const dragging = ref(false)

const nodes = ref([])

const canSubmit = computed(() => !!file.value && !!title.value.trim() && !uploading.value)

async function loadNodes() {
  try {
    const list = await api.nodes()
    nodes.value = (list || []).filter((n) => !n.placeholder && n.code !== 'recommend' && n.code !== 'hot')
  } catch (e) {
    nodes.value = []
  }
}

function onPick(e) {
  accept(e.target.files?.[0])
}

function onDrop(e) {
  dragging.value = false
  accept(e.dataTransfer?.files?.[0])
}

function accept(f) {
  error.value = ''
  result.value = null
  if (!f) return
  const ok = /\.(mp4|webm|mov|mkv)$/i.test(f.name) || (f.type || '').startsWith('video/')
  if (!ok) {
    error.value = '目前支持 mp4 / webm / mov / mkv 格式的视频'
    return
  }
  file.value = f
  if (localUrl.value) URL.revokeObjectURL(localUrl.value)
  localUrl.value = URL.createObjectURL(f)
  if (!title.value) {
    title.value = f.name.replace(/\.[^.]+$/, '')
  }
}

async function submit() {
  if (!canSubmit.value) return
  error.value = ''
  uploading.value = true
  percent.value = 0
  try {
    const res = await api.uc.uploadVideo(
      file.value,
      { title: title.value, node: node.value, description: description.value },
      (e) => {
        if (e.total) percent.value = Math.min(99, Math.round((e.loaded / e.total) * 100))
      }
    )
    percent.value = 100
    if (!res?.success) {
      error.value = res?.message || '投稿失败'
      return
    }
    result.value = res
    userStore.showToast('投稿成功！封面已自动生成')
  } catch (e) {
    error.value = e?.friendlyMessage || '投稿失败，请稍后再试'
  } finally {
    uploading.value = false
  }
}

function reset() {
  file.value = null
  localUrl.value = ''
  title.value = ''
  description.value = ''
  result.value = null
  percent.value = 0
}

function goVideo() {
  if (result.value?.videoId) {
    router.push({ name: 'video', params: { id: result.value.videoId } })
  }
}

loadNodes()
</script>

<template>
  <div class="upload-page container">
    <header class="head">
      <h1>投稿</h1>
      <p>上传后会自动截取视频第 3 秒的画面作为封面，并立即发布。</p>
    </header>

    <div class="layout">
      <!-- 左：拖拽上传 -->
      <section class="drop-area">
        <div
          class="drop-box"
          :class="{ dragging }"
          @dragover.prevent="dragging = true"
          @dragleave.prevent="dragging = false"
          @drop.prevent="onDrop"
          @click="$refs.fileInput.click()"
        >
          <template v-if="!file">
            <div class="drop-emoji"><AiIcon :size="42"><UploadFilled /></AiIcon></div>
            <p class="drop-title">点击选择视频，或把文件拖到这里</p>
            <p class="drop-sub">支持 mp4 / webm / mov / mkv，单个文件不超过 300MB</p>
          </template>
          <template v-else>
            <video v-if="localUrl" class="preview" :src="localUrl" controls preload="metadata"></video>
            <p class="file-name">{{ file.name }} · {{ (file.size / 1024 / 1024).toFixed(1) }} MB</p>
          </template>
          <input ref="fileInput" type="file" accept="video/*" hidden @change="onPick" />
        </div>
      </section>

      <!-- 右：信息填写 -->
      <section class="form-area">
        <label class="field">
          <span>标题</span>
          <input v-model="title" type="text" maxlength="80" placeholder="好标题更容易被推荐哦" />
        </label>

        <label class="field">
          <span>分区</span>
          <select v-model="node">
            <option v-for="n in nodes" :key="n.code" :value="n.code">{{ n.name }}</option>
          </select>
        </label>

        <label class="field">
          <span>简介</span>
          <textarea v-model="description" rows="4" maxlength="300" placeholder="介绍一下这个视频吧~"></textarea>
        </label>

        <div v-if="uploading" class="progress">
          <div class="bar"><div class="fill" :style="{ width: percent + '%' }"></div></div>
          <span>{{ percent }}%</span>
        </div>

        <div class="buttons">
          <button class="btn btn-primary btn-round big" :disabled="!canSubmit" @click="submit">
            {{ uploading ? '投稿中…' : '立即投稿' }}
          </button>
          <button class="btn btn-ghost btn-round" :disabled="uploading" @click="reset">清空</button>
        </div>

        <p v-if="error" class="error">{{ error }}</p>

        <!-- 投稿结果 -->
        <div v-if="result" class="result">
          <h3><AiIcon><CircleCheckFilled /></AiIcon> 投稿成功</h3>
          <div class="result-body">
            <img v-if="result.coverUrl" class="result-cover" :src="result.coverUrl" alt="自动生成的封面" />
            <div>
              <p>封面：自动截帧（第 3 秒）</p>
              <p>时长：{{ formatDurationCn(result.duration) }}</p>
              <p class="hint">视频地址：{{ result.videoUrl }}</p>
            </div>
          </div>
          <button class="btn btn-primary btn-round" @click="goVideo">去播放页看看</button>
        </div>
      </section>
    </div>

    <section class="tips">
      <h3>投稿小贴士</h3>
      <ul>
        <li>投稿后视频与封面都会保存在服务器上，播放时支持拖动进度条。</li>
        <li>标题和简介写清楚，更容易被更多人看到哦。</li>
      </ul>
    </section>
  </div>
</template>

<style scoped>
.upload-page {
  padding: 20px 20px 50px;
}

.head h1 {
  margin: 0 0 6px;
  font-size: 22px;
}

.head p {
  margin: 0 0 18px;
  font-size: 13px;
  color: var(--text-3);
}

.layout {
  display: grid;
  grid-template-columns: minmax(0, 1.2fr) minmax(0, 1fr);
  gap: 20px;
  align-items: start;
}

.drop-area,
.form-area {
  background: #fff;
  border-radius: 12px;
  padding: 18px;
  box-shadow: var(--shadow-card);
}

.drop-box {
  position: relative;
  min-height: 260px;
  border: 2px dashed var(--line);
  border-radius: 12px;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 8px;
  cursor: pointer;
  transition: border-color 0.2s, background 0.2s;
  padding: 16px;
  text-align: center;
}

.drop-box:hover,
.drop-box.dragging {
  border-color: var(--bili-pink);
  background: var(--brand-50);
}

.drop-emoji {
  font-size: 42px;
}

.drop-title {
  margin: 0;
  font-size: 15px;
  color: var(--text-1);
}

.drop-sub {
  margin: 0;
  font-size: 12px;
  color: var(--text-3);
}

.preview {
  width: 100%;
  max-height: 340px;
  border-radius: 10px;
  background: #000;
}

.file-name {
  margin: 0;
  font-size: 12px;
  color: var(--text-3);
  word-break: break-all;
}

.form-area {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.field {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.field span {
  font-size: 13px;
  color: var(--text-2);
}

.field input,
.field select,
.field textarea {
  padding: 10px 14px;
  border: 1px solid var(--line);
  border-radius: 8px;
  background: #fff;
  resize: vertical;
}

.field input:focus,
.field textarea:focus,
.field select:focus {
  border-color: var(--bili-pink);
  box-shadow: 0 0 0 3px rgba(110, 86, 248, 0.12);
}

.progress {
  display: flex;
  align-items: center;
  gap: 10px;
  font-size: 12px;
  color: var(--text-3);
}

.bar {
  flex: 1;
  height: 8px;
  border-radius: 999px;
  background: var(--line);
  overflow: hidden;
}

.fill {
  height: 100%;
  background: var(--grad-fill);
  transition: width 0.2s;
}

.buttons {
  display: flex;
  gap: 10px;
}

.big {
  height: 42px;
  padding: 0 28px;
  font-size: 15px;
}

.error {
  margin: 0;
  font-size: 13px;
  color: var(--rose-500);
  background: var(--rose-50);
  border: 1px solid var(--rose-200);
  border-radius: 8px;
  padding: 8px 12px;
}

.result {
  border-top: 1px dashed var(--line);
  padding-top: 14px;
}

.result h3 {
  margin: 0 0 10px;
  font-size: 15px;
  color: var(--mint-600);
}

.result-body {
  display: flex;
  gap: 14px;
  margin-bottom: 12px;
}

.result-cover {
  width: 150px;
  height: 84px;
  object-fit: cover;
  border-radius: 8px;
  background: var(--surface-sunken);
}

.result-body p {
  margin: 0 0 6px;
  font-size: 13px;
  color: var(--text-2);
}

.hint {
  font-size: 12px !important;
  color: var(--text-3) !important;
  word-break: break-all;
}

.tips {
  margin-top: 22px;
  background: #fff;
  border-radius: 12px;
  padding: 18px;
  box-shadow: var(--shadow-card);
}

.tips h3 {
  margin: 0 0 10px;
  font-size: 15px;
}

.tips li {
  font-size: 13px;
  color: var(--text-2);
  line-height: 24px;
}

.tips code {
  background: var(--surface-0);
  padding: 1px 6px;
  border-radius: 4px;
  font-size: 12px;
}

@media (max-width: 900px) {
  .layout {
    grid-template-columns: 1fr;
  }
}
</style>
