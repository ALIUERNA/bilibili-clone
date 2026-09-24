<script setup>
/**
 * 邮箱 + 验证码 输入组件（带 60 秒倒计时和开发环境提示）。
 *
 * 用法：
 *   <EmailCodeField v-model:email="form.email" v-model="form.emailCode" purpose="LOGIN" />
 */
import { computed, onUnmounted, ref } from 'vue'
import { api } from '../api'

const props = defineProps({
  email: { type: String, default: '' },
  modelValue: { type: String, default: '' },     // 验证码
  purpose: { type: String, default: 'LOGIN' },   // LOGIN / REGISTER / RESET
  devMode: { type: Boolean, default: true }
})

const emit = defineEmits(['update:email', 'update:modelValue', 'devCode', 'submit'])

const countdown = ref(0)
const sending = ref(false)
const tip = ref('')
const devCode = ref('')
let timer = null

const canSend = computed(() => countdown.value <= 0 && !sending.value && !!props.email)

async function send() {
  if (!canSend.value) return
  if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(props.email)) {
    tip.value = '请输入正确的邮箱地址'
    return
  }
  sending.value = true
  tip.value = ''
  try {
    const res = await api.auth.sendEmailCode(props.email, props.purpose)
    tip.value = res.message || ''
    if (res.devCode) {
      devCode.value = res.devCode
      emit('devCode', res.devCode)
    }
    if (res.success) {
      startCountdown(60)
    } else {
      const wait = Number(res.retryAfter) || 0
      if (wait > 0) startCountdown(wait)
    }
  } catch (e) {
    tip.value = e?.friendlyMessage || '发送失败，请稍后再试'
  } finally {
    sending.value = false
  }
}

function startCountdown(sec) {
  countdown.value = sec
  clearInterval(timer)
  timer = setInterval(() => {
    countdown.value -= 1
    if (countdown.value <= 0) clearInterval(timer)
  }, 1000)
}

onUnmounted(() => clearInterval(timer))
</script>

<template>
  <div class="email-field">
    <div class="row">
      <input
        :value="email"
        type="email"
        autocomplete="email"
        placeholder="请输入邮箱地址"
        @input="emit('update:email', $event.target.value)"
      />
      <button type="button" class="send-btn" :disabled="!canSend" @click="send">
        {{ sending ? '发送中' : countdown > 0 ? countdown + 's' : '获取验证码' }}
      </button>
    </div>
    <div class="row">
      <input
        :value="modelValue"
        type="text"
        inputmode="numeric"
        autocomplete="one-time-code"
        maxlength="6"
        placeholder="请输入 6 位邮箱验证码"
        @input="emit('update:modelValue', $event.target.value)"
        @keyup.enter="emit('submit')"
      />
    </div>
    <p v-if="tip" class="tip">{{ tip }}</p>
    <p v-if="devCode" class="tip dev">
      验证码（开发环境）：<b>{{ devCode }}</b>
    </p>
  </div>
</template>

<style scoped>
.email-field {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.row {
  display: flex;
  gap: 10px;
}

.row input {
  flex: 1;
  min-width: 0;
  height: 42px;
  padding: 0 14px;
  border: 1px solid var(--line);
  border-radius: 8px;
  background: #fff;
  transition: border-color 0.2s, box-shadow 0.2s;
}

.row input:focus {
  border-color: var(--bili-pink);
  box-shadow: 0 0 0 3px rgba(110, 86, 248, 0.12);
}

.send-btn {
  flex-shrink: 0;
  height: 42px;
  padding: 0 14px;
  border-radius: 8px;
  background: rgba(110, 86, 248, 0.1);
  color: var(--bili-pink);
  font-size: 13px;
  font-weight: 600;
  transition: background 0.2s;
}

.send-btn:hover:not(:disabled) {
  background: rgba(110, 86, 248, 0.2);
}

.send-btn:disabled {
  opacity: 0.55;
  cursor: not-allowed;
}

.tip {
  margin: 0;
  font-size: 12px;
  color: var(--text-3);
  line-height: 18px;
}

.tip.dev {
  color: var(--gold-600);
  background: #fffbeb;
  border: 1px dashed var(--gold-200);
  border-radius: 6px;
  padding: 6px 10px;
}

.tip.dev b {
  letter-spacing: 2px;
  color: var(--gold-700);
}
</style>
