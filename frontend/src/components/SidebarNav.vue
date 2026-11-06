<template>
  <aside class="sidebar">
    <RouterLink to="/" class="sidebar__logo" aria-label="Pretupid Launcher home">
      <img src="@/assets/branding/ptd_logo_main.webp" alt="" />
      <span class="sidebar__logo-text">Pretupid</span>
    </RouterLink>

    <nav class="sidebar__nav" aria-label="Main">
      <RouterLink
        v-for="item in items"
        :key="item.to"
        :to="item.to"
        class="sidebar__link"
        :class="{ 'sidebar__link--active': isActive(item.to) }"
      >
        <AppIcon :name="item.icon" :size="18" />
        <span class="sidebar__link-text">
          <span class="sidebar__link-label">{{ item.label }}</span>
          <span class="sidebar__link-hint">{{ item.hint }}</span>
        </span>
      </RouterLink>
    </nav>

    <RouterLink
      v-if="playing"
      :to="{ name: 'instance-detail', params: { id: playing.id } }"
      class="sidebar__playing"
    >
      <span class="sidebar__playing-dot" />
      <span class="sidebar__playing-text">
        <span class="sidebar__playing-label">Now playing</span>
        <span class="sidebar__playing-name">{{ playing.name }}</span>
      </span>
    </RouterLink>

    <div class="sidebar__bottom">
      <div v-if="authStore.authenticated && authStore.profile" class="sidebar__account">
        <img
          class="sidebar__avatar"
          :src="`https://mc-heads.net/avatar/${authStore.profile.id}/64`"
          alt=""
          loading="lazy"
        />
        <div class="sidebar__account-meta">
          <span class="sidebar__account-name">{{ authStore.profile.name }}</span>
          <span class="sidebar__account-type">Microsoft account</span>
        </div>
        <button class="icon-btn icon-btn--small" title="Sign out" aria-label="Sign out" @click="handleLogout">
          <AppIcon name="logout" :size="16" />
        </button>
      </div>
      <button v-else class="btn btn--small btn--block" :disabled="authStore.loading" @click="handleLogin">
        <AppIcon name="login" :size="14" />
        {{ authStore.loading ? 'Signing in' : 'Sign in' }}
      </button>

      <button class="sidebar__settings" @click.stop="$emit('open-settings')">
        <AppIcon name="settings" :size="16" />
        Settings
      </button>
    </div>
  </aside>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { RouterLink, useRoute } from 'vue-router'
import AppIcon, { type IconName } from '@/components/AppIcon.vue'
import { useAuthStore } from '@/stores/auth'
import { useNotificationsStore } from '@/stores/notifications'
import { useLaunchStore } from '@/stores/launch'
import { useInstanceStore } from '@/stores/instances'

defineEmits<{ (e: 'open-settings'): void }>()

const authStore = useAuthStore()
const notifications = useNotificationsStore()
const launchStore = useLaunchStore()
const instanceStore = useInstanceStore()
const route = useRoute()

const items: { to: string; label: string; hint: string; icon: IconName }[] = [
  { to: '/', label: 'Home', hint: 'Jump back in', icon: 'home' },
  { to: '/library', label: 'Library', hint: 'Your instances', icon: 'library' },
  { to: '/browse', label: 'Browse', hint: 'Mods & modpacks', icon: 'compass' },
  { to: '/profile', label: 'Skins', hint: 'Look & profile', icon: 'user' }
]

function isActive(path: string): boolean {
  if (path === '/') return route.path === '/'
  if (path === '/library') return route.path.startsWith('/library') || route.path.startsWith('/instance')
  return route.path.startsWith(path)
}

const playing = computed(() => {
  for (const [id, state] of Object.entries(launchStore.states)) {
    if (state.running) {
      return { id, name: instanceStore.byId(id)?.name ?? 'Minecraft' }
    }
  }
  return null
})

async function handleLogin() {
  try {
    await authStore.login()
  } catch (e) {
    notifications.notifyError('Sign-in failed', String(e))
  }
}

async function handleLogout() {
  await authStore.logout()
  await authStore.check()
  notifications.notifyInfo('Signed out', 'Your Microsoft account has been disconnected.')
}
</script>

