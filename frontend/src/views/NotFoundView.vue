<script setup>
/** 404 页面：给出入口，不会让用户卡在空白页 */
import { onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { api } from '../api'
import VideoCard from '../components/VideoCard.vue'
import { nodeIcon } from '../icons'

const route = useRoute()
const router = useRouter()

const nodes = ref([])
const hot = ref([])

onMounted(async () => {
  try {
    const [n, h] = await Promise.all([api.nodes().catch(() => []), api.videos({ category: 'hot', page: 1, size: 4 }).catch(() => null)])
    nodes.value = n || []
    hot.value = h?.items || []
  } catch (e) {
    /* 后端不可用也不影响 404 页面渲染 */
  }
})
</script>

<template>
  <div class="not-found container">
    <div class="art">
      <span class="code">404</span>
      <span class="face">(゜-゜)</span>
    </div>
    <h1>你访问的页面不存在</h1>
    <p class="path">路径：<code>{{ route.fullPath }}</code></p>
    <p class="sub">可能是链接输错了，也可能是这个页面还没做 —— 不过下面这些入口都可以点。</p>

    <div class="actions">
      <button class="btn btn-primary btn-round" @click="router.push('/')">回首页</button>
      <button class="btn btn-ghost btn-round" @click="router.push('/ranking')">排行榜</button>
      <button class="btn btn-ghost btn-round" @click="router.back()">返回上一页</button>
    </div>

    <section v-if="nodes.length" class="nodes">
      <h3>内容节点</h3>
      <div class="chips">
        <button v-for="n in nodes" :key="n.code" class="chip" @click="router.push(n.routePath || `/node/${n.code}`)">
          <AiIcon><component :is="nodeIcon(n.code)" /></AiIcon> {{ n.name }}
        </button>
      </div>
    </section>

    <section v-if="hot.length" class="hot">
      <h3>热门推荐</h3>
      <div class="grid">
        <VideoCard v-for="v in hot" :key="v.id" :video="v" />
      </div>
    </section>
  </div>
</template>

<style scoped>
.not-found {
  padding: 50px 20px 60px;
  text-align: center;
}

.art {
  display: flex;
  align-items: baseline;
  justify-content: center;
  gap: 14px;
}

.code {
  font-size: 78px;
  font-weight: 800;
  color: var(--bili-pink);
  letter-spacing: 4px;
  text-shadow: 0 8px 24px rgba(110, 86, 248, 0.3);
}

.face {
  font-size: 26px;
  color: var(--text-3);
}

h1 {
  margin: 10px 0 8px;
  font-size: 22px;
}

.path code {
  background: var(--surface-0);
  padding: 2px 8px;
  border-radius: 4px;
}

.sub {
  color: var(--text-3);
  font-size: 13px;
}

.actions {
  display: flex;
  gap: 12px;
  justify-content: center;
  margin: 22px 0 30px;
  flex-wrap: wrap;
}

.nodes,
.hot {
  margin-top: 26px;
  text-align: left;
}

.nodes h3,
.hot h3 {
  font-size: 16px;
  margin: 0 0 12px;
}

.chips {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  justify-content: center;
}

.chip {
  padding: 6px 12px;
  border-radius: 999px;
  background: #fff;
  border: 1px solid var(--line);
  font-size: 13px;
  color: var(--text-2);
}

.chip:hover {
  border-color: var(--bili-pink);
  color: var(--bili-pink);
}

.grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(220px, 1fr));
  gap: 18px 16px;
}
</style>
