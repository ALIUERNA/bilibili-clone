<script setup>
const columns = [
  {
    title: '哔哩哔哩',
    links: ['关于我们', '联系我们', '用户协议', '隐私政策', '意见反馈']
  },
  {
    title: '传送门',
    links: ['动画', '番剧', '游戏中心', '直播', '会员购']
  },
  {
    title: '帮助中心',
    links: ['帮助中心', '客服中心', '举报中心', '活动中心', '创作激励']
  },
  {
    title: '合作伙伴',
    links: ['合作方式', '开发者平台', '开放平台', '商务合作', '加入我们']
  }
]
</script>

<template>
  <footer class="footer">
    <div class="footer-inner">
      <div class="cols">
        <div v-for="col in columns" :key="col.title" class="col">
          <h4>{{ col.title }}</h4>
          <ul>
            <li v-for="link in col.links" :key="link">
              <a href="javascript:void(0)">{{ link }}</a>
            </li>
          </ul>
        </div>
        <div class="col brand">
          <div class="brand-logo">哔哩哔哩</div>
          <p>
            这是一个<b>学习用的仿站项目</b>，页面结构与交互参考哔哩哔哩，
            由 Vue 3 + Spring Boot 实现，与哔哩哔哩官方没有任何关系。
          </p>
          <p class="small">演示数据全部由程序生成，不含任何真实视频内容。</p>
        </div>
      </div>

      <div class="bottom">
        <span>© 2024 bilibili-clone · 仅供学习交流使用</span>
        <span class="sep">|</span>
        <span>Vue 3 + Vite + Pinia + Spring Boot 3</span>
      </div>
    </div>
  </footer>
</template>

<style scoped>
.footer {
  margin-top: 48px;
  background: #fafafa;
  border-top: 1px solid var(--line);
  padding: 32px 0 20px;
}

.footer-inner {
  max-width: 1400px;
  margin: 0 auto;
  padding: 0 20px;
}

.cols {
  display: flex;
  flex-wrap: wrap;
  gap: 40px;
  padding-bottom: 24px;
  border-bottom: 1px solid var(--line);
}

.col h4 {
  margin: 0 0 12px;
  font-size: 15px;
  color: var(--text-1);
}

.col li {
  margin-bottom: 8px;
}

.col a {
  color: var(--text-3);
  font-size: 13px;
}

.col a:hover {
  color: var(--bili-blue);
}

.brand {
  flex: 1;
  min-width: 260px;
}

.brand-logo {
  font-size: 20px;
  font-weight: 700;
  color: var(--bili-pink);
  margin-bottom: 10px;
}

.brand p {
  margin: 0 0 8px;
  color: var(--text-3);
  font-size: 13px;
  line-height: 1.7;
}

.brand .small {
  font-size: 12px;
}

.bottom {
  display: flex;
  align-items: center;
  gap: 10px;
  padding-top: 16px;
  color: var(--text-3);
  font-size: 12px;
}

.sep {
  color: var(--line);
}
</style>
