<template>
  <header class="navbar" :class="{ 'navbar--scrolled': scrolled }">
    <NuxtLink to="/" class="navbar__brand">
      <img :src="logo" alt="" width="32" height="32" class="navbar__logo" />
      <span class="navbar__brand-text">Pretupid</span>
    </NuxtLink>

    <nav class="navbar__nav" aria-label="Primary">
      <NuxtLink to="/" class="navbar__link">Home</NuxtLink>
      <NuxtLink to="/features" class="navbar__link">Features</NuxtLink>
      <NuxtLink to="/security" class="navbar__link">Security</NuxtLink>
      <NuxtLink to="/news" class="navbar__link">News</NuxtLink>
      <NuxtLink to="/download" class="navbar__link">Download</NuxtLink>
      <NuxtLink to="/about" class="navbar__link">About</NuxtLink>
    </nav>

    <div class="navbar__actions">
      <button
        type="button"
        class="btn btn--ghost btn--small navbar__open"
        @click="openLauncher"
      >
        Open Launcher
      </button>
      <button
        type="button"
        class="navbar__settings"
        aria-label="Open appearance settings"
        @click="$emit('open-settings')"
      >
        <svg viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="square" stroke-linejoin="miter">
          <circle cx="12" cy="12" r="3" />
          <path d="M12 2v3M12 19v3M4.22 4.22l2.12 2.12M17.66 17.66l2.12 2.12M2 12h3M19 12h3M4.22 19.78l2.12-2.12M17.66 6.34l2.12-2.12" />
        </svg>
      </button>
    </div>
  </header>
</template>

<script setup lang="ts">
import logo from '@shared/branding/logo.png'
import { onBeforeUnmount, onMounted, ref } from 'vue'
import { useLauncherBridge } from '~/composables/useLauncherBridge'

defineEmits<{
  (e: 'open-settings'): void
}>()

const { openLauncher } = useLauncherBridge()
const scrolled = ref(false)

function onScroll() {
  scrolled.value = window.scrollY > 12
}

onMounted(() => {
  onScroll()
  window.addEventListener('scroll', onScroll, { passive: true })
})

onBeforeUnmount(() => {
  window.removeEventListener('scroll', onScroll)
})
</script>

<style scoped lang="scss">
@use 'design-system/mixins' as *;

.navbar {
  position: sticky;
  top: 0;
  z-index: 500;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 1.5rem;
  padding: 0.9rem 2rem;
  background-color: rgba(26, 26, 26, 0.85);
  backdrop-filter: blur(10px);
  border-bottom: 2px solid var(--border-color);
  transition: padding var(--transition-fast), background-color var(--transition-fast);
}

.navbar--scrolled {
  padding: 0.65rem 2rem;
  background-color: rgba(26, 26, 26, 0.95);
}

.navbar__brand {
  display: flex;
  align-items: center;
  gap: 0.75rem;
  text-decoration: none;
  color: inherit;
}

.navbar__logo {
  width: 36px;
  height: 36px;
  border-radius: var(--edge-radius);
  border: 2px solid var(--border-color);
}

.navbar__brand-text {
  font-family: var(--font-pixel);
  font-weight: 700;
  font-size: 1rem;
  color: var(--accent);
}

.navbar__nav {
  display: flex;
  gap: 1.5rem;
  align-items: center;
  flex-wrap: wrap;
}

.navbar__link {
  position: relative;
  font-family: var(--font-body);
  font-size: 1.15rem;
  color: var(--text-secondary);
  text-decoration: none;
  padding: 0.25rem 0;
  transition: color var(--transition-fast);
}

.navbar__link::after {
  content: '';
  position: absolute;
  left: 0;
  bottom: -4px;
  width: 0;
  height: 2px;
  background-color: var(--accent);
  transition: width var(--transition-normal);
}

.navbar__link:hover {
  color: var(--text-primary);
}

.navbar__link:hover::after,
.navbar__link.router-link-active::after {
  width: 100%;
}

.navbar__link.router-link-active {
  color: var(--text-primary);
}

.navbar__actions {
  display: flex;
  align-items: center;
  gap: 0.75rem;
}

.navbar__open {
  white-space: nowrap;
}

.navbar__settings {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 38px;
  height: 38px;
  color: var(--text-secondary);
  background-color: transparent;
  border: 2px solid var(--border-color);
  border-radius: var(--edge-radius);
  cursor: pointer;
  transition: color var(--transition-fast), border-color var(--transition-fast);
}

.navbar__settings:hover {
  color: var(--text-primary);
  border-color: var(--accent);
}

@media (max-width: 800px) {
  .navbar {
    flex-wrap: wrap;
    padding: 0.75rem 1rem;
  }

  .navbar__nav {
    order: 3;
    width: 100%;
    justify-content: center;
    gap: 1rem;
    font-size: 1rem;
  }

  .navbar__brand-text {
    display: none;
  }
}
</style>