<template>
  <button
    v-if="!authStore.authenticated"
    class="btn play-btn"
    :class="sizeClass"
    :disabled="authStore.loading"
    @click.stop="signIn"
  >
    <AppIcon name="login" :size="iconSize" />
    <span class="play-btn__label">{{ authStore.loading ? 'Signing in' : 'Sign in to play' }}</span>
  </button>
  <button v-else-if="installing" class="btn btn--secondary play-btn" :class="sizeClass" disabled>
    <span class="play-btn__spinner" />
    <span class="play-btn__label">Installing</span>
  </button>
  <button v-else-if="state.running" class="btn btn--warning play-btn" :class="sizeClass" @click.stop="stop">
    <AppIcon name="stop" :size="iconSize" />
    <span class="play-btn__label">Stop</span>
  </button>
  <button
    v-else-if="state.busy"
    class="btn btn--secondary play-btn play-btn--busy"
    :class="sizeClass"
    :title="state.progress?.message ?? 'Preparing'"
    @click.stop="stop"
  >
    <span class="play-btn__fill" :style="{ width: `${percent}%` }" />
    <span class="play-btn__spinner" />
    <span class="play-btn__label">{{ percent > 0 ? `${percent}%` : busyLabel }}</span>
  </button>
  <button v-else class="btn btn--success play-btn" :class="sizeClass" :title="compact ? 'Play' : undefined" :aria-label="compact ? 'Play' : undefined" @click.stop="play">
    <AppIcon name="play" :size="iconSize" />
    <span class="play-btn__label">Play</span>
  </button>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import AppIcon from '@/components/AppIcon.vue'
import { useAuthStore } from '@/stores/auth'
import { useLaunchStore } from '@/stores/launch'
import { useInstallsStore } from '@/stores/installs'
import { useNotificationsStore } from '@/stores/notifications'

const props = withDefaults(defineProps<{ instanceId: string; size?: 'small' | 'normal' | 'large'; compact?: boolean }>(), {
  size: 'normal',
  compact: false
})

const authStore = useAuthStore()
const launchStore = useLaunchStore()
const installs = useInstallsStore()
const notifications = useNotificationsStore()

const state = computed(() => launchStore.getState(props.instanceId))
const installing = computed(() => installs.isInstalling(props.instanceId))
const sizeClass = computed(() => ({
  'btn--small': props.size === 'small',
  'btn--large': props.size === 'large',
  'play-btn--compact': props.compact
}))
const iconSize = computed(() => (props.size === 'small' ? 12 : props.size === 'large' ? 20 : 16))
const busyLabel = computed(() => (state.value.phase === 'installing' ? 'Installing' : 'Starting'))

const percent = computed(() => {
  const p = state.value.progress
  if (!p || p.total === 0) return 0
  return Math.min(100, Math.round((p.current / p.total) * 100))
})

async function signIn() {
  try {
    await authStore.login()
  } catch (e) {
    notifications.notifyError('Sign-in failed', String(e))
  }
}

async function play() {
  try {
    await launchStore.play(props.instanceId)
  } catch (e) {
    notifications.notifyError('Could not start the game', String(e))
  }
}

async function stop() {
  try {
    await launchStore.stop(props.instanceId)
  } catch (e) {
    notifications.notifyError('Could not stop the game', String(e))
  }
}
</script>

<style scoped lang="scss">
.play-btn {
  position: relative;
  overflow: hidden;
  min-width: 7.5rem;
}

.play-btn > span {
  position: relative;
}

.play-btn--compact {
  min-width: 0;
  width: 34px;
  height: 34px;
  padding: 0;

  .play-btn__label {
    display: none;
  }
}

.play-btn__fill {
  position: absolute !important;
  inset: 0 auto 0 0;
  background-color: var(--accent);
  opacity: 0.35;
  transition: width 0.2s ease;
}

.play-btn__spinner {
  width: 10px;
  height: 10px;
  border: 1px solid currentColor;
  border-right-color: transparent;
  border-radius: 50%;
  animation: play-spin 0.8s linear infinite;
}

@keyframes play-spin {
  to {
    transform: rotate(360deg);
  }
}
</style>
