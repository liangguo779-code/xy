import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import { resolve } from 'path'

/** Vite 配置：Vue 插件、@ 别名与开发代理 */
export default defineConfig({
  plugins: [vue()],
  resolve: {
    alias: {
      // @ 指向 src，便于绝对路径导入
      '@': resolve(__dirname, 'src')
    }
  },
  server: {
    // 监听所有网卡，方便局域网内其他设备（如手机）联调
    host: '0.0.0.0',
    port: 5173,
    // 开发代理：把接口与静态资源、WebSocket 转发到本地后端（9000），规避跨域
    proxy: {
      '/api': {
        target: 'http://localhost:9000',
        changeOrigin: true
      },
      '/uploads': {
        target: 'http://localhost:9000',
        changeOrigin: true
      },
      // WebSocket 需开启 ws 才能完成协议升级转发
      '/ws': {
        target: 'ws://localhost:9000',
        ws: true
      }
    }
  }
})
