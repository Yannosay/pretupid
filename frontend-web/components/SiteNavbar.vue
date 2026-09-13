<template>
  <header class="navbar">
    <NuxtLink to="/" class="navbar__brand">
      <img :src="logoUrl" alt="Pretupid Launcher" class="navbar__logo-image" />
    </NuxtLink>
    <nav class="navbar__nav">
      <NuxtLink to="/" class="navbar__link">Home</NuxtLink>
      <NuxtLink to="/#features" class="navbar__link">Features</NuxtLink>
      <NuxtLink to="/download" class="navbar__link">Download</NuxtLink>
      <NuxtLink to="/about" class="navbar__link">About</NuxtLink>
    </nav>
    <div class="navbar__actions">
      <button class="navbar__settings" @click="$emit('open-settings')" aria-label="Open settings">⚙</button>
      <NuxtLink to="/download" class="btn btn--small navbar__cta">Get Launcher</NuxtLink>
    </div>
  </header>
</template>

<script setup lang="ts">
defineEmits<{
  (e: 'open-settings'): void
}>()

const logoUrl = '/assets/showcase-ptd-launcher.webp'
</script>

<style scoped lang="scss">
@use '@/assets/styles/_mixins' as *;

.navbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 1rem 2rem;
  background-color: var(--bg-secondary);
  border-bottom: 2px solid var(--border-color);
  flex-wrap: wrap;
  gap: 1rem;
}

.navbar__brand {
  text-decoration: none;
  display: flex;
  align-items: center;
}

.navbar__logo-image {
  height: 3rem;
  width: auto;
}

.navbar__nav {
  display: flex;
  gap: 1.5rem;
  align-items: center;
  flex-wrap: wrap;
}

.navbar__link {
  font-family: var(--font-body);
  font-size: 1.2rem;
  color: var(--text-secondary);
  text-decoration: none;
  transition: color var(--transition-fast);
}

.navbar__link:hover,
.navbar__link.router-link-active {
  color: var(--text-primary);
}

.navbar__actions {
  display: flex;
  align-items: center;
  gap: 1rem;
}

.navbar__settings {
  background: none;
  border: none;
  color: var(--text-secondary);
  font-size: 1.5rem;
  cursor: pointer;
  transition: color var(--transition-fast);
}

.navbar__settings:hover {
  color: var(--text-primary);
}

.navbar__cta {
  white-space: nowrap;
}
</style>