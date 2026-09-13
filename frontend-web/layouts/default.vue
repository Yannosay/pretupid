<template>
  <div class="site-layout">
    <SiteNavbar @open-settings="settingsOpen = true" />
    <main class="site-main">
      <slot />
    </main>
    <SiteFooter />
    <SiteSettingsModal :open="settingsOpen" @close="settingsOpen = false" />
  </div>
</template>

<script setup lang="ts">
const settingsOpen = ref(false)
</script>

<style scoped lang="scss">
@use '@/assets/styles/_mixins' as *;

.site-layout {
  min-height: 100vh;
  display: flex;
  flex-direction: column;
  background-color: var(--bg-primary);
  color: var(--text-primary);
}

.site-main {
  flex: 1;
  width: 100%;
  max-width: 1200px;
  margin: 0 auto;
  padding: 2rem 1.5rem;
}
</style>