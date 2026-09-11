import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import AutoImport from 'unplugin-auto-import/vite'
import Components from 'unplugin-vue-components/vite'
import { ElementPlusResolver } from 'unplugin-vue-components/resolvers'

// Vite 配置
// 开发时：npm run dev  →  启动 5173 端口，/api 自动代理到 8080 后端
// 打包时：npm run build →  产物在 dist，会被复制到后端的 static 目录里
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
      resolvers: [ElementPlusResolver()],
      dts: false
    })
  ],
  server: {
    port: 5173,
    open: true,
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
