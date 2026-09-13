import { createRouter, createWebHashHistory, type Router } from 'vue-router'

const router = createRouter({
  history: createWebHashHistory(),
  routes: [
    {
      path: '/',
      name: 'home',
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
      path: '/modrinth',
      name: 'modrinth',
      component: () => import('../views/ModrinthView.vue')
    },
    {
      path: '/instance/:id',
      name: 'instance-detail',
      component: () => import('../views/InstanceDetailView.vue')
    }
  ]
})

declare global {
  interface Window {
    __pretupidRouter?: Router
  }
}

window.__pretupidRouter = router

window.addEventListener('pretupid:navigate', (event) => {
  const target = (event as CustomEvent<string>).detail
  if (typeof target === 'string' && target.length > 0) {
    router.push(target).catch(() => void 0)
  }
})

export default router