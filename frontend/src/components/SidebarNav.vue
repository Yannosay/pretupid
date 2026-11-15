<template>
  <aside class="sidebar">
    <RouterLink to="/" class="sidebar__brand" aria-label="Pretupid Launcher home">
      <img src="@shared/branding/pretupid_logo_full.png" alt="Pretupid Launcher" class="sidebar__brand-logo" />
    </RouterLink>

    <nav class="sidebar__nav" aria-label="Main">
      <RouterLink
        v-for="item in items"
        :key="item.to"
        :to="item.to"
        class="sidebar__link"
        :class="{ 'sidebar__link--active': isActive(item.to) }"
        :aria-current="isActive(item.to) ? 'page' : undefined"
      >
        <AppIcon :name="item.icon" :size="18" />
        <span class="sidebar__link-label">{{ item.label }}</span>
      </RouterLink>
    </nav>

    <div class="sidebar__spacer" />

    <RouterLink
      v-if="playing"
      :to="{ name: 'instance-detail', params: { id: playing.id } }"
      class="sidebar__now"
    >
      <span class="sidebar__now-dot" />
      <span class="sidebar__now-text">
        <span class="sidebar__now-label">Now playing</span>
        <span class="sidebar__now-name">{{ playing.name }}</span>
      </span>
    </RouterLink>

    <div v-else-if="quickInstance && route.name !== 'home'" class="sidebar__quick">
      <RouterLink :to="{ name: 'instance-detail', params: { id: quickInstance.id } }" class="sidebar__quick-link" :title="quickInstance.name">
        <InstanceIcon :instance="quickInstance" :size="34" />
        <span class="sidebar__quick-text">
          <span class="sidebar__quick-label">Last played</span>
          <span class="sidebar__quick-name">{{ quickInstance.name }}</span>
        </span>
      </RouterLink>
      <PlayButton :instance-id="quickInstance.id" size="small" compact />
    </div>

    <div class="sidebar__footer">
      <div v-if="authStore.authenticated && authStore.profile" class="sidebar__account">
        <img class="sidebar__avatar" :src="`https://mc-heads.net/avatar/${authStore.profile.id}/64`" alt="" loading="lazy" />
        <span class="sidebar__account-name" :title="authStore.profile.name">{{ authStore.profile.name }}</span>
        <button class="sidebar__icon-btn" title="Sign out" aria-label="Sign out" @click="handleLogout">
          <AppIcon name="logout" :size="16" />
        </button>
      </div>
      <button v-else class="btn btn--small btn--block" :disabled="authStore.loading" @click="handleLogin">
        <AppIcon name="login" :size="14" />
        {{ authStore.loading ? 'Signing in' : 'Sign in' }}
      </button>

      <div class="sidebar__tools">
        <button class="sidebar__icon-btn sidebar__settings" title="Settings" @click.stop="$emit('open-settings')">
          <AppIcon name="settings" :size="17" />
          <span>Settings</span>
        </button>
        <NotificationChevron />
      </div>
    </div>
  </aside>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { RouterLink, useRoute } from 'vue-router'
import AppIcon, { type IconName } from '@/components/AppIcon.vue'
import InstanceIcon from '@/components/InstanceIcon.vue'
import NotificationChevron from '@/components/NotificationChevron.vue'
import PlayButton from '@/components/PlayButton.vue'
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

