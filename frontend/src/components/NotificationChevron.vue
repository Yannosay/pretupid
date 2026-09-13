<template>
  <div ref="rootRef" class="notify">
    <button
      type="button"
      class="notify__trigger"
      :class="{ 'notify__trigger--open': store.open }"
      aria-haspopup="true"
      :aria-expanded="store.open"
      @click.stop="store.toggle()"
    >
      <span class="notify__icon" aria-hidden="true">
        <svg viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="square" stroke-linejoin="miter">
          <path d="M6 8a6 6 0 1 1 12 0v6l2 3H4l2-3z" />
          <path d="M10 21h4" />
        </svg>
      </span>
      <span class="notify__label">Notifications</span>
      <span v-if="store.unreadCount > 0" class="notify__badge">{{ store.unreadCount }}</span>
      <span class="notify__chevron" :class="{ 'notify__chevron--open': store.open }" aria-hidden="true">▾</span>
    </button>

    <Transition name="notify-panel">
      <div v-if="store.open" class="notify__panel" role="dialog" aria-label="Notifications">
        <header class="notify__panel-header">
          <h3 class="notify__panel-title">Notifications</h3>
          <div class="notify__panel-actions">
            <button type="button" class="notify__panel-action" :disabled="!store.hasItems" @click="store.markAllRead()">Mark read</button>
            <button type="button" class="notify__panel-action" :disabled="!store.hasItems" @click="store.clearAll()">Clear</button>
          </div>
        </header>

        <div v-if="!store.hasItems" class="notify__empty">
          <span class="notify__empty-icon" aria-hidden="true">✧</span>
          <p class="notify__empty-text">All caught up.</p>
          <p class="notify__empty-hint">Updates, downloads and errors will appear here.</p>
        </div>

        <ul v-else class="notify__list">
          <li
            v-for="item in store.items"
            :key="item.id"
            class="notify__item"
            :class="[`notify__item--${item.kind}`, { 'notify__item--unread': !item.read }]"
          >
            <span class="notify__item-icon" aria-hidden="true">{{ iconFor(item.kind) }}</span>
            <div class="notify__item-body">
              <p class="notify__item-title">{{ item.title }}</p>
              <p class="notify__item-message">{{ item.message }}</p>
              <button
                v-if="item.actionLabel && item.actionHandler"
                type="button"
                class="notify__item-action"
                @click="runAction(item)"
              >
                {{ item.actionLabel }}
              </button>
            </div>
            <button
              type="button"
              class="notify__item-dismiss"
              aria-label="Dismiss notification"
              @click="store.dismiss(item.id)"
            >
              ×
            </button>
          </li>
        </ul>
      </div>
    </Transition>
  </div>
</template>

<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref } from 'vue'
import { useNotificationsStore, type AppNotification, type NotificationKind } from '@/stores/notifications'

const store = useNotificationsStore()
const rootRef = ref<HTMLElement | null>(null)

function iconFor(kind: NotificationKind): string {
  switch (kind) {
    case 'success':
      return '✓'
    case 'warning':
      return '!'
    case 'error':
      return '×'
    default:
      return 'i'
  }
}

function runAction(item: AppNotification) {
  if (item.actionHandler) {
    item.actionHandler()
  }
  store.dismiss(item.id)
}

function onDocumentClick(event: MouseEvent) {
  if (!store.open) return
  const root = rootRef.value
  if (!root) return
  if (!root.contains(event.target as Node)) {
    store.close()
  }
}

function onKeydown(event: KeyboardEvent) {
  if (event.key === 'Escape' && store.open) {
    store.close()
  }
}

onMounted(() => {
  document.addEventListener('click', onDocumentClick)
  document.addEventListener('keydown', onKeydown)
})

onBeforeUnmount(() => {
  document.removeEventListener('click', onDocumentClick)
  document.removeEventListener('keydown', onKeydown)
})
</script>