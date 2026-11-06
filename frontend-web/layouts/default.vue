<template>
  <div class="site-layout">
    <SiteNavbar @open-settings="settingsOpen = true" />
    <main class="site-main">
      <slot />
    </main>
    <SiteFooter />
    <SiteSettingsModal :open="settingsOpen" @close="settingsOpen = false" />

    <Transition name="scroll-top">
      <button
        v-if="showScrollTop"
        type="button"
        class="scroll-top"
        aria-label="Scroll to top"
        @click="scrollToTop"
      >
        ↑
      </button>
    </Transition>
  </div>
</template>

<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref } from 'vue'

const settingsOpen = ref(false)
const showScrollTop = ref(false)

function onScroll() {
  showScrollTop.value = window.scrollY > 600
}

function scrollToTop() {
  window.scrollTo({ top: 0, behavior: 'smooth' })
}

onMounted(() => {
  window.addEventListener('scroll', onScroll, { passive: true })
})

onBeforeUnmount(() => {
  window.removeEventListener('scroll', onScroll)
})
</script>

<style scoped lang="scss">
@use 'design-system/mixins' as *;

.site-layout {
  min-height: 100vh;
  display: flex;
  flex-direction: column;
  background-color: var(--bg-primary);
  color: var(--text-primary);
  transition: background-color var(--transition-normal), color var(--transition-normal);
}

.site-main {
  flex: 1;
  width: 100%;
  max-width: 1200px;
  margin: 0 auto;
  padding: 2rem 1.5rem;
}

.scroll-top {
  position: fixed;
  bottom: 1.5rem;
  right: 1.5rem;
  z-index: 400;
  width: 44px;
  height: 44px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  font-size: 1.25rem;
  color: var(--accent-text);
  background-color: var(--accent);
  border: 2px solid var(--border-color);
  border-radius: var(--edge-radius);
  cursor: pointer;
  box-shadow: 4px 4px 0 0 var(--shadow-color);
  transition: transform var(--transition-fast);
}

.scroll-top:hover {
  transform: translate(-2px, -2px);
}

.scroll-top-enter-active,
.scroll-top-leave-active {
  transition: opacity var(--transition-fast), transform var(--transition-fast);
}

.scroll-top-enter-from,
.scroll-top-leave-to {
  opacity: 0;
  transform: translateY(10px);
}
</style>