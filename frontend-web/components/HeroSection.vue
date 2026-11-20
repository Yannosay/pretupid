<template>
  <section class="hero">
    <div class="hero__world" aria-hidden="true">
      <ClientOnly>
        <PixelScenery seed="pretupid-website" :parallax="scroll" />
      </ClientOnly>
    </div>

    <div class="hero__content">

      <h1 class="hero__title">
        <span class="hero__logo" :style="{ '--logo': `url(${wordmark})` }">
          <img :src="wordmark" alt="Pretupid Launcher" width="1305" height="118" class="hero__logo-image" />
        </span>
      </h1>

      <p class="hero__subtitle">
        Fabric, Quilt, Forge and NeoForge in one launcher - Pretupid! Modrinth mods and modpacks install with their dependencies,
        and the ad-free Pretupid Client brings modules, cosmetics, emoji, screenies and friends to Minecraft 1.21.2 up to 26.3!
      </p>

      <div class="hero__actions">
        <a :href="primary.url" class="btn btn--large hero__download" rel="nofollow">
          <PlatformIcon :platform="primary.id" :size="18" />
          Download for {{ primary.label }}
        </a>
        <button type="button" class="btn btn--secondary btn--large hero__open" @click="openLauncher">
          Open Launcher
        </button>
      </div>

      <nav class="hero__platforms" aria-label="Downloads for other systems">
        <a
          v-for="option in platforms"
          :key="option.id"
          :href="option.url"
          class="hero__platform"
          :class="{ 'hero__platform--active': option.id === platform }"
          rel="nofollow"
        >
          <PlatformIcon :platform="option.id" :size="15" />
          {{ option.label }}
        </a>
        <NuxtLink to="/download" class="hero__platform hero__platform--more">All downloads</NuxtLink>
      </nav>

      <ul class="hero__loaders" aria-label="Supported loaders">
        <li v-for="loader in loaders" :key="loader">{{ loader }}</li>
      </ul>
    </div>

    <a href="#features" class="hero__scroll" aria-label="Scroll to features">
      <span class="hero__scroll-arrow" aria-hidden="true"></span>
    </a>
  </section>
</template>

<script setup lang="ts">
import PixelScenery from '@ui/PixelScenery.vue'
import wordmark from '@shared/branding/pretupid_logo_full.png'
import { useLauncherBridge } from '~/composables/useLauncherBridge'
import { platformLabel, useVisitorPlatform, type VisitorPlatform } from '~/composables/useVisitorPlatform'

interface PlatformOption {
  id: VisitorPlatform
  label: string
  url: string
}

const { openLauncher } = useLauncherBridge()
const { platform } = useVisitorPlatform()

const platforms: PlatformOption[] = [
  { id: 'windows', label: platformLabel('windows'), url: '/api/download/launcher/windows' },
  { id: 'macos', label: platformLabel('macos'), url: '/api/download/launcher/macos?variant=arm64' },
  { id: 'linux', label: platformLabel('linux'), url: '/api/download/launcher/linux?variant=appimage' }
]

const primary = computed(() => platforms.find((option) => option.id === platform.value) ?? platforms[0]!)

const loaders = ['Vanilla', 'Fabric', 'Quilt', 'Forge', 'NeoForge', 'Modrinth']

const scroll = ref(0)
let frame = 0
let reducedMotion = false

function onScroll() {
  if (frame || reducedMotion) return
  frame = requestAnimationFrame(() => {
    frame = 0
    scroll.value = Math.min(window.scrollY, 1600)
  })
}

onMounted(() => {
  reducedMotion = window.matchMedia('(prefers-reduced-motion: reduce)').matches
  onScroll()
  window.addEventListener('scroll', onScroll, { passive: true })
})

onBeforeUnmount(() => {
  window.removeEventListener('scroll', onScroll)
  if (frame) cancelAnimationFrame(frame)
})
</script>

<style scoped lang="scss">
@use 'design-system/mixins' as *;

.hero {
  --hero-ink: #f4f5ff;
  --hero-ink-soft: rgba(236, 238, 255, 0.86);
  position: relative;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  min-height: calc(100vh - 7rem);
  padding: 3rem 0 6rem;
  text-align: center;
}

.hero__world {
  position: absolute;
  top: -2rem;
  bottom: 0;
  left: calc(50% - 50vw);
  width: 100vw;
  z-index: 0;
  overflow: hidden;
  pointer-events: none;
}

.hero__content {
  position: relative;
  z-index: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 1.5rem;
  width: 100%;
  max-width: 54rem;
}


.hero__title {
  width: 100%;
  margin: 0.5rem 0 0;
  line-height: 0;
  animation: hero-rise 0.6s 0.08s ease-out both;
}

.hero__logo {
  position: relative;
  display: inline-block;
  width: min(100%, 46rem);
}

.hero__logo-image {
  display: block;
  width: 100%;
  height: auto;
  image-rendering: pixelated;
  filter: drop-shadow(0 6px 18px rgba(8, 8, 30, 0.55));
}

