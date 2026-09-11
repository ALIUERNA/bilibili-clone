<script setup>
import { ref } from 'vue'
import { useUserStore } from '../stores/user'

/**
 * 登录弹窗。
 * 外壳用 Element Plus 的 Dialog（自带遮罩、ESC 关闭、焦点管理、过渡动画），
 * 里面的样式依然是照着 B 站扫码登录做的。
 */
defineEmits(['close'])
const userStore = useUserStore()
const loading = ref(false)
const tab = ref('qr')

// 生成一个「像二维码」的点阵，纯装饰用
const cells = Array.from({ length: 21 * 21 }, (_, i) => {
  const row = Math.floor(i / 21)
  const col = i % 21
  const inFinder = (r, c) =>
    (r < 7 && c < 7) || (r < 7 && c > 13) || (r > 13 && c < 7)
  return inFinder(row, col) ? true : (row * 31 + col * 17) % 3 === 0
})

async function doLogin() {
  loading.value = true
  try {
    await userStore.login()
  } finally {
    loading.value = false
  }
}

const close = () => userStore.loginVisible = false
</script>

<template>
  <el-dialog
    :model-value="true"
    class="modal"
    width="720px"
    align-center
    append-to-body
    :show-close="false"
    :close-on-click-modal="true"
    @close="close"
    @update:model-value="(v) => !v && close()"
  >
    <div class="login-body">
      <div class="left">
        <div class="title">
          <span class="logo">哔哩哔哩</span>
          <span class="sub">登录</span>
        </div>

        <div class="tabs">
          <button :class="{ on: tab === 'qr' }" @click="tab = 'qr'">扫码登录</button>
          <button :class="{ on: tab === 'sms' }" @click="tab = 'sms'">短信登录</button>
        </div>

        <div v-if="tab === 'qr'" class="qr-area">
          <div class="qr">
            <div class="qr-grid">
              <span v-for="(on, i) in cells" :key="i" :class="{ on }"></span>
            </div>
            <div class="qr-logo">bili</div>
          </div>
          <p class="tip">请使用「哔哩哔哩客户端」扫码登录</p>
          <p class="tip small">（演示项目：二维码是画出来的，直接点下面的按钮即可登录）</p>
        </div>

        <div v-else class="sms-area">
          <div class="field">
            <span class="prefix">+86</span>
            <input type="text" placeholder="请输入手机号" value="138 0000 0000" readonly />
          </div>
          <div class="field">
            <input type="text" placeholder="请输入验证码" value="123456" readonly />
            <button class="code-btn">获取验证码</button>
          </div>
          <p class="tip small">演示项目：验证码已自动填好</p>
        </div>

        <button class="primary" :disabled="loading" @click="doLogin">
          {{ loading ? '登录中...' : '一键登录' }}
        </button>

        <div class="agreement">
          登录即代表同意 <a href="javascript:void(0)">用户协议</a> 和
          <a href="javascript:void(0)">隐私政策</a>
        </div>
      </div>

      <div class="right">
        <div class="art">📺</div>
        <h3>欢迎来到哔哩哔哩</h3>
        <p>一起看番、一起追更、一起干杯</p>
        <div class="tags">
          <span>弹幕</span><span>追番</span><span>三连</span>
        </div>
      </div>
    </div>
  </el-dialog>
</template>

<style scoped>
/* 把 Element Plus 弹窗自带的内边距去掉，换成我们自己的两栏布局 */
:deep(.el-dialog) {
  border-radius: 12px;
  overflow: hidden;
  padding: 0;
  box-shadow: 0 20px 60px rgba(0, 0, 0, 0.3);
}

:deep(.el-dialog__header) {
  display: none;
}

:deep(.el-dialog__body) {
  padding: 0;
}

.login-body {
  display: flex;
}

.left {
  flex: 1;
  padding: 26px 30px 24px;
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

.tabs {
  display: flex;
  gap: 20px;
  margin: 18px 0 16px;
  border-bottom: 1px solid var(--line);
}

.tabs button {
  padding: 6px 2px 10px;
  font-size: 15px;
  color: var(--text-2);
  border-bottom: 2px solid transparent;
  margin-bottom: -1px;
  transition: color 0.2s;
}

.tabs button.on {
  color: var(--bili-pink);
  border-color: var(--bili-pink);
  font-weight: 600;
}

.qr-area {
  text-align: center;
}

.qr {
  position: relative;
  width: 168px;
  height: 168px;
  margin: 6px auto 10px;
  border: 1px solid var(--line);
  border-radius: 8px;
  padding: 10px;
  background: #fff;
}

.qr-grid {
  display: grid;
  grid-template-columns: repeat(21, 1fr);
  gap: 1px;
  width: 100%;
  height: 100%;
}

.qr-grid span {
  background: transparent;
  border-radius: 1px;
}

.qr-grid span.on {
  background: #1d1d1f;
}

.qr-logo {
  position: absolute;
  left: 50%;
  top: 50%;
  transform: translate(-50%, -50%);
  background: var(--bili-pink);
  color: #fff;
  font-size: 12px;
  font-weight: 700;
  padding: 4px 8px;
  border-radius: 6px;
  border: 3px solid #fff;
}

.tip {
  margin: 6px 0 0;
  font-size: 13px;
  color: var(--text-2);
}

.tip.small {
  font-size: 12px;
  color: var(--text-3);
}

.sms-area .field {
  display: flex;
  align-items: center;
  height: 42px;
  border-radius: 8px;
  background: #f4f5f7;
  padding: 0 12px;
  margin-bottom: 12px;
}

.sms-area .prefix {
  color: var(--text-2);
  margin-right: 10px;
}

.sms-area input {
  flex: 1;
  border: none;
  background: transparent;
  color: var(--text-1);
}

.code-btn {
  color: var(--bili-blue);
  font-size: 13px;
}

.primary {
  width: 100%;
  height: 42px;
  margin-top: 16px;
  border-radius: 8px;
  background: var(--bili-pink);
  color: #fff;
  font-size: 15px;
  font-weight: 600;
  transition: background 0.2s, transform 0.2s;
}

.primary:hover:not(:disabled) {
  background: var(--bili-pink-hover);
}

.primary:active:not(:disabled) {
  transform: scale(0.98);
}

.primary:disabled {
  opacity: 0.7;
  cursor: default;
}

.agreement {
  margin-top: 12px;
  font-size: 12px;
  color: var(--text-3);
  text-align: center;
}

.agreement a {
  color: var(--bili-blue);
}

.right {
  width: 250px;
  background: linear-gradient(160deg, #ffe3ec, #d8f1ff);
  padding: 30px 22px;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  text-align: center;
}

.right .art {
  font-size: 56px;
  animation: float 3s ease-in-out infinite;
}

@keyframes float {
  0%,
  100% {
    transform: translateY(0);
  }
  50% {
    transform: translateY(-8px);
  }
}

.right h3 {
  margin: 14px 0 6px;
  font-size: 17px;
  color: #33384d;
}

.right p {
  margin: 0 0 12px;
  font-size: 13px;
  color: #6b7280;
}

.tags {
  display: flex;
  gap: 6px;
}

.tags span {
  font-size: 11px;
  color: #6b7280;
  background: rgba(255, 255, 255, 0.7);
  border-radius: 999px;
  padding: 2px 10px;
}

@media (max-width: 640px) {
  .right {
    display: none;
  }
}
</style>
