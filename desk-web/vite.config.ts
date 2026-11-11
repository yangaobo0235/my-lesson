import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

export default defineConfig({
  plugins: [vue()],
  server: {
    host: '127.0.0.1',
    port: 28199,
    strictPort: true,
    proxy: { '/api': 'http://127.0.0.1:28101' },
  },
})
