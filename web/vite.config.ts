import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import { fileURLToPath, URL } from 'node:url'

export default defineConfig({
  plugins: [vue()],
  resolve: {
    alias: { '@': fileURLToPath(new URL('./src', import.meta.url)) }
  },
  server: {
    host: '0.0.0.0',
    port: 5173,
    proxy: {
      // 后端 context-path 就是 /api，因此原样转发，不 rewrite
      '/api': {
        target: 'http://localhost:8106',
        changeOrigin: true
      },
      '/static': {
        target: 'http://localhost:8106',
        changeOrigin: true
      }
    }
  }
})
