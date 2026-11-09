import { defineConfig, loadEnv } from 'vite'
import vue from '@vitejs/plugin-vue'
import { fileURLToPath, URL } from 'node:url'
import { rmSync } from 'node:fs'
import { resolve } from 'node:path'

export default defineConfig(({ mode }) => {
  const frontendRoot = fileURLToPath(new URL('.', import.meta.url))
  const env = loadEnv(mode, frontendRoot, '')
  const target = env.VITE_BACKEND_TARGET || 'http://127.0.0.1:8080'
  const outDir = resolve(frontendRoot, env.VITE_BUILD_OUT_DIR || 'dist')
  return {
    plugins: [
      {
        name: 'clean-generated-assets',
        apply: 'build',
        buildStart() {
          rmSync(resolve(outDir, 'assets'), {
            recursive: true,
            force: true
          })
        }
      },
      vue()
    ],
    base: '/petHome/',
    resolve: {
      alias: {
        '@': fileURLToPath(new URL('./src', import.meta.url))
      }
    },
    server: {
      port: 5173,
      proxy: {
        '/petHome/api': { target, changeOrigin: true },
        '/petHome/uploads': { target, changeOrigin: true }
      }
    },
    // 预览生产包时同样需要转发接口与图片，否则本地无法验证真实页面
    preview: {
      port: 4173,
      proxy: {
        '/petHome/api': { target, changeOrigin: true },
        '/petHome/uploads': { target, changeOrigin: true }
      }
    },
    build: {
      outDir,
      assetsDir: 'assets',
      emptyOutDir: true,
      sourcemap: false
    }
  }
})
