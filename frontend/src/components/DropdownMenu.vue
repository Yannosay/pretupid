<template>
  <div ref="anchorRef" class="dropdown" @click.stop>
    <slot name="trigger" :open="open" :toggle="toggle" />
    <Teleport to="body">
      <div
        v-if="open"
        ref="menuRef"
        class="menu dropdown__menu"
        role="menu"
        :style="menuStyle"
        @click.stop
        @keydown.esc.prevent="close"
      >
        <slot :close="close" />
      </div>
    </Teleport>
  </div>
</template>

<script setup lang="ts">
import { nextTick, onBeforeUnmount, ref, watch } from 'vue'

const VIEWPORT_MARGIN = 8
const GAP = 4

const props = withDefaults(defineProps<{ align?: 'left' | 'right' }>(), { align: 'right' })

const open = ref(false)
const anchorRef = ref<HTMLElement | null>(null)
const menuRef = ref<HTMLElement | null>(null)
const menuStyle = ref<Record<string, string>>({ visibility: 'hidden' })

function position() {
  const anchor = anchorRef.value
  const menu = menuRef.value
  if (!anchor || !menu) return
  const rect = anchor.getBoundingClientRect()
  const viewportWidth = window.innerWidth
  const viewportHeight = window.innerHeight
  const naturalHeight = menu.scrollHeight
  const width = menu.offsetWidth
  const below = viewportHeight - rect.bottom - GAP - VIEWPORT_MARGIN
  const above = rect.top - GAP - VIEWPORT_MARGIN
  const placeBelow = below >= naturalHeight || below >= above
  const available = Math.max(120, placeBelow ? below : above)
  const height = Math.min(naturalHeight, available)
  const top = placeBelow ? rect.bottom + GAP : rect.top - GAP - height
  let left = props.align === 'right' ? rect.right - width : rect.left
  left = Math.min(Math.max(VIEWPORT_MARGIN, left), viewportWidth - width - VIEWPORT_MARGIN)
  menuStyle.value = {
    position: 'fixed',
    top: `${Math.max(VIEWPORT_MARGIN, top)}px`,
    left: `${left}px`,
    maxHeight: `${available}px`,
    overflowY: naturalHeight > available ? 'auto' : 'visible',
    visibility: 'visible'
  }
}

function onPointerDown(event: PointerEvent) {
  const target = event.target as Node
  if (menuRef.value?.contains(target) || anchorRef.value?.contains(target)) return
  close()
}

function onScroll(event: Event) {
  if (menuRef.value?.contains(event.target as Node)) return
  close()
}

function onKeyDown(event: KeyboardEvent) {
  if (event.key === 'Escape') close()
}

function attach() {
  document.addEventListener('pointerdown', onPointerDown, true)
  document.addEventListener('keydown', onKeyDown)
  window.addEventListener('resize', close)
  window.addEventListener('scroll', onScroll, true)
}

function detach() {
  document.removeEventListener('pointerdown', onPointerDown, true)
  document.removeEventListener('keydown', onKeyDown)
  window.removeEventListener('resize', close)
  window.removeEventListener('scroll', onScroll, true)
}

function close() {
  open.value = false
}

function toggle() {
  open.value = !open.value
}

watch(open, async (value) => {
  if (value) {
    menuStyle.value = { position: 'fixed', top: '0px', left: '0px', visibility: 'hidden' }
    await nextTick()
    position()
    attach()
  } else {
    detach()
  }
})

onBeforeUnmount(detach)

defineExpose({ close })
</script>

<style scoped>
.dropdown {
  position: relative;
  display: inline-flex;
}

.dropdown__menu {
  z-index: 4000;
}
</style>
