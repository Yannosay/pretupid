<template>
  <div class="skin-preview">
    <canvas
      ref="canvasRef"
      class="skin-preview__canvas"
      :class="{ 'skin-preview__canvas--plain': compact }"
      :style="{ width: `${width}px`, height: `${height}px` }"
    ></canvas>

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
import {
  SkinViewer,
  IdleAnimation,
  WalkingAnimation,
  FlyingAnimation
} from 'skinview3d'

const props = withDefaults(
  defineProps<{
    skinUrl: string
    modelType: 'classic' | 'slim'
    width?: number
    height?: number
    compact?: boolean
  }>(),
  { width: 300, height: 480, compact: false }
)

const canvasRef = ref<HTMLCanvasElement | null>(null)
const activeAnimation = ref<'idle' | 'walk' | 'fly'>('idle')
const DEFAULT_SKIN_URL = 'https://mc-heads.net/skin/MHF_Steve'

let viewer: SkinViewer | null = null

const animations = [
  { type: 'idle' as const, label: 'Idle' },
  { type: 'walk' as const, label: 'Walk' },
  { type: 'fly' as const, label: 'Fly' }
]

function applyModelType() {
  if (!viewer) return
  viewer.playerObject.skin.modelType = props.modelType === 'slim' ? 'slim' : 'default'
}

function applyAnimation(type: 'idle' | 'walk' | 'fly') {
  if (!viewer) return

  activeAnimation.value = type

  if (type === 'walk') {
    viewer.animation = new WalkingAnimation()
    viewer.camera.position.set(34, 14, 34)
  } else if (type === 'fly') {
    viewer.animation = new FlyingAnimation()
    viewer.camera.position.set(34, 24, 34)
  } else {
    viewer.animation = new IdleAnimation()
    viewer.camera.position.set(36, 16, 36)
  }

  viewer.controls.target.set(0, 0, 0)
  viewer.camera.lookAt(0, 0, 0)
  viewer.controls.update()
}

function resetCamera() {
  if (!viewer) return
  viewer.camera.position.set(36, 16, 36)
  viewer.controls.target.set(0, 0, 0)
  viewer.camera.lookAt(0, 0, 0)
  viewer.controls.update()
}

onMounted(() => {
  if (!canvasRef.value) return

  viewer = new SkinViewer({
    canvas: canvasRef.value,
    width: props.width,
    height: props.height,
    skin: props.skinUrl || DEFAULT_SKIN_URL,
    model: props.modelType === 'slim' ? 'slim' : 'default',
    zoom: 1.2
  })

  applyAnimation('idle')
  viewer.controls.enableZoom = !props.compact
  viewer.controls.enablePan = false
  viewer.controls.enableRotate = true
  resetCamera()
})

watch(
  () => props.skinUrl,
  (url) => {
    if (viewer) {
      viewer
        .loadSkin(url || DEFAULT_SKIN_URL, {
          model: props.modelType === 'slim' ? 'slim' : 'default'
        })
        .catch(() => {})
      applyModelType()
    }
  }
)

watch(
  () => props.modelType,
  () => applyModelType()
)

onBeforeUnmount(() => {
  if (viewer) {
    viewer.dispose()
    viewer = null
  }
})
</script>

<style scoped lang="scss">
.skin-preview {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 1rem;
}

.skin-preview__canvas {
  max-width: none;
  display: block;
  margin: 0 auto;
  background-color: var(--bg-tertiary);
  border: 2px solid var(--border-color);
  border-radius: var(--edge-radius);
}

.skin-preview__canvas--plain {
  background-color: transparent;
  border-color: transparent;
}

.skin-preview__controls {
  display: flex;
  flex-wrap: wrap;
  justify-content: center;
  gap: 0.5rem;
}
</style>