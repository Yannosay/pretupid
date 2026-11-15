import path from 'node:path'
import { fileURLToPath, URL } from 'node:url'

const projectRoot = fileURLToPath(new URL('.', import.meta.url))
const packagesRoot = path.resolve(projectRoot, '..', 'packages')
const sharedAssetsRoot = path.resolve(projectRoot, '..', 'assets')
const isProduction = process.env.NODE_ENV === 'production'

const securityHeaders = {
  'Content-Security-Policy':
    "default-src 'self'; script-src 'self' 'unsafe-inline'; style-src 'self' 'unsafe-inline' https://fonts.googleapis.com; font-src 'self' https://fonts.gstatic.com data:; img-src 'self' data: https:; connect-src 'self'; frame-ancestors 'none'; base-uri 'self'; form-action 'self'; object-src 'none'",
  'X-Content-Type-Options': 'nosniff',
  'X-Frame-Options': 'DENY',
  'Referrer-Policy': 'strict-origin-when-cross-origin',
  'Permissions-Policy': 'camera=(), microphone=(), geolocation=(), payment=(), usb=()',
  'Strict-Transport-Security': 'max-age=31536000; includeSubDomains',
  'Cross-Origin-Opener-Policy': 'same-origin'
}

export default defineNuxtConfig({
  compatibilityDate: '2026-09-12',
  devtools: { enabled: false },
  ssr: true,
  nitro: {
    preset: 'cloudflare-pages',
    cloudflare: {
      nodeCompat: true,
      deployConfig: false
    },
    output: {
      dir: '.output'
    }
  },
  alias: {
    '@shared': sharedAssetsRoot
  },
  css: ['~/assets/styles/main.scss'],
  routeRules: {
    ...(isProduction ? { '/**': { headers: securityHeaders } } : {}),
    '/admin': { ssr: false, headers: { ...(isProduction ? securityHeaders : {}), 'X-Robots-Tag': 'noindex, nofollow', 'Cache-Control': 'no-store' } },
    '/api/**': { headers: { 'X-Content-Type-Options': 'nosniff' } }
  },
  runtimeConfig: {
    presenceSecret: '',
    adminKey: '',
    adminSessionSecret: '',
    public: {
      repository: 'Yannosay/pretupid',
      siteUrl: 'https://pretupid.yannosay.com'
    }
  },
  vite: {
    css: {
      preprocessorOptions: {
        scss: {
          loadPaths: [packagesRoot]
        }
      }
    }
  },
  app: {
    head: {
      title: 'Pretupid Launcher',
      meta: [
        { charset: 'utf-8' },
        { name: 'viewport', content: 'width=device-width, initial-scale=1' },
        {
          name: 'description',
          content: 'Pretupid Launcher — a fast, safe Minecraft launcher with Fabric, Quilt, Forge and NeoForge, Modrinth mods and verified downloads.'
        }
      ],
      link: [
        { rel: 'preconnect', href: 'https://fonts.googleapis.com' },
        { rel: 'preconnect', href: 'https://fonts.gstatic.com', crossorigin: '' },
        {
          rel: 'stylesheet',
          href: 'https://fonts.googleapis.com/css2?family=Press+Start+2P&family=VT323&display=swap'
        }
      ]
    }
  }
})