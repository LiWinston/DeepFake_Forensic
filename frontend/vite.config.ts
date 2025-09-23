import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'
import { configDefaults } from 'vitest/config'

// https://vite.dev/config/
export default defineConfig({
  plugins: [react()],
  test: {
    environment: 'jsdom',
    include: ['Test/**/*.test.{ts,tsx}'],
    exclude: [...configDefaults.exclude],
    setupFiles: ['./Test/setup.ts'],
    coverage: {
      provider: 'v8',
      // 让控制台和 PR 评论更清晰 + CI 可上传
      reporter: ['text', 'text-summary', 'json-summary', 'html', 'lcov'],
      reportsDirectory: './coverage',
      // 只统计 src 下的业务代码
      include: ['src/**/*.{ts,tsx}'],
      // 明确排除不会或暂不测试的文件，避免把总覆盖率“稀释”得过低
      exclude: [
        // 基础排除
        'coverage/**',
        'dist/**',
        'node_modules/**',
        'Test/**',
        '**/*.test.{ts,tsx}',
        '**/*.config.{ts,js}',
        '**/*.d.ts',
        'src/vite-env.d.ts',
        // 入口文件通常不做单测
        'src/main.tsx',
        'src/App.tsx',
        // 如有“超大容器页/纯路由页”暂时不测，可先排除，等写上测试再移除：
        // 'src/pages/**',
      ],
      // 是否把“未被测试导入的文件”也记为 0%（默认 false）
      // 你现在已经能看到很多 0%，说明此前可能已经是 all=true 的效果；
      // 这里建议先关闭，避免总覆盖率过低影响门槛推进。
      all: false,
      // 质量门（先定一个可达的起点，后续逐步上调到 Lines≥80%、Branches≥70%）
      thresholds: {
        global: {
          lines: 40,
          functions: 40,
          branches: 35,
          statements: 40,
        },
      },
      // 让阈值未达时在 CI 里直接失败（Vitest 默认就会根据 thresholds 失败）
      // reportOnFailure: true, // 可选
    },
  },
  server: {
    port: 3000,
    host: true,
    proxy: {
      '/api': {
        target: 'http://localhost:8082',
        changeOrigin: true,
        secure: false,
      },
    },
  },
  build: {
    outDir: 'dist',
    sourcemap: true,
    chunkSizeWarningLimit: 1600,
  },
  define: {
    global: 'globalThis',
  },
})
