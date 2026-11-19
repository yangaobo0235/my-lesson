import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import { localAcceptance } from './vite/acceptance.mjs'

export default defineConfig({
  plugins: [vue(), localAcceptance()],
  server: {
    host: '127.0.0.1',
    port: 26199,
    strictPort: true,
    proxy: { '/api': 'http://127.0.0.1:26101' },
  },
})
