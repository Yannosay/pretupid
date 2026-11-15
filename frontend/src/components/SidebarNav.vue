<template>
  <aside class="sidebar">
    <RouterLink to="/" class="sidebar__logo" aria-label="Pretupid Launcher home">
      <img src="@shared/branding/logo.png" alt="" />
      <span class="sidebar__logo-text">
        <span class="sidebar__logo-name">Pretupid</span>
        <span class="sidebar__logo-sub">Launcher</span>
      </span>
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
        <span class="sidebar__link-label">{{ item.label }}</span>
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
@use 'design-system/mixins' as *;

.sidebar {
  width: 216px;
  height: 100vh;
  flex-shrink: 0;
  display: flex;
  flex-direction: column;
  gap: 1.1rem;
  padding: 1.1rem 0.75rem;
  overflow-y: auto;
  background-color: rgba(0, 0, 0, 0.28);
  border-right: 1px solid var(--border-color);
  backdrop-filter: blur(14px);
}

.sidebar__logo {
  display: flex;
  align-items: center;
  gap: 0.7rem;
  padding: 0.2rem 0.4rem 0.4rem;
  color: inherit;
  text-decoration: none;

  img {
    width: 36px;
    height: 36px;
    image-rendering: pixelated;
  }
}

.sidebar__logo-text {
  display: flex;
  flex-direction: column;
  gap: 0.15rem;
}

.sidebar__logo-name {
  font-family: var(--font-pixel);
  font-size: 1rem;
  font-weight: 700;
  color: var(--text-primary);
}

.sidebar__logo-sub {
  font-size: 0.8rem;
  color: var(--text-dim);
}

.sidebar__nav {
  display: flex;
  flex-direction: column;
  gap: 0.25rem;
}

.sidebar__link {
  @include chamfer;
  position: relative;
  display: flex;
  align-items: center;
  gap: 0.75rem;
  padding: 0.6rem 0.8rem;
  color: var(--text-secondary);
  text-decoration: none;
  transition: background-color var(--transition-normal), color var(--transition-fast), padding-left var(--transition-normal);

  &:hover {
    color: var(--text-primary);
    background-color: var(--bg-tertiary);
    padding-left: 0.95rem;
  }
}

.sidebar__link--active {
  color: var(--text-primary);
  background-color: color-mix(in srgb, var(--accent) 22%, transparent);

  &::before {
    content: '';
    position: absolute;
    left: 0;
    top: 22%;
    bottom: 22%;
    width: 3px;
    background-color: var(--accent);
  }

  .app-icon {
    color: var(--accent);
  }
}

.sidebar__link-label {
  font-family: var(--font-pixel);
  font-size: 0.92rem;
}

.sidebar__playing {
  @include chamfer;
  display: flex;
  align-items: center;
  gap: 0.6rem;
  padding: 0.6rem 0.75rem;
  color: inherit;
  text-decoration: none;
  background-color: color-mix(in srgb, var(--success) 12%, transparent);
  border: 1px solid color-mix(in srgb, var(--success) 45%, transparent);
}

.sidebar__playing-dot {
  width: 8px;
  height: 8px;
  flex-shrink: 0;
  background-color: var(--success);
  animation: sidebar-pulse 1.4s ease-in-out infinite;
}

.sidebar__playing-text {
  display: flex;
  flex-direction: column;
  min-width: 0;
}

.sidebar__playing-label {
  font-size: 0.85rem;
  color: var(--success);
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
  @include chamfer;
  display: flex;
  align-items: center;
  gap: 0.6rem;
  padding: 0.55rem;
  background-color: var(--bg-tertiary);
  border: 1px solid var(--border-color);
}

.sidebar__avatar {
  width: 32px;
  height: 32px;
  flex-shrink: 0;
  image-rendering: pixelated;
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
  font-size: 0.9rem;
  color: var(--text-primary);
}

.sidebar__account-type {
  font-size: 0.85rem;
  color: var(--text-dim);
}

.sidebar__settings {
  @include chamfer;
  display: flex;
  align-items: center;
  gap: 0.6rem;
  width: 100%;
  padding: 0.55rem 0.8rem;
  font-family: var(--font-pixel);
  font-size: 0.88rem;
  color: var(--text-secondary);
  background: none;
  border: none;
  cursor: pointer;

  &:hover {
    color: var(--text-primary);
    background-color: var(--bg-tertiary);
  }
}

@keyframes sidebar-pulse {
  50% {
    opacity: 0.35;
  }
}
</style>
