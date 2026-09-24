<script setup>
/**
 * 图形验证码输入框（可点击刷新）。
 *
 * 用法：
 *   <CaptchaImage v-model="form.captchaCode" v-model:captcha-key="form.captchaKey" purpose="LOGIN" />
 *
 * 后端每次生成都会返回一个新的 captchaKey，验证码 5 分钟内有效且一次性使用，
 * 点击图片（或「看不清」按钮）即可换一张。
 */
import { onMounted, ref } from 'vue'
import { api } from '../api'

const props = defineProps({
  modelValue: { type: String, default: '' },      // 用户输入的验证码
  captchaKey: { type: String, default: '' },      // 后端返回的标识
  purpose: { type: String, default: 'LOGIN' },    // LOGIN / REGISTER / RESET
  placeholder: { type: String, default: '请输入图形验证码' }
})

const emit = defineEmits(['update:modelValue', 'update:captchaKey', 'loaded', 'submit'])

const image = ref('')
const loading = ref(false)
const error = ref('')

async function refresh() {
  loading.value = true
  error.value = ''
  emit('update:modelValue', '')
  try {
    const res = await api.auth.captcha(props.purpose)
    image.value = res.image || ''
    emit('update:captchaKey', res.captchaKey || '')
    emit('loaded', res)
  } catch (e) {
    error.value = '验证码加载失败，点击重试'
  } finally {
    loading.value = false
  }
}

onMounted(refresh)

defineExpose({ refresh })
</script>

<template>
  <div class="captcha-field">
    <div class="input-wrap">
      <input
        :value="modelValue"
        type="text"
        inputmode="text"
        autocomplete="off"
        maxlength="6"
        :placeholder="placeholder"
        @input="emit('update:modelValue', $event.target.value)"
        @keyup.enter="emit('submit')"
      />
    </div>

    <button type="button" class="captcha-img" :title="'看不清？点击刷新'" @click="refresh">
      <img v-if="image" :src="image" alt="点击刷新验证码" width="120" height="40" />
      <span v-else class="skeleton cap-skeleton">{{ error || '加载中' }}</span>
      <span v-if="loading" class="cap-mask">刷新中…</span>
    </button>
  </div>
</template>

<style scoped>
.captcha-field {
  display: flex;
  gap: 10px;
  align-items: center;
  width: 100%;
}

.input-wrap {
  flex: 1;
  min-width: 0;
}

.input-wrap input {
  width: 100%;
  height: 42px;
  padding: 0 14px;
  border: 1px solid var(--line);
  border-radius: 8px;
  background: #fff;
  color: var(--text-1);
  transition: border-color 0.2s, box-shadow 0.2s;
}

.input-wrap input:focus {
  border-color: var(--bili-pink);
  box-shadow: 0 0 0 3px rgba(110, 86, 248, 0.12);
}

.captcha-img {
  position: relative;
  width: 120px;
  height: 42px;
  padding: 0;
  border: 1px solid var(--line);
  border-radius: 8px;
  overflow: hidden;
  background: #fff;
  flex-shrink: 0;
  cursor: pointer;
}

.captcha-img img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  display: block;
}

.cap-skeleton {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 100%;
  height: 100%;
  font-size: 12px;
  color: var(--text-3);
}

.cap-mask {
  position: absolute;
  inset: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 12px;
  color: #fff;
  background: rgba(0, 0, 0, 0.35);
}

@media (max-width: 480px) {
  .captcha-field {
    flex-direction: column;
    align-items: stretch;
  }

  .captcha-img {
    width: 100%;
  }
}
</style>