<style scoped lang="scss">
.sidebar {
  width: 232px;
  height: 100vh;
  flex-shrink: 0;
  display: flex;
  flex-direction: column;
  gap: 1rem;
  padding: 1.25rem 0.85rem;
  overflow-y: auto;
  background-color: var(--bg-secondary);
  border-right: 2px solid var(--border-color);
}

.sidebar__logo {
  display: flex;
  align-items: center;
  gap: 0.75rem;
  padding: 0 0.35rem 0.5rem;
  color: inherit;
  text-decoration: none;

  img {
    width: 38px;
    height: 38px;
    border-radius: var(--edge-radius);
  }
}

.sidebar__logo-text {
  font-family: var(--font-pixel);
  font-size: 0.95rem;
  color: var(--accent);
}

.sidebar__nav {
  display: flex;
  flex-direction: column;
  gap: 0.3rem;
}

.sidebar__link {
  display: flex;
  align-items: center;
  gap: 0.8rem;
  padding: 0.6rem 0.75rem;
  color: var(--text-secondary);
  text-decoration: none;
  border: 2px solid transparent;
  border-radius: var(--edge-radius);
  transition: background-color var(--transition-fast), color var(--transition-fast), border-color var(--transition-fast);

  &:hover {
    color: var(--text-primary);
    background-color: var(--bg-tertiary);
  }
}

.sidebar__link--active {
  color: var(--text-primary);
  background-color: var(--bg-tertiary);
  border-color: var(--border-color);
  box-shadow: inset 3px 0 0 0 var(--accent);

  .app-icon {
    color: var(--accent);
  }
}

.sidebar__link-text {
  display: flex;
  flex-direction: column;
  gap: 0.2rem;
  min-width: 0;
}

.sidebar__link-label {
  font-family: var(--font-pixel);
  font-size: 0.62rem;
  text-transform: uppercase;
}

.sidebar__link-hint {
  font-size: 0.9rem;
  line-height: 1;
  color: var(--text-secondary);
}

.sidebar__playing {
  display: flex;
  align-items: center;
  gap: 0.6rem;
  padding: 0.6rem 0.75rem;
  color: inherit;
  text-decoration: none;
  background-color: var(--bg-tertiary);
  border: 2px solid #2ecc71;
  border-radius: var(--edge-radius);
}

.sidebar__playing-dot {
  width: 10px;
  height: 10px;
  flex-shrink: 0;
  background-color: #2ecc71;
  animation: sidebar-pulse 1.4s ease-in-out infinite;
}

.sidebar__playing-text {
  display: flex;
  flex-direction: column;
  min-width: 0;
}

.sidebar__playing-label {
  font-family: var(--font-pixel);
  font-size: 0.5rem;
  text-transform: uppercase;
  color: #2ecc71;
}

.sidebar__playing-name {
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
}

.sidebar__bottom {
  margin-top: auto;
  display: flex;
  flex-direction: column;
  gap: 0.5rem;
}

.sidebar__account {
  display: flex;
  align-items: center;
  gap: 0.6rem;
  padding: 0.55rem;
  background-color: var(--bg-tertiary);
  border: 2px solid var(--border-color);
  border-radius: var(--edge-radius);
}

.sidebar__avatar {
  width: 32px;
  height: 32px;
  flex-shrink: 0;
  image-rendering: pixelated;
  border-radius: var(--edge-radius);
}

.sidebar__account-meta {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 0.15rem;
}

.sidebar__account-name {
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
  font-family: var(--font-pixel);
  font-size: 0.55rem;
  color: var(--accent);
}

.sidebar__account-type {
  font-size: 0.85rem;
  line-height: 1;
  color: var(--text-secondary);
}

.sidebar__settings {
  display: flex;
  align-items: center;
  gap: 0.6rem;
  width: 100%;
  padding: 0.55rem 0.75rem;
  font-family: var(--font-pixel);
  font-size: 0.55rem;
  text-transform: uppercase;
  color: var(--text-secondary);
  background: none;
  border: 2px solid transparent;
  border-radius: var(--edge-radius);
  cursor: pointer;

  &:hover {
    color: var(--text-primary);
    border-color: var(--border-color);
  }
}

@keyframes sidebar-pulse {
  50% {
    opacity: 0.35;
  }
}
</style>
