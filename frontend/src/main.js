import { createApp } from 'vue'
import { createPinia } from 'pinia'
import App from './App.vue'
import router from './router'
import { reveal } from './directives/reveal'
import './styles/global.css'
// Element Plus 的基础变量与暗色/亮色主题变量（组件样式依然按需引入）
import 'element-plus/theme-chalk/base.css'

const app = createApp(App)

app.use(createPinia())
app.use(router)

// 全局注册 v-reveal 指令：元素滚进视口时淡入
app.directive('reveal', reveal)

app.mount('#app')