.hero__logo::after {
  content: '';
  position: absolute;
  inset: 0;
  background: linear-gradient(105deg, transparent 38%, rgba(255, 255, 255, 0.75) 50%, transparent 62%) no-repeat;
  background-size: 250% 100%;
  background-position: 160% 0;
  mask: var(--logo) center / 100% 100% no-repeat;
  -webkit-mask: var(--logo) center / 100% 100% no-repeat;
  mix-blend-mode: overlay;
  animation: hero-shine 7s 1.2s ease-in-out infinite;
  pointer-events: none;
}

.hero__subtitle {
  font-family: var(--font-body);
  font-size: clamp(1.1rem, 1.7vw, 1.4rem);
  line-height: 1.5;
  color: var(--hero-ink-soft);
  max-width: 44rem;
  margin: 0;
  text-shadow: 0 2px 14px rgba(0, 0, 0, 0.6);
  animation: hero-rise 0.6s 0.16s ease-out both;
}

.hero__actions {
  display: flex;
  flex-wrap: wrap;
  justify-content: center;
  gap: 1rem;
  margin-top: 0.5rem;
  animation: hero-rise 0.6s 0.24s ease-out both;
}

.hero__download {
  display: inline-flex;
  align-items: center;
  gap: 0.6rem;
  min-width: 240px;
  justify-content: center;
}

.hero__open {
  min-width: 200px;
  backdrop-filter: blur(6px);
}

.hero__platforms {
  display: flex;
  flex-wrap: wrap;
  justify-content: center;
  gap: 0.5rem;
  animation: hero-rise 0.6s 0.32s ease-out both;
}

.hero__platform {
  display: inline-flex;
  align-items: center;
  gap: 0.45rem;
  padding: 0.45rem 0.85rem;
  font-size: 0.95rem;
  font-weight: 600;
  color: var(--hero-ink-soft);
  text-decoration: none;
  background-color: rgba(12, 12, 28, 0.45);
  border: 1px solid rgba(255, 255, 255, 0.14);
  border-radius: var(--edge-radius);
  backdrop-filter: blur(6px);
  transition:
    color var(--transition-fast),
    background-color var(--transition-fast),
    border-color var(--transition-fast),
    transform var(--transition-fast);

  &:hover {
    color: var(--hero-ink);
    border-color: var(--accent);
    transform: translateY(-2px);
  }
}

.hero__platform--active {
  color: var(--hero-ink);
  border-color: color-mix(in srgb, var(--accent) 70%, transparent);
  background-color: color-mix(in srgb, var(--accent) 22%, rgba(12, 12, 28, 0.5));
}

.hero__platform--more {
  color: var(--hero-ink-soft);
}


.hero__loaders {
  display: flex;
  flex-wrap: wrap;
  justify-content: center;
  gap: 0.4rem 1.4rem;
  margin: 0.75rem 0 0;
  padding: 0;
  list-style: none;
  font-family: var(--font-pixel);
  font-size: 0.85rem;
  color: var(--hero-ink-soft);
  text-shadow: 0 2px 10px rgba(0, 0, 0, 0.7);
  animation: hero-rise 0.6s 0.4s ease-out both;

  li {
    display: inline-flex;
    align-items: center;
    gap: 0.5rem;
  }

  li::before {
    content: '';
    width: 6px;
    height: 6px;
    background-color: var(--accent);
  }
}

.hero__scroll {
  position: absolute;
  bottom: 1.5rem;
  left: 50%;
  z-index: 1;
  display: grid;
  place-items: center;
  width: 2.5rem;
  height: 2.5rem;
  transform: translateX(-50%);
  border: 2px solid rgba(255, 255, 255, 0.25);
  border-radius: var(--edge-radius);
  background-color: rgba(12, 12, 28, 0.35);
  animation: hero-nudge 2.4s ease-in-out infinite;

  &:hover {
    border-color: var(--accent);
  }
}

.hero__scroll-arrow {
  width: 10px;
  height: 10px;
  margin-top: -4px;
  border-right: 3px solid var(--hero-ink);
  border-bottom: 3px solid var(--hero-ink);
  transform: rotate(45deg);
}

@keyframes hero-rise {
  from {
    opacity: 0;
    transform: translateY(14px);
  }
  to {
    opacity: 1;
    transform: none;
  }
}

@keyframes hero-shine {
  0%,
  55% {
    background-position: 160% 0;
  }
  100% {
    background-position: -60% 0;
  }
}

@keyframes hero-nudge {
  0%,
  100% {
    transform: translate(-50%, 0);
  }
  50% {
    transform: translate(-50%, 6px);
  }
}

@media (prefers-reduced-motion: reduce) {
  .hero__logo::after,
  .hero__scroll {
    animation: none;
  }
}

@media (max-width: 700px) {
  .hero {
    min-height: auto;
    padding: 2.5rem 0 5rem;
  }

  .hero__actions {
    width: 100%;
  }

  .hero__download,
  .hero__open {
    flex: 1;
    min-width: 0;
  }
}
</style>
