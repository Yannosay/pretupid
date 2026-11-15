import { createRouter, createWebHashHistory, type Router } from 'vue-router'
import { listen } from '@tauri-apps/api/event'

const router = createRouter({
  history: createWebHashHistory(),
  routes: [
    {
      path: '/',
      name: 'home',
      meta: { bleed: true },
      component: () => import('../views/HomeView.vue')
    },
    {
      path: '/library',
      name: 'library',
      component: () => import('../views/LibraryView.vue')
    },
    {
      path: '/profile',
      name: 'profile',
      component: () => import('../views/ProfileView.vue')
    },
    {
      path: '/browse',
      name: 'browse',
      component: () => import('../views/ModrinthView.vue')
    },
    {
      path: '/modrinth',
      redirect: '/browse'
    },
    {
      path: '/instance/:id',
      name: 'instance-detail',
      component: () => import('../views/InstanceDetailView.vue')
    },
    {
      path: '/:pathMatch(.*)*',
      redirect: '/'
    }
  ]
})

declare global {
  interface Window {
    __pretupidRouter?: Router
  }
}

window.__pretupidRouter = router

const ALLOWED_NAVIGATION = /^\/(library|browse|modrinth|profile|instance\/[0-9a-f-]{36})?$/

export async function registerNavigationListeners() {
  await listen<string>('main://navigate', (event) => {
    const target = event.payload
    if (typeof target === 'string' && ALLOWED_NAVIGATION.test(target)) {
      router.push(target).catch(() => void 0)
    }
  })
}

export default router
