<template>
  <div v-if="crash" class="modal-overlay" @click.self="dismiss">
    <div class="modal" role="alertdialog" aria-modal="true">
      <div class="modal__header">
        <div>
          <h3 class="modal__title">{{ instanceName }} crashed</h3>
          <p class="modal__subtitle">Exit code {{ crash.code ?? 'unknown' }}</p>
        </div>
        <button class="modal__close" aria-label="Close" @click="dismiss">×</button>
      </div>
      <div class="modal__body">
        <template v-if="crash.hints.length > 0">
          <p class="crash__lead">Here is what most likely went wrong:</p>
          <ul class="crash__hints">
            <li v-for="hint in crash.hints" :key="hint.title" class="crash__hint">
              <AppIcon name="alert" :size="16" />
              <div>
                <strong class="crash__hint-title">{{ hint.title }}</strong>
                <p class="crash__hint-detail">{{ hint.detail }}</p>
              </div>
            </li>
          </ul>
        </template>
        <p v-else class="crash__lead">
          The game closed unexpectedly. Check the console output or the crash report for details. Disabling recently
          added mods is often the fastest fix.
        </p>
        <p v-if="crash.message" class="crash__message">{{ crash.message }}</p>
      </div>
      <div class="modal__footer">
        <button class="btn btn--ghost btn--small" @click="copyLog">
          <AppIcon name="copy" :size="12" />
          Copy log
        </button>
        <button v-if="crash.crash_report" class="btn btn--ghost btn--small" @click="openReports">
          <AppIcon name="folder" :size="12" />
          Crash reports
        </button>
        <span class="modal__footer-spacer" />
        <button class="btn btn--secondary btn--small" @click="viewInstance">Open instance</button>
        <button class="btn btn--small" @click="dismiss">OK</button>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { useEscapeKey } from '@/composables/useEscapeKey'
import { computed } from 'vue'
import { useRouter } from 'vue-router'
import AppIcon from '@/components/AppIcon.vue'
import { useLaunchStore } from '@/stores/launch'
import { useInstanceStore } from '@/stores/instances'
import { useNotificationsStore } from '@/stores/notifications'
import { openInstanceFolder } from '@/api'

const launchStore = useLaunchStore()
const instanceStore = useInstanceStore()
const notifications = useNotificationsStore()
const router = useRouter()

const crash = computed(() => {
  for (const state of Object.values(launchStore.states)) {
    if (state.crash) return state.crash
  }
  return null
})

const instanceName = computed(() => {
  if (!crash.value) return ''
  return instanceStore.byId(crash.value.instance_id)?.name ?? 'Minecraft'
})

function dismiss() {
  if (crash.value) launchStore.dismissCrash(crash.value.instance_id)
}

async function copyLog() {
  if (!crash.value) return
  const logs = launchStore.getState(crash.value.instance_id).logs.join('\n')
  try {
    await navigator.clipboard.writeText(logs)
    notifications.notifySuccess('Copied', 'The game log is on your clipboard.')
  } catch {
    notifications.notifyError('Copy failed', 'The clipboard is not available.')
  }
}

async function openReports() {
  if (!crash.value) return
  try {
    await openInstanceFolder(crash.value.instance_id, 'crash-reports')
  } catch (e) {
    notifications.notifyError('Could not open folder', String(e))
  }
}

function viewInstance() {
  if (!crash.value) return
  const id = crash.value.instance_id
  dismiss()
  router.push({ name: 'instance-detail', params: { id }, query: { tab: 'console' } })
}
useEscapeKey(() => Boolean(crash.value), dismiss)
</script>

<style scoped lang="scss">
.crash__lead {
  margin: 0;
}

.crash__hints {
  list-style: none;
  margin: 0;
  padding: 0;
  display: flex;
  flex-direction: column;
  gap: 0.6rem;
}

.crash__hint {
  display: flex;
  gap: 0.75rem;
  padding: 0.75rem;
  background-color: var(--bg-tertiary);
  border: 1px solid var(--warning);
  border-radius: var(--edge-radius);
  color: var(--warning);
}

.crash__hint-title {
  display: block;
  font-family: var(--font-pixel);
  font-size: 0.85rem;
  color: var(--text-primary);
}

.crash__hint-detail {
  margin: 0.35rem 0 0;
  color: var(--text-secondary);
  line-height: 1.2;
}

.crash__message {
  margin: 0;
  padding: 0.6rem 0.75rem;
  font-family: var(--font-mono);
  font-size: 0.85rem;
  color: var(--text-secondary);
  background-color: var(--bg-primary);
  border: 1px solid var(--border-color);
  border-radius: var(--edge-radius);
  word-break: break-word;
}
</style>
