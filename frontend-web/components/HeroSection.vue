<template>
  <section class="hero">
    <div class="hero__world" aria-hidden="true">
      <ClientOnly>
        <PixelScenery seed="pretupid-website" :parallax="scroll" />
      </ClientOnly>
    </div>

    <div class="hero__content">
      <span class="hero__eyebrow">Free Minecraft launcher and client</span>
      <h1 class="hero__title">Pretupid Launcher</h1>
      <p class="hero__subtitle">
        Fabric, Quilt, Forge and NeoForge in one launcher. Modrinth mods and modpacks install with their dependencies,
        and Pretupid Client adds a movable HUD, cosmetics and friends to any Fabric instance.
      </p>

      <div class="hero__actions">
        <a :href="downloadUrl" class="btn hero__cta hero__cta--primary" rel="nofollow">
          {{ downloadLabel }}
        </a>
        <button
          type="button"
          class="btn btn--secondary hero__cta"
          @click="openLauncher"
        >
          Open Launcher
        </button>
      </div>

      <ul class="hero__meta">
        <li><span class="hero__meta-dot" aria-hidden="true"></span>Every loader, natively installed</li>
        <li><span class="hero__meta-dot" aria-hidden="true"></span>Mods, packs and one-click updates</li>
        <li><span class="hero__meta-dot" aria-hidden="true"></span>Windows, macOS and Linux</li>
      </ul>
    </div>

    <div class="hero__visual">
      <div class="hero__visual-frame">
        <img
          src="/screenshots/launcher-home.webp"
          alt="The Pretupid Launcher home screen with recent instances and a 3D skin preview"
          class="hero__image"
          width="1600"
          height="1000"
          loading="eager"
          fetchpriority="high"
        />
        <div class="hero__visual-chrome" aria-hidden="true">
          <span class="hero__visual-dot"></span>
          <span class="hero__visual-dot"></span>
          <span class="hero__visual-dot"></span>
        </div>
      </div>
    </div>
  </section>
</template>

<script setup lang="ts">
import PixelScenery from '@ui/PixelScenery.vue'
import { useLauncherBridge } from '~/composables/useLauncherBridge'
import { platformLabel, useVisitorPlatform } from '~/composables/useVisitorPlatform'

const { openLauncher } = useLauncherBridge()
const { platform } = useVisitorPlatform()
const downloadLabel = computed(() => `Download for ${platformLabel(platform.value)}`)
const downloadUrl = computed(() => `/api/download/launcher/${platform.value}`)

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
  position: relative;
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(0, 1fr);
  align-items: center;
  gap: 4rem;
  min-height: calc(100vh - 7rem);
  padding: 3rem 0 5rem;
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

.hero__title,
.hero__subtitle,
.hero__meta {
  text-shadow: 0 2px 14px rgba(0, 0, 0, 0.55);
}


.hero__content {
  position: relative;
  z-index: 1;
  display: flex;
  flex-direction: column;
  gap: 1.5rem;
}

.hero__eyebrow {
  display: inline-block;
  align-self: flex-start;
  font-family: var(--font-body);
  font-weight: 600;
  font-size: 0.85rem;
  text-transform: uppercase;
  letter-spacing: 0.1em;
  color: var(--accent);
  background-color: var(--bg-secondary);
  border: 2px solid var(--border-color);
  border-radius: var(--edge-radius);
  padding: 0.4rem 0.8rem;
}

.hero__title {
  font-family: var(--font-pixel);
  font-weight: 700;
  font-size: clamp(2.4rem, 5vw, 3.6rem);
  line-height: 1.1;
  color: var(--text-primary);
  margin: 0;
}

.hero__subtitle {
  font-family: var(--font-body);
  font-size: clamp(1.15rem, 1.8vw, 1.5rem);
  line-height: 1.4;
  color: var(--text-secondary);
  max-width: 32rem;
  margin: 0;
}

.hero__actions {
  display: flex;
  flex-wrap: wrap;
  gap: 1rem;
  margin-top: 0.5rem;
}

.hero__cta {
  min-width: 200px;
}

.hero__cta--primary {
  font-size: 0.9rem;
  padding: 1rem 1.75rem;
}

.hero__meta {
  list-style: none;
  margin: 0.75rem 0 0;
  padding: 0;
  display: flex;
  flex-direction: column;
  gap: 0.5rem;
  font-family: var(--font-body);
  font-size: 1.1rem;
  color: var(--text-secondary);
}

.hero__meta li {
  display: flex;
  align-items: center;
  gap: 0.6rem;
}

.hero__meta-dot {
  width: 8px;
  height: 8px;
  background-color: var(--accent);
  border-radius: var(--edge-radius);
  flex-shrink: 0;
}

.hero__visual {
  position: relative;
  z-index: 1;
  display: flex;
  justify-content: center;
}

.hero__visual-frame {
  position: relative;
  width: 100%;
  max-width: 640px;
  background-color: var(--bg-secondary);
  border: 2px solid var(--border-color);
  border-radius: var(--edge-radius);
  padding: 1.75rem 0.75rem 0.75rem;
  @include block-shadow(var(--shadow-color));
  transition: transform var(--transition-normal);
}

.hero__visual-frame:hover {
  transform: translate(-3px, -3px);
}

.hero__visual-chrome {
  position: absolute;
  top: 0.6rem;
  left: 0.75rem;
  display: flex;
  gap: 0.35rem;
}

.hero__visual-dot {
  width: 10px;
  height: 10px;
  border: 2px solid var(--border-color);
  border-radius: var(--edge-radius);
  background-color: var(--bg-tertiary);
}

.hero__visual-dot:nth-child(1) { background-color: #ff6b6b; }
.hero__visual-dot:nth-child(2) { background-color: #ffd93d; }
.hero__visual-dot:nth-child(3) { background-color: #6bcf7f; }

.hero__image {
  display: block;
  width: 100%;
  height: auto;
  border-radius: var(--edge-radius);
  object-fit: cover;
}

@media (max-width: 900px) {
  .hero {
    grid-template-columns: 1fr;
    gap: 2.5rem;
    min-height: auto;
    padding: 2rem 0;
  }


  .hero__actions {
    width: 100%;
  }

  .hero__cta {
    flex: 1;
    min-width: 0;
  }
}
</style>