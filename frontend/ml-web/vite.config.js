import {defineConfig} from 'vite'
import vue from '@vitejs/plugin-vue'

// https://vitejs.dev/config/
export default defineConfig({
  plugins: [vue()],
  build: {
    rollupOptions: {
      output: {
        manualChunks(id) {
          if (!id.includes('node_modules')) return;
          if (id.includes('element-plus') || id.includes('@element-plus')) return 'element-plus';
          if (id.includes('echarts') || id.includes('zrender')) return 'echarts';
          if (id.includes('xgplayer')) return 'xgplayer';
          if (id.includes('vue')) return 'vue';
          return 'vendor';
        },
      },
    },
  },

  // 配置用户前台项目的IP和端口号
  server: {
    host: 'localhost',
    port: 24108,
  }
})
