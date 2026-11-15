<template>
  <div class="scenery" :class="[`scenery--${phase}`, { 'scenery--still': !moving }]" aria-hidden="true">
    <svg class="scenery__svg" :viewBox="`0 0 ${WIDTH} ${HEIGHT}`" preserveAspectRatio="xMidYMax slice" shape-rendering="crispEdges">
      <defs>
        <linearGradient :id="`sky-${uid}`" x1="0" y1="0" x2="0" y2="1">
          <stop offset="0%" :stop-color="palette.skyTop" />
          <stop offset="100%" :stop-color="palette.skyBottom" />
        </linearGradient>
      </defs>
      <rect :width="WIDTH" :height="HEIGHT" :fill="`url(#sky-${uid})`" />
      <g :transform="depth(SKY_DEPTH)">
        <g v-if="phase === 'night'" class="scenery__stars">
          <rect v-for="(star, i) in stars" :key="i" :x="star.x" :y="star.y" :width="star.s" :height="star.s" :style="{ animationDelay: `${star.d}s` }" />
        </g>
        <g class="scenery__orb">
          <rect :x="orb.x" :y="orb.y" :width="orb.size" :height="orb.size" :fill="palette.orb" />
          <rect :x="orb.x - 8" :y="orb.y - 8" :width="orb.size + 16" :height="orb.size + 16" :fill="palette.orb" opacity="0.14" />
        </g>
      </g>
      <g :transform="depth(CLOUD_DEPTH)" class="scenery__clouds">
        <g v-for="(cloud, i) in clouds" :key="i" class="scenery__cloud" :style="{ animationDuration: `${cloud.speed}s`, animationDelay: `${-cloud.offset}s` }">
          <rect v-for="(part, j) in cloud.parts" :key="j" :x="cloud.x + part[0]" :y="cloud.y + part[1]" :width="part[2]" :height="BLOCK" :fill="palette.cloud" />
        </g>
      </g>
      <g v-for="(layer, i) in layers" :key="i" :transform="depth(LAYER_DEPTHS[i])">
        <g class="scenery__layer" :style="{ animationDuration: `${DRIFT_SECONDS[i]}s`, animationDelay: `${-layer.offset}s` }">
          <path :d="layer.path" :fill="layer.fill" />
        </g>
      </g>
    </svg>
    <div v-if="fade" class="scenery__fade" />
  </div>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'

const props = withDefaults(defineProps<{ seed?: string; moving?: boolean; parallax?: number; fade?: boolean }>(), {
  seed: 'pretupid',
  moving: true,
  parallax: 0,
  fade: true
})

const SKY_DEPTH = 0.55
const CLOUD_DEPTH = 0.45
const LAYER_DEPTHS = [0.36, 0.26, 0.16, 0.06]

function depth(factor: number): string | undefined {
  return props.parallax ? `translate(0 ${(props.parallax * factor).toFixed(1)})` : undefined
}

const WIDTH = 960
const HEIGHT = 360
const BLOCK = 12
const COLUMNS = WIDTH / BLOCK
const DRIFT_SECONDS = [420, 280, 180, 120]
const uid = Math.random().toString(36).slice(2, 8)

type Phase = 'dawn' | 'day' | 'dusk' | 'night'

const hour = ref(new Date().getHours())
let timer: number | null = null

onMounted(() => {
  timer = window.setInterval(() => {
    hour.value = new Date().getHours()
  }, 60_000)
})

onBeforeUnmount(() => {
  if (timer !== null) window.clearInterval(timer)
})

const phase = computed<Phase>(() => {
  const h = hour.value
  if (h >= 5 && h < 9) return 'dawn'
  if (h >= 9 && h < 17) return 'day'
  if (h >= 17 && h < 21) return 'dusk'
  return 'night'
})

const PALETTES: Record<Phase, { skyTop: string; skyBottom: string; orb: string; cloud: string; hills: string[] }> = {
  dawn: { skyTop: '#2a2d5c', skyBottom: '#e8957a', orb: '#ffd9a0', cloud: 'rgba(255, 214, 196, 0.55)', hills: ['#5b4a78', '#3d3459', '#262240', '#14131f'] },
  day: { skyTop: '#2f6fb3', skyBottom: '#8fc6e8', orb: '#fff3b0', cloud: 'rgba(255, 255, 255, 0.75)', hills: ['#4f86a8', '#3b6a55', '#2a5038', '#132219'] },
  dusk: { skyTop: '#1f1838', skyBottom: '#c75a5a', orb: '#ffb36b', cloud: 'rgba(255, 170, 150, 0.45)', hills: ['#57345a', '#3a2444', '#24172d', '#120c17'] },
  night: { skyTop: '#05070f', skyBottom: '#1a2547', orb: '#e9eef7', cloud: 'rgba(160, 175, 210, 0.22)', hills: ['#1d2a4a', '#152036', '#0e1626', '#070b14'] }
}

const palette = computed(() => PALETTES[phase.value])

