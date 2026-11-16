import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import type { Plugin } from 'vite'
import { localMinecraftTextures } from './dev-server/minecraftTextures.ts'
import { fileURLToPath, URL } from 'node:url'
import path from 'node:path'

const projectRoot = fileURLToPath(new URL('.', import.meta.url))
const packagesRoot = path.resolve(projectRoot, '..', 'packages')
const sharedRoot = path.resolve(projectRoot, '..', 'assets')
const uiRoot = path.resolve(packagesRoot, 'ui')

function sharedAssetsInDevHtml(): Plugin {
  const devPrefix = `/@fs/${sharedRoot.split(path.sep).join('/')}/`
  return {
    name: 'pretupid-shared-assets-in-dev-html',
    apply: 'serve',
    transformIndexHtml: (html) => html.replaceAll('src="../assets/', `src="${devPrefix}`)
  }
}

export default defineConfig({
  plugins: [vue(), sharedAssetsInDevHtml(), localMinecraftTextures()],
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