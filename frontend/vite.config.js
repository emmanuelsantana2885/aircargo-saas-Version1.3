import { fileURLToPath, URL } from 'node:url'
import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

export default defineConfig({
  plugins: [
    vue(),
  ],
  server: {
    host: process.env.VITE_HOST || '127.0.0.1', // no exponer a la LAN por defecto
    port: 5173,
    proxy: {
      '/api/': {
        target: 'http://localhost:8080',
        changeOrigin: true,
        secure: false,
      }
    }
  },
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url))
    },
  },
  build: {
    target: 'es2020',
    rollupOptions: {
      output: {
        manualChunks(id) {
          if (!id.includes('node_modules')) return
          if (id.includes('/vue/') || id.includes('/@vue/') || id.includes('/vue-router/')
              || id.includes('/pinia/') || id.includes('/vue-i18n/')) return 'vue-vendor'
          if (id.includes('/axios/')) return 'http'
          if (id.includes('/xlsx/')) return 'xlsx'
          if (id.includes('/date-fns/')) return 'dates'
        }
      }
    }
  },
  test: {
    environment: 'happy-dom',
    globals: true,
  },
})