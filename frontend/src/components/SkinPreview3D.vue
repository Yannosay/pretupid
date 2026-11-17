<template>
  <div class="skin-preview">
    <div
      class="skin-preview__stage"
      :class="{ 'skin-preview__stage--plain': compact, 'skin-preview__stage--grabbing': dragging }"
      :style="{ width: `${width}px`, height: `${height}px` }"
      @pointerdown="onPointerDown"
      @mousedown.prevent
      @dragstart.prevent
      @selectstart.prevent
      @contextmenu.prevent
    >
      <slot name="backdrop" />
      <canvas ref="canvasRef" class="skin-preview__canvas" draggable="false"></canvas>
    </div>

    <div v-if="!compact" class="skin-preview__controls">
      <button
        v-for="animation in animations"
        :key="animation.type"
        type="button"
        class="btn btn--small"
        :class="[
          'skin-preview__control',
          activeAnimation === animation.type ? 'btn--active' : 'btn--ghost'
        ]"
        @click.stop="applyAnimation(animation.type)"
      >
        {{ animation.label }}
      </button>

      <button type="button" class="btn btn--ghost btn--small" @click.stop="resetCamera">
        Reset
      </button>
    </div>
  </div>
</template>

<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { SkinViewer, IdleAnimation, WalkingAnimation, FlyingAnimation } from 'skinview3d'

type AnimationType = 'idle' | 'walk' | 'fly'

const props = withDefaults(
  defineProps<{
    skinUrl: string
    capeUrl?: string | null
    width?: number
    height?: number
    compact?: boolean
  }>(),
  { width: 300, height: 480, compact: false, capeUrl: null }
)

const DEFAULT_SKIN_URL = 'https://mc-heads.net/skin/MHF_Steve'
const CAMERA_POSITIONS: Record<AnimationType, [number, number, number]> = {
  idle: [36, 16, 36],
  walk: [34, 14, 34],
  fly: [34, 24, 34]
}

const canvasRef = ref<HTMLCanvasElement | null>(null)
const activeAnimation = ref<AnimationType>('idle')
const dragging = ref(false)

const animations: { type: AnimationType; label: string }[] = [
  { type: 'idle', label: 'Idle' },
  { type: 'walk', label: 'Walk' },
  { type: 'fly', label: 'Fly' }
]

let viewer: SkinViewer | null = null
let loadGeneration = 0

function placeCamera(type: AnimationType) {
  if (!viewer) return
  const [x, y, z] = CAMERA_POSITIONS[type]
  viewer.camera.position.set(x, y, z)
  viewer.controls.target.set(0, 0, 0)
  viewer.camera.lookAt(0, 0, 0)
  viewer.controls.update()
}

function applyAnimation(type: AnimationType) {
  if (!viewer) return
  activeAnimation.value = type
  if (type === 'walk') {
    viewer.animation = new WalkingAnimation()
  } else if (type === 'fly') {
    viewer.animation = new FlyingAnimation()
  } else {
    viewer.animation = new IdleAnimation()
  }
  placeCamera(type)
}

function resetCamera() {
  placeCamera(activeAnimation.value)
}

async function loadSkin(url: string) {
  if (!viewer) return
  const generation = ++loadGeneration
  try {
    await viewer.loadSkin(url || DEFAULT_SKIN_URL, { model: 'auto-detect' })
  } catch {
    if (viewer && generation === loadGeneration && url !== DEFAULT_SKIN_URL) {
      await viewer.loadSkin(DEFAULT_SKIN_URL, { model: 'auto-detect' }).catch(() => undefined)
    }
  }
}

async function loadCape(url: string | null) {
  if (!viewer) return
  if (!url) {
    viewer.resetCape()
    return
  }
  await viewer.loadCape(url).catch(() => viewer?.resetCape())
}

function onPointerDown(event: PointerEvent) {
  if (event.button !== 0) return
  window.getSelection()?.removeAllRanges()
  dragging.value = true
  window.addEventListener('pointerup', onPointerEnd, { once: true })
  window.addEventListener('pointercancel', onPointerEnd, { once: true })
}

function onPointerEnd() {
  dragging.value = false
  window.removeEventListener('pointerup', onPointerEnd)
  window.removeEventListener('pointercancel', onPointerEnd)
}

onMounted(() => {
  if (!canvasRef.value) return
  viewer = new SkinViewer({
    canvas: canvasRef.value,
    width: props.width,
    height: props.height,
    zoom: 1.2
  })
  viewer.controls.enableZoom = !props.compact
  viewer.controls.enablePan = false
  viewer.controls.enableRotate = true
  applyAnimation('idle')
  void loadSkin(props.skinUrl)
  void loadCape(props.capeUrl)
})

watch(
  () => props.skinUrl,
  (url) => void loadSkin(url)
)

watch(
  () => props.capeUrl,
  (url) => void loadCape(url)
)

watch(
  () => [props.width, props.height] as const,
  ([width, height]) => viewer?.setSize(width, height)
)

onBeforeUnmount(() => {
  loadGeneration++
  onPointerEnd()
  viewer?.dispose()
  viewer = null
})
</script>

<style scoped lang="scss">
.skin-preview {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 1rem;
}

.skin-preview__stage {
  position: relative;
  margin: 0 auto;
  background-color: var(--bg-tertiary);
  border: 1px solid var(--border-color);
  border-radius: var(--edge-radius);
  overflow: hidden;
  cursor: grab;
  user-select: none;
  -webkit-user-select: none;
  -webkit-user-drag: none;
  touch-action: none;
}

.skin-preview__stage--plain {
  background-color: transparent;
  border-color: transparent;
}

.skin-preview__stage--grabbing {
  cursor: grabbing;
}

.skin-preview__canvas {
  position: relative;
  z-index: 1;
  display: block;
  max-width: none;
  pointer-events: auto;
  -webkit-user-drag: none;
}

.skin-preview__controls {
  display: flex;
  flex-wrap: wrap;
  justify-content: center;
  gap: 0.5rem;
}
</style>
