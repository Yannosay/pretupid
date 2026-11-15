<template>
  <div class="app-layout">
    <SidebarNav @open-settings="settingsModalOpen = true" />
    <main class="app-layout__content">
      <div ref="viewRef" class="app-layout__view" :class="{ 'app-layout__view--bleed': route.meta.bleed }">
        <RouterView v-slot="{ Component, route: viewRoute }">
          <Transition name="view" mode="out-in">
            <component :is="Component" :key="viewRoute.path" @open-settings="settingsModalOpen = true" />
          </Transition>
        </RouterView>
      </div>
    </main>
    <SettingsModal v-model="settingsModalOpen" />
    <LaunchProgressPopup />
    <CrashDialog />
  </div>
</template>

<script setup lang="ts">
import { ref, watch } from 'vue'
import { RouterView, useRoute } from 'vue-router'
import SidebarNav from '@/components/SidebarNav.vue'
import SettingsModal from '@/components/SettingsModal.vue'
import LaunchProgressPopup from '@/components/LaunchProgressPopup.vue'
import CrashDialog from '@/components/CrashDialog.vue'

const settingsModalOpen = ref(false)
const route = useRoute()
const viewRef = ref<HTMLElement | null>(null)

watch(
  () => route.path,
  () => {
    viewRef.value?.scrollTo({ top: 0 })
  }
)
</script>

<style scoped lang="scss">
.app-layout {
  display: flex;
  height: 100vh;
  overflow: hidden;
}

.app-layout__content {
  flex: 1;
  display: flex;
  flex-direction: column;
  min-width: 0;
  position: relative;
}

.app-layout__view {
  flex: 1;
  padding: 1.75rem 2rem 2.5rem;
  overflow-y: auto;
  overflow-x: hidden;
  min-width: 0;
  min-height: 0;
  scrollbar-gutter: stable;
}

.app-layout__view--bleed {
  padding: 0;
}

.view-enter-active,
.view-leave-active {
  transition: opacity 0.14s ease;
}

.view-enter-from,
.view-leave-to {
  opacity: 0;
}

@media (prefers-reduced-motion: reduce) {
  .view-enter-active,
  .view-leave-active {
    transition: none;
  }
}
</style>
