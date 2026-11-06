<template>
  <section class="console-tab">
    <div v-if="state.crash" class="panel panel--compact console-tab__crash">
      <AppIcon name="alert" :size="18" />
      <div class="console-tab__crash-body">
        <strong>The last session crashed (exit code {{ state.crash.code ?? '?' }})</strong>
        <ul v-if="state.crash.hints.length" class="console-tab__hints">
          <li v-for="hint in state.crash.hints" :key="hint.title">
            <span class="console-tab__hint-title">{{ hint.title }}:</span> {{ hint.detail }}
          </li>
        </ul>
      </div>
    </div>

    <div class="console-tab__toolbar">
      <div class="console-tab__search">
        <AppIcon name="search" :size="14" />
        <input v-model="filter" type="search" class="input" placeholder="Filter lines" aria-label="Filter log lines" />
      </div>
      <label class="toggle">
        <input v-model="problemsOnly" type="checkbox" class="toggle__input" />
        <span class="toggle__track" />
        <span class="toggle__label">Warnings &amp; errors only</span>
      </label>
      <span class="console-tab__spacer" />
      <button class="btn btn--ghost btn--small" :disabled="lines.length === 0" @click="copy">
        <AppIcon name="copy" :size="12" />
        Copy
      </button>
      <button class="btn btn--ghost btn--small" @click="openLogs">
        <AppIcon name="folder" :size="12" />
        Logs folder
      </button>
      <button class="btn btn--ghost btn--small" :disabled="lines.length === 0 || state.running" @click="clear">Clear</button>
    </div>

    <div ref="scroller" class="console console-tab__output" @scroll="onScroll">
      <p v-if="lines.length === 0" class="console-tab__placeholder">
        {{ state.running || state.busy ? 'Waiting for output' : 'Press Play to see the game output here.' }}
      </p>
      <div
        v-for="(line, i) in visibleLines"
        :key="i"
        class="console__line"
        :class="lineClass(line)"
      >{{ line }}</div>
    </div>
    <p class="console-tab__summary">
      {{ visibleLines.length }} of {{ lines.length }} lines
      <button v-if="!follow" class="console-tab__follow" @click="scrollToEnd">Jump to latest</button>
    </p>
  </section>
</template>

<script setup lang="ts">
import { computed, nextTick, ref, watch } from 'vue'
import AppIcon from '@/components/AppIcon.vue'
import { useLaunchStore } from '@/stores/launch'
import { useNotificationsStore } from '@/stores/notifications'
import { openInstanceFolder } from '@/api'

const props = defineProps<{ instanceId: string }>()

const launchStore = useLaunchStore()
const notifications = useNotificationsStore()
const scroller = ref<HTMLElement | null>(null)
const filter = ref('')
const problemsOnly = ref(false)
const follow = ref(true)

const state = computed(() => launchStore.getState(props.instanceId))
const lines = computed(() => state.value.logs)

const PROBLEM = /(WARN|ERROR|FATAL|Exception|Caused by|\bat [\w.$]+\()/

const visibleLines = computed(() => {
  const term = filter.value.trim().toLowerCase()
  return lines.value.filter((line) => {
    if (problemsOnly.value && !PROBLEM.test(line)) return false
    return !term || line.toLowerCase().includes(term)
  })
})

function lineClass(line: string) {
  if (/ERROR|FATAL|Exception|Caused by/.test(line)) return 'console-tab__line--error'
  if (/WARN/.test(line)) return 'console-tab__line--warn'
  if (line.startsWith('[pretupid]') || line.startsWith('Launch failed')) return 'console-tab__line--info'
  return ''
}

function onScroll() {
  const el = scroller.value
  if (!el) return
  follow.value = el.scrollHeight - el.scrollTop - el.clientHeight < 40
}

function scrollToEnd() {
  const el = scroller.value
  if (!el) return
  el.scrollTop = el.scrollHeight
  follow.value = true
}

watch(
  () => visibleLines.value.length,
  async () => {
    if (!follow.value) return
    await nextTick()
    scrollToEnd()
  }
)

async function copy() {
  try {
    await navigator.clipboard.writeText(visibleLines.value.join('\n'))
    notifications.notifySuccess('Copied', 'Log lines copied to the clipboard.')
  } catch {
    notifications.notifyError('Copy failed', 'The clipboard is not available.')
  }
}

function clear() {
  launchStore.ensureState(props.instanceId).logs = []
}

async function openLogs() {
  try {
    await openInstanceFolder(props.instanceId, 'logs')
  } catch (e) {
    notifications.notifyError('Could not open folder', String(e))
  }
}
</script>

<style scoped lang="scss">
.console-tab {
  display: flex;
  flex-direction: column;
  gap: 0.85rem;
}

.console-tab__crash {
  display: flex;
  gap: 0.75rem;
  color: #f39c12;
  border-color: #f39c12;
}

.console-tab__crash-body {
  display: flex;
  flex-direction: column;
  gap: 0.35rem;
  color: var(--text-primary);
}

.console-tab__hints {
  margin: 0;
  padding-left: 1rem;
  color: var(--text-secondary);
}

.console-tab__hint-title {
  color: var(--text-primary);
}

.console-tab__toolbar {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 0.6rem;
}

.console-tab__search {
  position: relative;
  width: 260px;
  color: var(--text-secondary);

  .app-icon {
    position: absolute;
    top: 50%;
    left: 0.8rem;
    transform: translateY(-50%);
  }

  .input {
    padding: 0.5rem 0.8rem 0.5rem 2.2rem;
  }
}

.console-tab__spacer {
  flex: 1;
}

.console-tab__output {
  height: calc(100vh - 430px);
  min-height: 260px;
  max-height: none;
}

.console-tab__placeholder {
  margin: 0;
  color: var(--text-secondary);
}

.console-tab__line--error {
  color: #ff6b5b;
}

.console-tab__line--warn {
  color: #f1c40f;
}

.console-tab__line--info {
  color: var(--accent);
}

.console-tab__summary {
  display: flex;
  align-items: center;
  gap: 0.75rem;
  margin: 0;
  font-size: 0.9rem;
  color: var(--text-secondary);
}

.console-tab__follow {
  padding: 0;
  color: var(--accent);
  font: inherit;
  text-decoration: underline;
  background: none;
  border: none;
  cursor: pointer;
}
</style>
