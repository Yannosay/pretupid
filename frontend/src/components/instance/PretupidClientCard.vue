<template>
  <div
    v-if="busy || status?.supported"
    class="pretupid-card"
    :class="{ 'pretupid-card--active': active, 'pretupid-card--busy': busy }"
  >
    <span class="pretupid-card__frame" aria-hidden="true" />
    <img :src="logo" alt="" class="pretupid-card__logo" />
    <div class="pretupid-card__body">
      <span class="pretupid-card__title">
        Pretupid Client
        <span v-if="busy" class="badge badge--accent">Installing</span>
        <span v-else-if="status?.installed" class="badge" :class="status.enabled ? 'badge--accent' : ''">
          {{ status.enabled ? 'Active' : 'Disabled' }}
        </span>
      </span>
      <span class="pretupid-card__meta">
        <template v-if="busy">Downloading and verifying the client. This takes a few seconds.</template>
        <template v-else-if="status?.installed">
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
      <div v-if="busy" class="progress-bar progress-bar--indeterminate pretupid-card__progress">
        <div class="progress-bar__fill" />
      </div>
    </div>
    <button v-if="!busy && status && !status.installed" class="btn btn--small" :disabled="locked" @click="install">
      <AppIcon name="download" :size="12" />
      Install
    </button>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import AppIcon from '@/components/AppIcon.vue'
import logo from '@shared/branding/pretupid_logo_512x.png'
import { installPretupidClient, pretupidClientStatus } from '@/api'
import { useNotificationsStore } from '@/stores/notifications'
import { useSetupStore } from '@/stores/setup'
import type { Instance, PretupidClientStatus } from '@/types'

const props = defineProps<{ instance: Instance; locked: boolean; revision: number }>()
const emit = defineEmits<{ (e: 'installed'): void }>()

const notifications = useNotificationsStore()
const setup = useSetupStore()
const status = ref<PretupidClientStatus | null>(null)
const installing = ref(false)

const busy = computed(() => installing.value || setup.isPending(props.instance.id, 'client'))
const active = computed(() => !busy.value && !!status.value?.installed && !!status.value.enabled)

async function refresh() {
  const id = props.instance.id
  try {
    const next = await pretupidClientStatus(id)
    if (props.instance.id === id) status.value = next
  } catch {
    if (props.instance.id === id) status.value = null
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

watch(() => [props.instance.id, props.instance.mod_loader, props.instance.minecraft_version, props.revision], refresh)
onMounted(refresh)
</script>

<style scoped lang="scss">
.pretupid-card {
  position: relative;
  display: flex;
  align-items: center;
  gap: 0.9rem;
  padding: 0.85rem 1rem;
  isolation: isolate;
  background-color: var(--bg-secondary);
  border: 1px solid var(--border-color);
  border-radius: var(--edge-radius);
  transition: box-shadow var(--transition-normal), border-color var(--transition-normal);
}

.pretupid-card__frame {
  position: absolute;
  inset: -1px;
  z-index: -1;
  padding: 2px;
  border-radius: inherit;
  opacity: 0;
  pointer-events: none;
  background: linear-gradient(110deg, var(--accent), #ffffff 18%, var(--accent) 36%, color-mix(in srgb, var(--accent) 40%, #facc15) 60%, var(--accent));
  background-size: 250% 100%;
  mask: linear-gradient(#000 0 0) content-box exclude, linear-gradient(#000 0 0);
  transition: opacity var(--transition-normal);
}

.pretupid-card--active,
.pretupid-card--busy {
  border-color: transparent;
  background:
    radial-gradient(circle at 34px 50%, color-mix(in srgb, var(--accent) 26%, transparent), transparent 150px),
    linear-gradient(110deg, color-mix(in srgb, var(--accent) 14%, transparent), transparent 75%),
    var(--bg-secondary);

  .pretupid-card__frame {
    opacity: 1;
    animation: pretupid-card-sheen 3.2s linear infinite;
  }
}

.pretupid-card--active {
  box-shadow: 0 0 22px color-mix(in srgb, var(--accent) 22%, transparent);

  .pretupid-card__logo {
    filter: drop-shadow(0 0 10px color-mix(in srgb, var(--accent) 65%, transparent));
    animation: pretupid-card-float 3s ease-in-out infinite;
  }
}

.pretupid-card--busy .pretupid-card__frame {
  animation-duration: 1.4s;
}

.pretupid-card__logo {
  width: 40px;
  height: 40px;
  flex-shrink: 0;
  image-rendering: pixelated;
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
  font-family: var(--font-pixel);
  font-size: 0.95rem;
  color: var(--text-primary);
}

.pretupid-card__meta {
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
  font-size: 0.92rem;
  color: var(--text-secondary);
}

.pretupid-card__progress {
  margin-top: 0.4rem;
}

@keyframes pretupid-card-sheen {
  from {
    background-position: 100% 0;
  }
  to {
    background-position: -150% 0;
  }
}

@keyframes pretupid-card-float {
  50% {
    transform: translateY(-3px);
  }
}

@media (prefers-reduced-motion: reduce) {
  .pretupid-card__frame,
  .pretupid-card__logo {
    animation: none !important;
  }
}
</style>
