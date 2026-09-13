<template>
  <div class="app-layout">
    <SidebarNav @open-settings="settingsModalOpen = true" />
    <main class="app-layout__content">
      <header class="app-layout__topbar">
        <div class="app-layout__topbar-spacer" />
        <NotificationChevron />
      </header>
      <div class="app-layout__view">
        <RouterView v-slot="{ Component, route }">
          <component
            :is="Component"
            :key="route.fullPath"
            @open-settings="settingsModalOpen = true"
          />
        </RouterView>
      </div>
    </main>
    <SettingsModal v-model="settingsModalOpen" />
    <LaunchProgressPopup />
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { RouterView } from 'vue-router'
import SidebarNav from '@/components/SidebarNav.vue'
import SettingsModal from '@/components/SettingsModal.vue'
import LaunchProgressPopup from '@/components/LaunchProgressPopup.vue'
import NotificationChevron from '@/components/NotificationChevron.vue'

const settingsModalOpen = ref(false)
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

.app-layout__topbar {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 1rem;
  padding: 0.75rem 2rem;
  border-bottom: 2px solid var(--border-color);
  background-color: var(--bg-secondary);
  flex-shrink: 0;
}

.app-layout__topbar-spacer {
  flex: 1;
}

.app-layout__view {
  flex: 1;
  padding: 2rem;
  overflow-y: auto;
  min-width: 0;
  min-height: 0;
}
</style>