const items: { to: string; label: string; icon: IconName }[] = [
  { to: '/', label: 'Home', icon: 'home' },
  { to: '/library', label: 'Library', icon: 'library' },
  { to: '/browse', label: 'Browse', icon: 'compass' },
  { to: '/profile', label: 'Skins', icon: 'user' }
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

const quickInstance = computed(() => {
  if (!authStore.authenticated) return null
  return instanceStore.recent.find((i) => i.last_played) ?? null
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
  width: var(--sidebar-width);
  height: 100vh;
  flex-shrink: 0;
  display: flex;
  flex-direction: column;
  gap: 0.35rem;
  padding: 1rem 0.75rem 0.85rem;
  overflow-y: auto;
  overflow-x: hidden;
  background-color: color-mix(in srgb, var(--bg-primary) 72%, transparent);
  border-right: 1px solid var(--border-color);
  backdrop-filter: blur(14px);
}

.sidebar__brand {
  display: flex;
  align-items: center;
  gap: 0.65rem;
  padding: 0.25rem 0.5rem 1rem;
  color: var(--text-primary);
  text-decoration: none;
}

.sidebar__brand-logo {
  display: block;
  width: 100%;
  max-width: 190px;
  height: auto;
}

.sidebar__nav {
  display: flex;
  flex-direction: column;
  gap: 0.2rem;
}

.sidebar__link {
  position: relative;
  display: flex;
  align-items: center;
  gap: 0.75rem;
  padding: 0.6rem 0.75rem;
  color: var(--text-secondary);
  text-decoration: none;
  border-radius: var(--edge-radius);
  transition: background-color var(--transition-fast), color var(--transition-fast);

  &:hover {
    color: var(--text-primary);
    background-color: var(--bg-tertiary);
  }

  &:focus-visible {
    outline: 2px solid var(--accent);
    outline-offset: -2px;
  }
}

.sidebar__link--active {
  color: var(--text-primary);
  background-color: color-mix(in srgb, var(--accent) 18%, transparent);

  &::before {
    content: '';
    position: absolute;
    left: -0.75rem;
    top: 20%;
    bottom: 20%;
    width: 3px;
    background-color: var(--accent);
  }

  .app-icon {
    color: var(--accent);
  }
}

.sidebar__link-label {
  font-family: var(--font-pixel);
  font-size: 0.9rem;
}

.sidebar__spacer {
  flex: 1;
  min-height: 1rem;
}

.sidebar__now,
.sidebar__quick {
  display: flex;
  align-items: center;
  gap: 0.6rem;
  margin-bottom: 0.5rem;
  padding: 0.55rem;
  background-color: var(--bg-tertiary);
  border: 1px solid var(--border-color);
  border-radius: var(--edge-radius);
}

.sidebar__now {
  color: inherit;
  text-decoration: none;
  background-color: color-mix(in srgb, var(--success) 10%, transparent);
  border-color: color-mix(in srgb, var(--success) 40%, transparent);
}

.sidebar__now-dot {
  width: 8px;
  height: 8px;
  margin-left: 0.25rem;
  flex-shrink: 0;
  border-radius: 50%;
  background-color: var(--success);
  animation: sidebar-pulse 1.4s ease-in-out infinite;
}

.sidebar__now-text,
.sidebar__quick-text {
  display: flex;
  flex-direction: column;
  min-width: 0;
}

.sidebar__now-label,
.sidebar__quick-label {
  font-size: 0.72rem;
  text-transform: uppercase;
  letter-spacing: 0.06em;
  color: var(--text-dim);
}

.sidebar__now-label {
  color: var(--success);
}

.sidebar__now-name,
.sidebar__quick-name {
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
  font-size: 0.88rem;
  font-weight: 600;
  color: var(--text-primary);
}

.sidebar__quick-link {
  display: flex;
  flex: 1;
  align-items: center;
  gap: 0.6rem;
  min-width: 0;
  color: inherit;
  text-decoration: none;

  &:hover .sidebar__quick-name {
    color: var(--accent);
  }
}

.sidebar__footer {
  display: flex;
  flex-direction: column;
  gap: 0.35rem;
  padding-top: 0.6rem;
  border-top: 1px solid var(--border-color);
}

.sidebar__account {
  display: flex;
  align-items: center;
  gap: 0.6rem;
  padding: 0.3rem 0.25rem 0.3rem 0.4rem;
}

.sidebar__avatar {
  width: 28px;
  height: 28px;
  flex-shrink: 0;
  border-radius: var(--edge-radius);
  image-rendering: pixelated;
}

.sidebar__account-name {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
  font-size: 0.9rem;
  font-weight: 600;
}

.sidebar__tools {
  display: flex;
  align-items: center;
  gap: 0.25rem;
}

.sidebar__icon-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 0.6rem;
  min-width: 36px;
  height: 36px;
  padding: 0 0.5rem;
  font-size: 0.9rem;
  color: var(--text-secondary);
  background: none;
  border: none;
  border-radius: var(--edge-radius);
  cursor: pointer;
  transition: background-color var(--transition-fast), color var(--transition-fast);

  &:hover {
    color: var(--text-primary);
    background-color: var(--bg-tertiary);
  }

  &:focus-visible {
    outline: 2px solid var(--accent);
    outline-offset: -2px;
  }
}

.sidebar__settings {
  flex: 1;
  justify-content: flex-start;
}

@keyframes sidebar-pulse {
  50% {
    opacity: 0.35;
  }
}
</style>
