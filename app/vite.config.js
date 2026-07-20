import { defineConfig } from 'vite'
import uni from '@dcloudio/vite-plugin-uni'
import path from 'path'
import { fileURLToPath } from 'url'

const __dirname = path.dirname(fileURLToPath(import.meta.url))
// Windows 路径转正斜杠，供 Sass @import 使用
const uviewTheme = path
  .resolve(__dirname, 'node_modules/uview-plus/theme.scss')
  .replace(/\\/g, '/')

export default defineConfig({
  plugins: [uni()],
  css: {
    preprocessorOptions: {
      scss: {
        // 全局注入 flex 等 mixin，修复 uview-plus 组件 Undefined mixin
        additionalData: `@import "${uviewTheme}";\n`,
        silenceDeprecations: ['legacy-js-api', 'import', 'color-functions', 'global-builtin']
      }
    }
  },
  server: {
    port: 5180,
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true,
        rewrite: (p) => p.replace(/^\/api/, '')
      }
    }
  }
})
