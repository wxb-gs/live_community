import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'
import tailwindcss from '@tailwindcss/vite'

export default defineConfig({
  plugins: [react(), tailwindcss()],
  server: {
    port: 5173,
    proxy: {
      '/api': 'http://localhost:8080',
      '/minio': {
        target: 'http://localhost:19000',
        changeOrigin: true,
        rewrite: (path) => path.replace(/^\/minio/, ''),
        configure: (proxy) => {
          proxy.on('proxyReq', (proxyReq) => {
            proxyReq.setHeader('Host', 'minio:9000');
          });
        },
      },
    },
  },
})