function hash(value: string): number {
  let h = 2166136261
  for (let i = 0; i < value.length; i++) {
    h ^= value.charCodeAt(i)
    h = Math.imul(h, 16777619)
  }
  return h >>> 0
}

function rng(seed: number): () => number {
  let state = seed || 1
  return () => {
    state ^= state << 13
    state ^= state >>> 17
    state ^= state << 5
    return (state >>> 0) / 4294967296
  }
}

function ridge(rand: () => number, base: number, amplitude: number, roughness: number): string {
  const waves = [1, 2, 3].map((frequency) => ({ frequency, phase: rand() * Math.PI * 2, weight: rand() }))
  const total = waves.reduce((sum, wave) => sum + wave.weight, 0) || 1
  const noise = Array.from({ length: COLUMNS }, () => (rand() < roughness ? Math.round((rand() - 0.5) * 2) : 0))
  const heights = Array.from({ length: COLUMNS }, (_, c) => {
    const angle = (c / COLUMNS) * Math.PI * 2
    const shape = waves.reduce((sum, wave) => sum + Math.sin(angle * wave.frequency + wave.phase) * wave.weight, 0) / total
    const blocks = Math.round((base + shape * amplitude) / BLOCK) + noise[c]
    return blocks * BLOCK
  })
  let d = `M${-WIDTH} ${HEIGHT}`
  for (let tile = -1; tile <= 1; tile++) {
    for (let c = 0; c < COLUMNS; c++) {
      const x = tile * WIDTH + c * BLOCK
      const y = HEIGHT - heights[c]
      d += ` L${x} ${y} L${x + BLOCK} ${y}`
    }
  }
  return `${d} L${WIDTH * 2} ${HEIGHT} Z`
}

const layers = computed(() => {
  const rand = rng(hash(props.seed))
  const specs = [
    { base: 230, amplitude: 70, roughness: 0.35 },
    { base: 170, amplitude: 48, roughness: 0.3 },
    { base: 112, amplitude: 30, roughness: 0.25 },
    { base: 56, amplitude: 18, roughness: 0.2 }
  ]
  return specs.map((spec, i) => ({
    path: ridge(rand, spec.base, spec.amplitude, spec.roughness),
    fill: palette.value.hills[i],
    offset: rand() * DRIFT_SECONDS[i]
  }))
})

const stars = computed(() => {
  const rand = rng(hash(props.seed + 'stars'))
  return Array.from({ length: 70 }, () => ({
    x: Math.floor(rand() * (WIDTH / 4)) * 4,
    y: Math.floor(rand() * 50) * 4,
    s: rand() < 0.2 ? 4 : 2,
    d: Math.round(rand() * 40) / 10
  }))
})

const clouds = computed(() => {
  const rand = rng(hash(props.seed + 'clouds'))
  return Array.from({ length: 5 }, () => {
    const width = 4 + Math.floor(rand() * 5)
    const parts: [number, number, number][] = [
      [BLOCK * 2, 0, BLOCK * Math.max(2, width - 3)],
      [0, BLOCK, BLOCK * width]
    ]
    return {
      x: Math.floor(rand() * WIDTH),
      y: 24 + Math.floor(rand() * 7) * BLOCK,
      speed: 300 + rand() * 180,
      offset: rand() * 480,
      parts
    }
  })
})

const orb = computed(() => {
  const size = 36
  const positions: Record<Phase, [number, number]> = { dawn: [140, 150], day: [700, 48], dusk: [820, 140], night: [760, 56] }
  const [x, y] = positions[phase.value]
  return { x, y, size }
})
</script>

<style scoped lang="scss">
.scenery {
  position: absolute;
  inset: 0;
  overflow: hidden;
  pointer-events: none;
  mask-image: linear-gradient(180deg, #000 60%, transparent 100%);
}

.scenery__svg {
  position: absolute;
  inset: 0;
  width: 100%;
  height: 100%;
}

.scenery__layer {
  animation: scenery-pan linear infinite;
}

.scenery__stars rect {
  fill: #ffffff;
  animation: scenery-twinkle 4s ease-in-out infinite;
}

.scenery__cloud {
  animation: scenery-drift linear infinite;
}

.scenery--still {
  .scenery__layer,
  .scenery__cloud,
  .scenery__stars rect {
    animation-play-state: paused;
  }
}

.scenery__fade {
  position: absolute;
  inset: 0;
  background: linear-gradient(90deg, rgba(5, 7, 12, 0.82) 0%, rgba(5, 7, 12, 0.35) 55%, transparent 80%);
}

@keyframes scenery-pan {
  from {
    transform: translateX(0);
  }
  to {
    transform: translateX(-960px);
  }
}

@keyframes scenery-drift {
  from {
    transform: translateX(960px);
  }
  to {
    transform: translateX(-1200px);
  }
}

@keyframes scenery-twinkle {
  50% {
    opacity: 0.25;
  }
}

@media (prefers-reduced-motion: reduce) {
  .scenery__layer,
  .scenery__cloud,
  .scenery__stars rect {
    animation: none;
  }
}
</style>
