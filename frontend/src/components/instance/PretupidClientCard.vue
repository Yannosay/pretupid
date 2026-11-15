<template>
  <div v-if="status?.supported" class="panel panel--compact pretupid-card" :class="{ 'pretupid-card--active': status.installed && status.enabled }">
    <img :src="logo" alt="" class="pretupid-card__logo" />
    <div class="pretupid-card__body">
      <span class="pretupid-card__title">
        Pretupid Client
        <span v-if="status.installed" class="badge" :class="status.enabled ? 'badge--accent' : ''">
          {{ status.enabled ? 'Active' : 'Disabled' }}
        </span>
      </span>
      <span class="pretupid-card__meta">
        <template v-if="status.installed">
          {{ status.version_name ?? 'Installed' }}
          <template v-if="status.latest_version && status.latest_version !== status.version_name">
            · {{ status.latest_version }} installs on next launch
          </template>
          · Press Right Shift in game to open the menu
        </template>
        <template v-else>
          Mod menu, HUD editor, FPS tools, zoom, cosmetics, friends and the Pretupid badge.
        </template>
      </span>
    </div>
    <button v-if="!status.installed" class="btn btn--small" :disabled="locked || installing" @click="install">
      <AppIcon name="download" :size="12" />
      {{ installing ? 'Installing' : 'Install' }}
    </button>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref, watch } from 'vue'
import AppIcon from '@/components/AppIcon.vue'
import logo from '@shared/branding/logo.png'
import { installPretupidClient, pretupidClientStatus } from '@/api'
import { useNotificationsStore } from '@/stores/notifications'
import type { Instance, PretupidClientStatus } from '@/types'

const props = defineProps<{ instance: Instance; locked: boolean; revision: number }>()
const emit = defineEmits<{ (e: 'installed'): void }>()

const notifications = useNotificationsStore()
const status = ref<PretupidClientStatus | null>(null)
const installing = ref(false)

async function refresh() {
  try {
    status.value = await pretupidClientStatus(props.instance.id)
  } catch {
    status.value = null
  }
}

async function install() {
  installing.value = true
  try {
    const result = await installPretupidClient(props.instance.id)
    notifications.notifySuccess('Pretupid Client installed', result.installed.map((m) => m.title).join(', '))
    if (result.warnings.length > 0) {
      notifications.notifyWarning('Some items need attention', result.warnings.slice(0, 3).join(' · '))
    }
    emit('installed')
    await refresh()
  } catch (e) {
    notifications.notifyError('Could not install Pretupid Client', String(e))
  } finally {
    installing.value = false
  }
}

watch(() => [props.instance.id, props.instance.mod_loader, props.revision], refresh)
onMounted(refresh)
</script>

<style scoped lang="scss">
.pretupid-card {
  display: flex;
  align-items: center;
  gap: 0.9rem;
  border-color: color-mix(in srgb, var(--accent) 35%, transparent);
  background: linear-gradient(110deg, color-mix(in srgb, var(--accent) 14%, transparent), transparent 70%);
}

.pretupid-card--active {
  border-color: color-mix(in srgb, var(--accent) 60%, transparent);
}

.pretupid-card__logo {
  width: 36px;
  height: 36px;
  flex-shrink: 0;
  image-rendering: pixelated;
  border-radius: var(--edge-radius);
}

.pretupid-card__body {
  display: flex;
  flex: 1;
  min-width: 0;
  flex-direction: column;
  gap: 0.2rem;
}

.pretupid-card__title {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  font-size: 1.15rem;
  color: var(--text-primary);
}

.pretupid-card__meta {
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
  font-size: 0.95rem;
  color: var(--text-secondary);
}
</style>
