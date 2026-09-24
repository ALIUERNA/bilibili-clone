import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import legacy from '@vitejs/plugin-legacy'
import AutoImport from 'unplugin-auto-import/vite'
import Components from 'unplugin-vue-components/vite'
import { ElementPlusResolver } from 'unplugin-vue-components/resolvers'

// Vite 配置
// 开发时：npm run dev  →  启动 5173 端口，/api 自动代理到 8080 后端
// 打包时：npm run build →  产物在 dist，会被复制到后端的 static 目录里
//
// 浏览器兼容：
//  1. browserslist（package.json）+ Autoprefixer（postcss.config.js）自动加 CSS 前缀；
//  2. build.target 指定现代浏览器基线（es2018 ≈ Chrome 63+ / Edge 79+ / Firefox 58+ / Safari 12+）；
//  3. @vitejs/plugin-legacy 额外生成一份 legacy 包（nomodule + core-js polyfill），
//     老浏览器自动加载它，不会白屏。
export default defineConfig({
  plugins: [
    vue(),
    // Element Plus 按需引入：只有真正用到的组件和样式才会进打包结果
    AutoImport({
      imports: ['vue', 'vue-router', '@vueuse/core'],
      resolvers: [ElementPlusResolver()],
      dts: false,
      eslintrc: { enabled: false }
    }),
    Components({
      // src/ui 是自研的基础组件库（AiModal / AiProgress / AiEmpty / AiSkeleton …），
      // 必须显式写进 dirs：unplugin 默认只扫 src/components，
      // 少了这一项的话模板里的 <AiModal> 不会被解析成组件，
      // Vue 会把它当成未知标签原样渲染成一个 <aimodal> 元素——
      // 弹窗于是变成页面底部的一块普通内容（不报错、不白屏，最难查）。
      dirs: ['src/components', 'src/ui'],
      resolvers: [ElementPlusResolver()],
      dts: false
    }),
    legacy({
      targets: ['chrome >= 70', 'edge >= 79', 'firefox >= 68', 'safari >= 12', 'ios >= 12'],
      // 只为老浏览器补 polyfill，现代浏览器加载的包不受影响
      modernPolyfills: false,
      renderLegacyChunks: true,
      additionalLegacyPolyfills: ['regenerator-runtime/runtime'],
      // 让 legacy 包体积小一点
      polyfills: ['es.promise.finally', 'es.array.flat', 'es.object.from-entries', 'es.string.trim-start']
    })
  ],
  server: {
    port: 5173,
    open: true,
    host: true,
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true
      }
    }
  },
  build: {
    outDir: 'dist',
    emptyOutDir: true,
    chunkSizeWarningLimit: 1500,
    target: 'es2018',
    cssTarget: ['chrome70', 'edge79', 'firefox68', 'safari12'],
    rollupOptions: {
      output: {
        // 把体积大的第三方库单独拆包，浏览器缓存更友好
        manualChunks(id) {
          if (!id.includes('node_modules')) return
          if (id.includes('element-plus')) return 'element-plus'
          if (id.includes('@vueuse')) return 'vueuse'
          if (id.includes('vue-router') || id.includes('pinia') || id.includes('/vue/')) return 'vue-vendor'
          return 'vendor'
        }
      }
    }
  },

  // 单元测试（Vitest）配置：node 环境跑纯逻辑，不需要浏览器
  test: {
    environment: 'node',
    include: ['src/**/*.{test,spec}.js'],
    // element-plus 是 node_modules 里的依赖，默认不经过 Vite 处理，
    // 它内部 import 的 .css 会报 "Unknown file extension"，这里让它走 Vite
    server: {
      deps: {
        inline: ['element-plus']
      }
    }
  }
})
