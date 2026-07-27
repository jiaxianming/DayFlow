/// <reference types="vitest" />
import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import { fileURLToPath, URL } from 'node:url'

/**
 * Vite 构建配置
 * - dev server proxy：/api → 后端 localhost:8080（规避联调跨域）
 * - 路径别名 @ → src
 * - Vitest：jsdom 环境 + 同别名
 */
export default defineConfig({
  plugins: [vue()],
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url)),
    },
  },
  server: {
    port: 5173,
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true,
      },
    },
  },
  test: {
    environment: 'jsdom',
    globals: true,
    setupFiles: ['./src/setupTests.ts'],
    /**
     * 单 fork 进程串行执行测试文件
     * 默认 threads 多 worker 在 jsdom + Element Plus 组件 mount 负载下会 OOM 崩溃
     * （"Worker exited unexpectedly"），静默丢弃测试文件 → 计数飘忽、假"全量通过"。
     * 串行单进程保证确定性、无静默丢文件；本项目 10 文件 33 用例约 2s，并行无收益。
     */
    pool: 'forks',
    poolOptions: {
      forks: {
        singleFork: true,
      },
    },
  },
})
