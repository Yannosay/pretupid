<template>
  <aside class="sidebar">
    <div class="sidebar__logo">
      <img src="@/assets/branding/ptd_logo_main.webp" alt="Pretupid Launcher" />
      <span class="sidebar__logo-text">Pretupid</span>
    </div>

    <nav class="sidebar__nav">
      <RouterLink to="/" class="sidebar__nav-link" active-class="sidebar__nav-link--active">
        Home
      </RouterLink>
      <RouterLink to="/library" class="sidebar__nav-link" active-class="sidebar__nav-link--active">
        Instances
      </RouterLink>
      <RouterLink to="/profile" class="sidebar__nav-link" active-class="sidebar__nav-link--active">
        Profile
      </RouterLink>
      <RouterLink to="/modrinth" class="sidebar__nav-link" active-class="sidebar__nav-link--active">
        Modrinth Library
      </RouterLink>
    </nav>

    <div class="sidebar__bottom">
      <div class="sidebar__auth">
        <div v-if="authStore.authenticated && authStore.profile" class="sidebar__profile">
          <span class="sidebar__profile-name">{{ authStore.profile.name }}</span>
          <button class="btn btn--ghost btn--small sidebar__logout" @click="handleLogout">
            Logout
          </button>
        </div>
        <button
          v-else
          class="btn btn--small sidebar__login"
          :disabled="authStore.loading"
          @click="handleLogin"
        >
          {{ authStore.loading ? 'Logging in...' : 'Login with Microsoft' }}
        </button>
      </div>

      <button class="btn btn--ghost sidebar__settings" @click.stop="$emit('open-settings')">
        Settings
      </button>
    </div>
  </aside>
</template>

<script setup lang="ts">
import { RouterLink } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import { useNotificationsStore } from '@/stores/notifications'

defineEmits<{
  (e: 'open-settings'): void
}>()

const authStore = useAuthStore()
const notifications = useNotificationsStore()

async function handleLogin() {
  try {
    await authStore.login()
  } catch (e) {
    notifications.notifyError('Login failed', String(e))
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
  width: 240px;
  height: 100vh;
  flex-shrink: 0;
  background-color: var(--bg-secondary);
  border-right: 2px solid var(--border-color);
  display: flex;
  flex-direction: column;
  padding: 1.5rem 1rem;
  overflow-y: auto;
}

.sidebar__logo {
  display: flex;
  align-items: center;
  gap: 0.75rem;
  margin-bottom: 2rem;
}

.sidebar__logo img {
  width: 40px;
  height: 40px;
  border-radius: var(--edge-radius);
}

.sidebar__logo-text {
  font-family: var(--font-pixel);
  font-size: 1.2rem;
  color: var(--accent);
}

.sidebar__nav {
  display: flex;
  flex-direction: column;
  gap: 0.5rem;
}

.sidebar__nav-link {
  display: block;
  padding: 0.75rem 1rem;
  font-family: var(--font-pixel);
  font-size: 0.8rem;
  text-transform: uppercase;
  color: var(--text-primary);
  border: 2px solid transparent;
  border-radius: var(--edge-radius);
  transition: background-color var(--transition-fast),
              border-color var(--transition-fast),
              color var(--transition-fast);
  cursor: pointer;
}

.sidebar__nav-link:hover {
  background-color: var(--bg-tertiary);
}

.sidebar__nav-link--active {
  background-color: var(--accent);
  color: var(--accent-text);
  border-color: var(--accent);
}

.sidebar__bottom {
  margin-top: auto;
  display: flex;
  flex-direction: column;
  gap: 0.5rem;
}

.sidebar__auth {
  display: flex;
  flex-direction: column;
  gap: 0.5rem;
}

.sidebar__profile {
  display: flex;
  flex-direction: column;
  gap: 0.5rem;
  padding: 0.75rem 1rem;
  background-color: var(--bg-tertiary);
  border-radius: var(--edge-radius);
}

.sidebar__profile-name {
  font-family: var(--font-pixel);
  font-size: 0.7rem;
  color: var(--accent);
  word-break: break-all;
}

.sidebar__login {
  width: 100%;
  font-size: 0.6rem;
}

.sidebar__logout {
  width: 100%;
  font-size: 0.55rem;
}

.sidebar__settings {
  width: 100%;
}
</style>