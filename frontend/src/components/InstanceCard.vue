<template>
  <article
    class="instance-card"
    :class="{ 'instance-card--running': state.running, 'instance-card--installing': installing }"
    tabindex="0"
    @click="open"
    @keydown.enter="open"
  >
    <div class="instance-card__top">
      <InstanceIcon :instance="instance" :size="52" />
      <div class="instance-card__heading">
        <h3 class="instance-card__name" :title="instance.name">{{ instance.name }}</h3>
        <div class="instance-card__tags">
          <span class="badge">{{ instance.minecraft_version }}</span>
          <span class="badge" :class="instance.mod_loader === 'vanilla' ? '' : 'badge--accent'">{{ loaderLabel(instance.mod_loader) }}</span>
        </div>
      </div>
      <DropdownMenu @keydown.enter.stop>
        <template #trigger="{ toggle, open: menuOpen }">
          <button
            class="icon-btn icon-btn--small"
            aria-label="More actions"
            aria-haspopup="menu"
            :aria-expanded="menuOpen"
            @click="toggle"
          >
            <AppIcon name="more" :size="16" />
          </button>
        </template>
        <template #default="{ close }">
          <button class="menu__item" role="menuitem" @click="run(close, open)"><AppIcon name="sliders" :size="14" />Manage</button>
          <button class="menu__item" role="menuitem" @click="run(close, openFolder)"><AppIcon name="folder" :size="14" />Open folder</button>
          <button class="menu__item" role="menuitem" :disabled="busy" @click="run(close, duplicate)"><AppIcon name="copy" :size="14" />Duplicate</button>
          <div class="menu__divider" />
          <button class="menu__item menu__item--danger" role="menuitem" :disabled="busy" @click="run(close, () => emit('delete', instance))">
            <AppIcon name="trash" :size="14" />Delete
          </button>
        </template>
      </DropdownMenu>
    </div>

    <p v-if="installing" class="instance-card__status">
      <span class="instance-card__pulse" />
      {{ installState?.message || 'Installing modpack' }}
    </p>
    <p v-else-if="state.running" class="instance-card__status instance-card__status--running">
      <span class="instance-card__pulse" />
      Running
    </p>
    <p v-else class="instance-card__meta">
      <AppIcon name="clock" :size="12" />
      {{ instance.last_played ? `${formatRelative(instance.last_played)} · ${formatPlaytime(instance.playtime_seconds)}` : 'Not played yet' }}
    </p>

    <div class="instance-card__footer" @click.stop>
      <PlayButton :instance-id="instance.id" size="small" />
    </div>
  </article>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useRouter } from 'vue-router'
import AppIcon from '@/components/AppIcon.vue'
import DropdownMenu from '@/components/DropdownMenu.vue'
import InstanceIcon from '@/components/InstanceIcon.vue'
import PlayButton from '@/components/PlayButton.vue'
import { useLaunchStore } from '@/stores/launch'
import { useInstallsStore } from '@/stores/installs'
import { useInstanceStore } from '@/stores/instances'
import { useNotificationsStore } from '@/stores/notifications'
import { openInstanceFolder } from '@/api'
import { formatPlaytime, formatRelative, loaderLabel } from '@/utils/format'
import type { Instance } from '@/types'

const props = defineProps<{ instance: Instance }>()
const emit = defineEmits<{ (e: 'delete', instance: Instance): void }>()

const router = useRouter()
const launchStore = useLaunchStore()
const installs = useInstallsStore()
const instanceStore = useInstanceStore()
const notifications = useNotificationsStore()

const state = computed(() => launchStore.getState(props.instance.id))
const installState = computed(() => installs.getState(props.instance.id))
const installing = computed(() => installs.isInstalling(props.instance.id))
const busy = computed(() => state.value.busy || installing.value)

function open() {
  router.push({ name: 'instance-detail', params: { id: props.instance.id } })
}

function run(close: () => void, action: () => unknown) {
  close()
  action()
}

async function openFolder() {
  try {
    await openInstanceFolder(props.instance.id)
  } catch (e) {
    notifications.notifyError('Could not open folder', String(e))
  }
}

async function duplicate() {
  try {
    const copy = await instanceStore.duplicate(props.instance.id)
    notifications.notifySuccess('Instance duplicated', `${copy.name} was created with the same mods and settings.`)
  } catch (e) {
    notifications.notifyError('Duplicate failed', String(e))
  }
}
</script>

<style scoped lang="scss">
.instance-card {
  position: relative;
  display: flex;
  flex-direction: column;
  gap: 0.75rem;
  padding: 1rem;
  background-color: var(--bg-secondary);
  border: 1px solid var(--border-color);
  border-radius: var(--edge-radius);
  cursor: pointer;
  transition: transform var(--transition-fast), box-shadow var(--transition-fast), border-color var(--transition-fast);

  &:hover,
  &:focus-visible {
    outline: none;
    border-color: var(--accent);
    transform: translate(-2px, -2px);
    box-shadow: 0 6px 18px var(--shadow-color);
  }
}

.instance-card--running {
  border-color: var(--success);
}

.instance-card--installing {
  border-style: dashed;
}

.instance-card__top {
  display: flex;
  align-items: flex-start;
  gap: 0.8rem;
}

.instance-card__heading {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 0.45rem;
}

.instance-card__name {
  margin: 0;
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
  font-family: var(--font-pixel);
  font-size: 0.85rem;
  line-height: 1.3;
  text-transform: none;
}

.instance-card__tags {
  display: flex;
  flex-wrap: wrap;
  gap: 0.3rem;
}

.instance-card__meta,
.instance-card__status {
  display: flex;
  align-items: center;
  gap: 0.45rem;
  margin: 0;
  font-size: 0.95rem;
  color: var(--text-secondary);
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
}

.instance-card__status {
  color: var(--accent);
}

.instance-card__status--running {
  color: var(--success);
}

.instance-card__pulse {
  width: 8px;
  height: 8px;
  flex-shrink: 0;
  background-color: currentColor;
  animation: card-pulse 1.2s ease-in-out infinite;
}

.instance-card__footer {
  display: flex;
  justify-content: flex-start;
  margin-top: auto;
}

@keyframes card-pulse {
  50% {
    opacity: 0.3;
  }
}
</style>
