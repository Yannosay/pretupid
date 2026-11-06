<template>
  <div class="instance-icon" :style="iconStyle">
    <img v-if="instance.icon_url && !broken" :src="instance.icon_url" :alt="instance.name" @error="broken = true" />
    <span v-else class="instance-icon__initials">{{ initials(instance.name) }}</span>
  </div>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import type { Instance } from '@/types'
import { hueFromString, initials } from '@/utils/format'

const props = withDefaults(defineProps<{ instance: Pick<Instance, 'id' | 'name' | 'icon_url'>; size?: number }>(), {
  size: 56
})

const broken = ref(false)

const iconStyle = computed(() => {
  const hue = hueFromString(props.instance.id + props.instance.name)
  return {
    width: `${props.size}px`,
    height: `${props.size}px`,
    fontSize: `${Math.max(0.45, props.size / 90)}rem`,
    '--tile-a': `hsl(${hue} 55% 42%)`,
    '--tile-b': `hsl(${(hue + 40) % 360} 60% 30%)`
  }
})
</script>

<style scoped lang="scss">
.instance-icon {
  position: relative;
  flex-shrink: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  overflow: hidden;
  border: 2px solid var(--border-color);
  border-radius: var(--edge-radius);
  background-color: var(--tile-a);
  background-image:
    linear-gradient(45deg, var(--tile-b) 25%, transparent 25%),
    linear-gradient(-45deg, var(--tile-b) 25%, transparent 25%),
    linear-gradient(45deg, transparent 75%, var(--tile-b) 75%),
    linear-gradient(-45deg, transparent 75%, var(--tile-b) 75%);
  background-size: 16px 16px;
  background-position: 0 0, 0 8px, 8px -8px, -8px 0;
  image-rendering: pixelated;

  img {
    width: 100%;
    height: 100%;
    object-fit: cover;
    image-rendering: auto;
  }
}

.instance-icon__initials {
  font-family: var(--font-pixel);
  color: #ffffff;
  text-shadow: 2px 2px 0 rgba(0, 0, 0, 0.45);
}
</style>
