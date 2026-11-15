import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import { fileURLToPath, URL } from 'node:url'
import path from 'node:path'

const projectRoot = fileURLToPath(new URL('.', import.meta.url))
const packagesRoot = path.resolve(projectRoot, '..', 'packages')
const sharedRoot = path.resolve(projectRoot, '..', 'assets')
const uiRoot = path.resolve(packagesRoot, 'ui')

export default defineConfig({
  plugins: [vue()],
  resolve: {
    dedupe: ['vue'],
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url)),
      '@shared': sharedRoot,
      '@ui': uiRoot
    }
  },
  css: {
    preprocessorOptions: {
      scss: {
        loadPaths: [packagesRoot]
      }
    }
  },
  clearScreen: false,
  build: {
    chunkSizeWarningLimit: 600
  },
  server: {
    port: 1420,
    strictPort: true,
    fs: {
      allow: [projectRoot, sharedRoot, packagesRoot]
    }
  }
})