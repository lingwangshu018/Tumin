import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'
import tailwindcss from '@tailwindcss/vite'

// GitHub Pages: 使用仓库名作为 base
// 如果使用自定义域名，改为 '/'
export default defineConfig({
  base: '/bennychat/',
  plugins: [
    react(),
    tailwindcss(),
  ],
})
