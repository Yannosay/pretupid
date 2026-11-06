<template>
  <svg
    class="app-icon"
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

export type IconName =
  | 'home'
  | 'library'
  | 'compass'
  | 'user'
  | 'settings'
  | 'play'
  | 'stop'
  | 'folder'
  | 'plus'
  | 'refresh'
  | 'trash'
  | 'copy'
  | 'download'
  | 'upload'
  | 'search'
  | 'more'
  | 'check'
  | 'x'
  | 'wrench'
  | 'clock'
  | 'puzzle'
  | 'image'
  | 'sparkle'
  | 'box'
  | 'back'
  | 'external'
  | 'alert'
  | 'terminal'
  | 'sliders'
  | 'arrow-up'
  | 'login'
  | 'logout'
  | 'layers'
  | 'server'
  | 'info'

const props = withDefaults(defineProps<{ name: IconName; size?: number }>(), { size: 18 })

const ICONS: Record<IconName, string[]> = {
  home: ['M3 11l9-8 9 8', 'M5 10v10h14V10', 'M10 20v-6h4v6'],
  library: ['M4 4h4v16H4z', 'M10 4h4v16h-4z', 'M16 5l4 1-3 14-4-1'],
  compass: ['M12 3a9 9 0 1 0 0 18a9 9 0 1 0 0-18z', 'M15.5 8.5l-2 5-5 2 2-5z'],
  user: ['M12 4a4 4 0 1 0 0 8a4 4 0 1 0 0-8z', 'M4 21c1-4 4.5-6 8-6s7 2 8 6'],
  settings: ['M12 9a3 3 0 1 0 0 6a3 3 0 1 0 0-6z', 'M12 2v3', 'M12 19v3', 'M2 12h3', 'M19 12h3', 'M4.9 4.9l2.1 2.1', 'M17 17l2.1 2.1', 'M4.9 19.1L7 17', 'M17 7l2.1-2.1'],
  play: ['M7 4l13 8-13 8z'],
  stop: ['M6 6h12v12H6z'],
  folder: ['M3 6h6l2 2h10v11H3z'],
  plus: ['M12 5v14', 'M5 12h14'],
  refresh: ['M20 12a8 8 0 1 1-2.3-5.7', 'M20 4v5h-5'],
  trash: ['M4 7h16', 'M9 7V4h6v3', 'M6 7l1 13h10l1-13'],
  copy: ['M9 9h11v11H9z', 'M5 15H4V4h11v1'],
  download: ['M12 4v11', 'M7 10l5 5 5-5', 'M4 20h16'],
  upload: ['M12 20V9', 'M7 14l5-5 5 5', 'M4 4h16'],
  search: ['M10.5 4a6.5 6.5 0 1 0 0 13a6.5 6.5 0 1 0 0-13z', 'M15.5 15.5L21 21'],
  more: ['M5 12h.01', 'M12 12h.01', 'M19 12h.01'],
  check: ['M4 12l5 5L20 6'],
  x: ['M6 6l12 12', 'M18 6L6 18'],
  wrench: ['M14.5 6.5a4 4 0 0 0 5 5L12 19l-3 1 1-3 7.5-7.5', 'M14.5 6.5L17 4'],
  clock: ['M12 3a9 9 0 1 0 0 18a9 9 0 1 0 0-18z', 'M12 7v5l3 3'],
  puzzle: ['M4 8h4a2 2 0 1 1 4 0h4v4a2 2 0 1 1 0 4v4H4z'],
  image: ['M3 5h18v14H3z', 'M3 16l5-5 4 4 3-3 6 6', 'M15 9h.01'],
  sparkle: ['M12 3l2 6 6 2-6 2-2 6-2-6-6-2 6-2z', 'M19 3v4', 'M17 5h4'],
  box: ['M3 7l9-4 9 4-9 4z', 'M3 7v10l9 4 9-4V7', 'M12 11v10'],
  back: ['M15 5l-7 7 7 7'],
  external: ['M14 4h6v6', 'M20 4l-9 9', 'M18 14v6H4V6h6'],
  alert: ['M12 3l10 18H2z', 'M12 10v5', 'M12 18h.01'],
  terminal: ['M3 4h18v16H3z', 'M7 9l3 3-3 3', 'M12 15h5'],
  sliders: ['M4 6h10', 'M18 6h2', 'M4 12h4', 'M12 12h8', 'M4 18h12', 'M20 18h0', 'M14 4v4', 'M8 10v4', 'M16 16v4'],
  'arrow-up': ['M12 19V5', 'M5 12l7-7 7 7'],
  login: ['M10 4h10v16H10', 'M3 12h11', 'M10 8l4 4-4 4'],
  logout: ['M14 4H4v16h10', 'M10 12h11', 'M17 8l4 4-4 4'],
  layers: ['M12 3l9 5-9 5-9-5z', 'M3 13l9 5 9-5'],
  server: ['M3 4h18v7H3z', 'M3 13h18v7H3z', 'M7 7.5h.01', 'M7 16.5h.01'],
  info: ['M12 3a9 9 0 1 0 0 18a9 9 0 1 0 0-18z', 'M12 11v6', 'M12 7h.01']
}

const paths = computed(() => ICONS[props.name] ?? [])
</script>

<style scoped>
.app-icon {
  flex-shrink: 0;
  display: inline-block;
  vertical-align: middle;
}
</style>
