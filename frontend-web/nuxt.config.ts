export default defineNuxtConfig({
  compatibilityDate: '2026-09-12',
  devtools: { enabled: false },
  ssr: true,
  nitro: {
    preset: 'cloudflare-pages',
    output: {
      dir: '.output'
    }
  },
  css: ['~/assets/styles/main.scss'],
  runtimeConfig: {
    public: {
      downloadUrl: 'PLACEHOLDER_DOWNLOAD_URL'
    }
  },
  app: {
    head: {
      title: 'Pretupid Launcher',
      meta: [
        { charset: 'utf-8' },
        { name: 'viewport', content: 'width=device-width, initial-scale=1' },
        { name: 'description', content: 'Pretupid Launcher — The modern Minecraft launcher built with Tauri and Rust.' }
      ],
      link: [
        { rel: 'preconnect', href: 'https://fonts.googleapis.com' },
        { rel: 'preconnect', href: 'https://fonts.gstatic.com', crossorigin: '' },
        { rel: 'stylesheet', href: 'https://fonts.googleapis.com/css2?family=Press+Start+2P&family=VT323&display=swap' }
      ]
    }
  }
})