<template>
  <div class="app-layout">
    <SidebarNav @open-settings="settingsModalOpen = true" />
    <main class="app-layout__content">
      <RouterView @open-settings="settingsModalOpen = true" />
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

const settingsModalOpen = ref(false)
</script>

<style scoped lang="scss">
.app-layout {
  display: flex;
  min-height: 100vh;
}

.app-layout__content {
  flex: 1;
  padding: 2rem;
  overflow-y: auto;
}
</style>