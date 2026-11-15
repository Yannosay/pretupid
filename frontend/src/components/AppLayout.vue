<template>
  <div class="app-layout">
    <SidebarNav @open-settings="settingsModalOpen = true" />
    <main class="app-layout__content">
      <header class="app-layout__topbar">
        <RouterLink
          v-if="quickInstance && route.name !== 'home'"
          :to="{ name: 'instance-detail', params: { id: quickInstance.id } }"
          class="app-layout__quick"
          title="Last played"
        >
          <InstanceIcon :instance="quickInstance" :size="26" />
          <span class="app-layout__quick-name">{{ quickInstance.name }}</span>
        </RouterLink>
        <PlayButton v-if="quickInstance && route.name !== 'home'" :instance-id="quickInstance.id" size="small" />
        <div class="app-layout__topbar-spacer" />
        <NotificationChevron />
      </header>
      <div class="app-layout__view">
        <RouterView v-slot="{ Component, route: viewRoute }">
          <component :is="Component" :key="viewRoute.path" @open-settings="settingsModalOpen = true" />
        </RouterView>
      </div>
    </main>
    <SettingsModal v-model="settingsModalOpen" />
    <LaunchProgressPopup />
    <CrashDialog />
  </div>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import { RouterLink, RouterView, useRoute } from 'vue-router'
import SidebarNav from '@/components/SidebarNav.vue'
import SettingsModal from '@/components/SettingsModal.vue'
import LaunchProgressPopup from '@/components/LaunchProgressPopup.vue'
import NotificationChevron from '@/components/NotificationChevron.vue'
import CrashDialog from '@/components/CrashDialog.vue'
import InstanceIcon from '@/components/InstanceIcon.vue'
import PlayButton from '@/components/PlayButton.vue'
import { useInstanceStore } from '@/stores/instances'
import { useAuthStore } from '@/stores/auth'

const settingsModalOpen = ref(false)
const route = useRoute()
const instanceStore = useInstanceStore()
const authStore = useAuthStore()

const quickInstance = computed(() => {
  if (!authStore.authenticated) return null
  return instanceStore.recent.find((i) => i.last_played) ?? null
})
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
  gap: 0.75rem;
  min-height: 58px;
  padding: 0.6rem 1.75rem;
  border-bottom: 1px solid var(--border-color);
  background-color: var(--bg-secondary);
  flex-shrink: 0;
}

.app-layout__quick {
  display: flex;
  align-items: center;
  gap: 0.6rem;
  min-width: 0;
  max-width: 280px;
  color: var(--text-primary);
  text-decoration: none;

  &:hover .app-layout__quick-name {
    color: var(--accent);
  }
}

.app-layout__quick-name {
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
}

.app-layout__topbar-spacer {
  flex: 1;
}

.app-layout__view {
  flex: 1;
  padding: 1.75rem;
  overflow-y: auto;
  min-width: 0;
  min-height: 0;
}
</style>
