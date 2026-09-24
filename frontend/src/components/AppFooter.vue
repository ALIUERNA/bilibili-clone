<script setup>
import AiLogo from '../ui/AiLogo.vue'
</script>

<template>
  <footer class="footer">
    <div class="footer-inner">
      <div class="brand">
        <AiLogo class="brand-logo" :size="26" wordmark :text-size="19" />
        <p>一起看番、一起追更、一起发弹幕。</p>
      </div>

      <div class="bottom">
        <span>© 2024 a哩a哩</span>
      </div>
    </div>
  </footer>
</template>

<style scoped>
.footer {
  margin-top: 48px;
  background: var(--surface-2);
  border-top: 1px solid var(--line);
  padding: 32px 0 20px;
}

.footer-inner {
  max-width: 1400px;
  margin: 0 auto;
  padding: 0 20px;
  display: flex;
  flex-wrap: wrap;
  align-items: flex-end;
  justify-content: space-between;
  gap: 16px 40px;
}

.brand {
  min-width: 260px;
}

.brand-logo {
  margin-bottom: 12px;
}

.brand p {
  margin: 0;
  color: var(--text-3);
  font-size: 13px;
  line-height: 1.7;
}

.bottom {
  color: var(--text-3);
  font-size: 12px;
}
</style>
