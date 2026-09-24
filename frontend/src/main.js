// 浏览器兼容 polyfill（放在所有业务代码之前执行）
import './polyfills'

import { createApp } from 'vue'
import { createPinia } from 'pinia'
import App from './App.vue'
import router from './router'
import { reveal } from './directives/reveal'
import { registerIcons } from './icons'
import './styles/global.css'

const app = createApp(App)

// 全局错误兜底：某个页面报错时不要让整站白屏
app.config.errorHandler = (err, instance, info) => {
  console.error('[Vue 错误]', info, err)
}

app.use(createPinia())
app.use(router)

// 统一图标库：a哩a哩 自绘图标（按需注册，见 icons.js）
registerIcons(app)

// 全局注册 v-reveal 指令：元素滚进视口时淡入
app.directive('reveal', reveal)

app.mount('#app')
