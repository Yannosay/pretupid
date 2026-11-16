<template>
  <svg
    class="site-icon"
    :width="size"
    :height="size"
    viewBox="0 0 24 24"
    fill="none"
    stroke="currentColor"
    stroke-width="2"
    stroke-linecap="square"
    stroke-linejoin="miter"
    aria-hidden="true"
  >
    <path v-for="(d, i) in paths" :key="i" :d="d" />
  </svg>
</template>

<script setup lang="ts">
import { computed } from 'vue'

export type SiteIconName =
  | 'bolt'
  | 'box'
  | 'puzzle'
  | 'shield'
  | 'lock'
  | 'check'
  | 'refresh'
  | 'toggle'
  | 'upload'
  | 'download'
  | 'alert'
  | 'clock'
  | 'server'
  | 'copy'
  | 'palette'
  | 'coffee'
  | 'folder'
  | 'eye-off'
  | 'key'
  | 'layers'
  | 'wrench'
  | 'terminal'
  | 'github'
  | 'bug'
  | 'mail'
  | 'sparkle'
  | 'users'
  | 'cpu'

const props = withDefaults(defineProps<{ name: SiteIconName; size?: number }>(), { size: 24 })

const ICONS: Record<SiteIconName, string[]> = {
  bolt: ['M13 2L4 14h7l-1 8 9-12h-7z'],
  box: ['M3 7l9-4 9 4-9 4z', 'M3 7v10l9 4 9-4V7', 'M12 11v10'],
  puzzle: ['M4 8h4a2 2 0 1 1 4 0h4v4a2 2 0 1 1 0 4v4H4z'],
  shield: ['M12 3l8 3v6c0 5-3.5 8-8 9-4.5-1-8-4-8-9V6z'],
  lock: ['M5 11h14v10H5z', 'M8 11V7a4 4 0 0 1 8 0v4'],
  check: ['M4 12l5 5L20 6'],
  refresh: ['M20 12a8 8 0 1 1-2.3-5.7', 'M20 4v5h-5'],
  toggle: ['M3 8h18v8H3z', 'M15 12h.01'],
  upload: ['M12 20V9', 'M7 14l5-5 5 5', 'M4 4h16'],
  download: ['M12 4v11', 'M7 10l5 5 5-5', 'M4 20h16'],
  alert: ['M12 3l10 18H2z', 'M12 10v5', 'M12 18h.01'],
  clock: ['M12 3a9 9 0 1 0 0 18a9 9 0 1 0 0-18z', 'M12 7v5l3 3'],
  server: ['M3 4h18v7H3z', 'M3 13h18v7H3z', 'M7 7.5h.01', 'M7 16.5h.01'],
  copy: ['M9 9h11v11H9z', 'M5 15H4V4h11v1'],
  palette: ['M12 3a9 9 0 1 0 0 18c1 0 2-1 2-2s-1-2 0-3h3a4 4 0 0 0 4-4c0-5-4-9-9-9z', 'M7.5 11h.01', 'M11 7h.01', 'M16 8h.01'],
  coffee: ['M4 8h13v6a5 5 0 0 1-5 5H9a5 5 0 0 1-5-5z', 'M17 10h2a2 2 0 0 1 0 4h-2', 'M8 3v2', 'M12 3v2'],
  folder: ['M3 6h6l2 2h10v11H3z'],
  'eye-off': ['M3 3l18 18', 'M10.6 6.1A10 10 0 0 1 12 6c5 0 9 6 9 6a17 17 0 0 1-3 3.5', 'M6.6 6.6C4 8.3 3 12 3 12s4 6 9 6a9 9 0 0 0 4-1'],
  key: ['M15 3a6 6 0 1 1-5.6 8.2L3 17.6V21h3.4l1-1v-2h2v-2h2l1.4-1.4A6 6 0 0 1 15 3z', 'M16 8h.01'],
  layers: ['M12 3l9 5-9 5-9-5z', 'M3 13l9 5 9-5'],
  wrench: ['M14.5 6.5a4 4 0 0 0 5 5L12 19l-3 1 1-3 7.5-7.5', 'M14.5 6.5L17 4'],
  terminal: ['M3 4h18v16H3z', 'M7 9l3 3-3 3', 'M12 15h5'],
  github: ['M9 19c-4 1.5-4-2-6-2.5', 'M15 21v-3.5c0-1 .1-1.4-.5-2 2.8-.3 5.5-1.4 5.5-6a4.6 4.6 0 0 0-1.3-3.2 4.3 4.3 0 0 0-.1-3.2s-1-.3-3.4 1.3a11.6 11.6 0 0 0-6 0C6.8 2.8 5.8 3.1 5.8 3.1a4.3 4.3 0 0 0-.1 3.2A4.6 4.6 0 0 0 4.4 9.5c0 4.6 2.7 5.7 5.5 6-.6.6-.6 1.2-.5 2V21'],
  bug: ['M8 8h8v9a4 4 0 0 1-8 0z', 'M9 4l1.5 2', 'M15 4l-1.5 2', 'M4 12h4', 'M16 12h4', 'M5 18l3-2', 'M19 18l-3-2'],
  mail: ['M3 5h18v14H3z', 'M3 6l9 7 9-7'],
  sparkle: ['M12 3v5', 'M12 16v5', 'M3 12h5', 'M16 12h5', 'M8 8l2 2', 'M14 14l2 2', 'M16 8l-2 2', 'M10 14l-2 2'],
  users: ['M9 11a4 4 0 1 0 0-8 4 4 0 0 0 0 8z', 'M2 21v-1a6 6 0 0 1 6-6h2a6 6 0 0 1 6 6v1', 'M16 3.5a4 4 0 0 1 0 7', 'M19 14a5 5 0 0 1 3 4.5V21'],
  cpu: ['M6 6h12v12H6z', 'M9 9h6v6H9z', 'M9 2v4', 'M15 2v4', 'M9 18v4', 'M15 18v4', 'M2 9h4', 'M2 15h4', 'M18 9h4', 'M18 15h4']
}

const paths = computed(() => ICONS[props.name] ?? [])
</script>

<style scoped>
.site-icon {
  flex-shrink: 0;
  display: inline-block;
  vertical-align: middle;
}
</style>
