<template>
  <div class="instance-icon" :class="{ 'instance-icon--pending': display.kind === 'pending' }" :style="iconStyle">
    <img v-if="display.kind === 'image'" :src="display.src" :alt="instance.name" draggable="false" @error="broken = true" />
    <img v-else-if="display.kind === 'block'" class="instance-icon__block" :src="display.src" alt="" draggable="false" />
    <span v-else class="instance-icon__letter">{{ letter }}</span>
  </div>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import type { Instance } from '@/types'
import { autoMaterial, blockTexture, parseIconStyle } from '@/utils/instanceIcon'

type IconSource = Pick<Instance, 'id' | 'name' | 'icon_url'> & { icon_style?: string | null }

const props = withDefaults(defineProps<{ instance: IconSource; size?: number }>(), {
  size: 56
})

const broken = ref(false)

watch(
  () => props.instance.icon_url,
  () => {
    broken.value = false
  }
)

const material = computed(() => parseIconStyle(props.instance.icon_style) ?? autoMaterial(props.instance.id))

const display = computed(() => {
  if (props.instance.icon_url && !broken.value) return { kind: 'image' as const, src: props.instance.icon_url }
  const texture = blockTexture(material.value)
  return texture ? { kind: 'block' as const, src: texture } : { kind: 'pending' as const, src: '' }
})

const letter = computed(() => {
  const trimmed = props.instance.name.trim()
  return trimmed ? Array.from(trimmed)[0].toUpperCase() : '?'
})

const iconStyle = computed(() => ({
  width: `${props.size}px`,
  height: `${props.size}px`,
  fontSize: `${Math.round(props.size * 0.42)}px`
}))
</script>

<style scoped lang="scss">
.instance-icon {
  position: relative;
  flex-shrink: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  overflow: hidden;
  border-radius: var(--edge-radius);
  background-color: var(--bg-tertiary);
  box-shadow:
    inset 0 0 0 1px rgba(255, 255, 255, 0.08),
    inset 0 -3px 0 rgba(0, 0, 0, 0.22);
  user-select: none;

  img {
    width: 100%;
    height: 100%;
    object-fit: cover;
  }
}

.instance-icon__block {
  object-position: top;
  image-rendering: pixelated;
}

.instance-icon--pending {
  background: linear-gradient(110deg, var(--bg-tertiary) 30%, var(--surface-hover) 50%, var(--bg-tertiary) 70%);
  background-size: 200% 100%;
  animation: instance-icon-loading 1.4s linear infinite;
}

.instance-icon__letter {
  font-family: var(--font-pixel);
  line-height: 1;
  color: var(--text-secondary);
}

@keyframes instance-icon-loading {
  to {
    background-position: -200% 0;
  }
}

@media (prefers-reduced-motion: reduce) {
  .instance-icon--pending {
    animation: none;
  }
}
</style>
