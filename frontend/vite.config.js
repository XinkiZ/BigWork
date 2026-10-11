import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

// 开发服务器：5173 端口，/api 反向代理到后端 8080
export default defineConfig({
  plugins: [vue()],
  server: {
    port: 5173,
    strictPort: false,
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true
      }
    }
  }
})