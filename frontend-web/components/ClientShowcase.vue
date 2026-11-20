<template>
  <section class="client" aria-labelledby="client-title">
    <div class="client__sky" aria-hidden="true">
      <ClientOnly>
        <PixelScenery seed="pretupid-client-night" />
      </ClientOnly>
    </div>

    <div class="client__inner">
      <header class="client__head">
        <img src="@shared/branding/pretupid_logo_512x.png" alt="" width="64" height="64" class="client__mark" />
        <h2 id="client-title" class="client__title">Pretupid Client</h2>
        <p class="client__lead">
          The Minecraft you know, with the things you always wished it had. Free, no ads, and one click away in Pretupid Launcher.
        </p>
      </header>

      <div class="client__stage">
        <div class="client__window">
          <Transition name="client-shot" mode="out-in">
            <img
              :key="active.id"
              :src="active.image"
              :alt="active.alt"
              width="1600"
              height="900"
              class="client__shot"
              loading="lazy"
            />
          </Transition>
        </div>

        <div class="client__tooltip" role="status" aria-live="polite">
          <strong class="client__tooltip-title">{{ active.title }}</strong>
          <span class="client__tooltip-text">{{ active.text }}</span>
          <span class="client__tooltip-key">{{ active.key }}</span>
        </div>

        <div class="client__hotbar" role="tablist" aria-label="Pretupid Client features">
          <button
            v-for="(slide, index) in slides"
            :key="slide.id"
            type="button"
            role="tab"
            class="client__slot"
            :class="{ 'client__slot--active': index === current }"
            :aria-selected="index === current"
            :aria-label="slide.title"
            @click="select(index)"
          >
            <SiteIcon :name="slide.icon" :size="20" />
            <span class="client__slot-number">{{ index + 1 }}</span>
          </button>
        </div>
      </div>

      <div class="client__versions" aria-label="Supported Minecraft versions">
        <span class="client__versions-label">Runs on Minecraft</span>
        <span v-for="version in versions" :key="version" class="client__version">{{ version }}</span>
      </div>

      <div class="client__actions">
        <a href="/api/download/client" class="client__button" rel="nofollow">
          <span>Download Pretupid Client</span>
        </a>
        <NuxtLink to="/features#client" class="client__link">Every module and feature</NuxtLink>
      </div>
      <p class="client__note">
        Needs Fabric Loader and Fabric API. In Pretupid Launcher, turn on Install Pretupid Client in any Fabric instance and it picks the right version for you.
      </p>
    </div>
  </section>
</template>

<script setup lang="ts">
import PixelScenery from '@ui/PixelScenery.vue'
import type { SiteIconName } from '~/components/SiteIcon.vue'

interface DownloadInfo {
  client: { version: string; minecraft: string; minecraftVersions?: string[] } | null
}

interface Slide {
  id: string
  icon: SiteIconName
  title: string
  text: string
  key: string
  image: string
  alt: string
}

const slides: Slide[] = [
  {
    id: 'cosmetics',
    icon: 'palette',
    title: 'Cosmetics everyone sees',
    text: 'Capes that move with you, wings, crowns, pets and a nametag you style yourself. Every Pretupid player sees them.',
    key: 'Right Shift · Cosmetics',
    image: '/screenshots/client-ingame.webp',
    alt: 'A player with a crown, wings, a cape, a pet and a rainbow nametag, with FPS, CPS, keystrokes and armor on the HUD'
  },
  {
    id: 'emoji',
    icon: 'users',
    title: 'Emoji in chat',
    text: 'Type :sob: or pick one from the button. Pretupid players see real emoji; everyone else just sees your words.',
    key: 'Chat · :name:',
    image: '/screenshots/client-emoji.webp',
    alt: 'Minecraft chat with fire, crying and laughing emoji and the emoji picker open'
  },
  {
    id: 'screenies',
    icon: 'sparkle',
    title: 'Screenies and memes',
    text: 'Grab a screenshot without the HUD at up to three times your screen size, add filters and meme text, then save it or send it to a friend.',
    key: 'F4',
    image: '/screenshots/client-screenie.webp',
    alt: 'Screenie Studio with a top and bottom meme caption over a Minecraft landscape'
  },
  {
    id: 'cinematic',
    icon: 'eye-off',
    title: 'Freecam and Cinematic Studio',
    text: 'Fly the camera freely, drop keyframes along a path and play smooth camera shots with cinema bars.',
    key: 'F6 · K · J',
    image: '/screenshots/client-cinematic.webp',
    alt: 'The Cinematic Studio timeline with keyframes and the camera path drawn in the world'
  },
  {
    id: 'waypoints',
    icon: 'folder',
    title: 'Waypoints',
    text: 'Coloured markers in the world with their distance, death points that clear themselves and Nether coordinates worked out for you.',
    key: 'N',
    image: '/screenshots/client-waypoints.webp',
    alt: 'A waypoint marker named Home floating over the landscape with its distance'
  },
  {
    id: 'menu',
    icon: 'layers',
    title: 'Everything in one menu',
    text: 'Modules, cosmetics, friends, skins, profiles and news, in a menu that looks like it belongs in Minecraft.',
    key: 'Right Shift',
    image: '/screenshots/client-menu.webp',
    alt: 'The Pretupid menu showing nametag presets and customisation'
  }
]

const current = ref(0)
const active = computed(() => slides[current.value])
let timer: ReturnType<typeof setInterval> | null = null
let paused = false

function select(index: number) {
  current.value = index
  paused = true
}

onMounted(() => {
  timer = setInterval(() => {
    if (!paused && !document.hidden) {
      current.value = (current.value + 1) % slides.length
    }
  }, 6000)
})

onBeforeUnmount(() => {
  if (timer) clearInterval(timer)
})

const { data: info } = useFetch<DownloadInfo>('/api/download/info', { lazy: true, server: false })
const versions = computed(() => {
  const list = info.value?.client?.minecraftVersions
  const shown = list && list.length > 0 ? [...list] : ['1.21.2', '1.21.3', '1.21.4', '1.21.5', '1.21.6', '1.21.7', '1.21.8', '1.21.9', '1.21.10', '1.21.11', '26.1', '26.1.1', '26.1.2', '26.2', '26.3']
  return shown.sort(compareVersions)
})

function compareVersions(a: string, b: string): number {
  const left = a.split('.').map(Number)
  const right = b.split('.').map(Number)
  for (let i = 0; i < Math.max(left.length, right.length); i++) {
    const difference = (left[i] ?? 0) - (right[i] ?? 0)
    if (difference !== 0) return difference
  }
  return 0
}
</script>

<style scoped lang="scss">
.client {
  position: relative;
  overflow: hidden;
  padding: 4.5rem 1.5rem 3.5rem;
  color: #f2f2f2;
  background-color: #070b14;
  border: 2px solid #000;
  box-shadow: inset 0 0 0 2px rgba(255, 255, 255, 0.06);
}

.client__sky {
  position: absolute;
  inset: 0 0 auto;
  height: 70%;
  opacity: 0.9;
  pointer-events: none;

  &::after {
    content: '';
    position: absolute;
    inset: auto 0 0;
    height: 45%;
    background: linear-gradient(to bottom, transparent, #070b14);
  }
}

.client__inner {
  position: relative;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 1.75rem;
  max-width: 1080px;
  margin: 0 auto;
}

.client__head {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 0.6rem;
  text-align: center;
}

.client__mark {
  width: 56px;
  height: 56px;
  image-rendering: auto;
  filter: drop-shadow(3px 3px 0 rgba(0, 0, 0, 0.6));
}

.client__title {
  margin: 0;
  font-family: var(--font-pixel);
  font-size: clamp(2rem, 5vw, 3.4rem);
  line-height: 1;
  color: #fff;
  text-shadow: 4px 4px 0 #3f3f3f;
}

.client__lead {
  max-width: 38rem;
  margin: 0;
  font-size: 1.15rem;
  line-height: 1.5;
  color: #d6dbe6;
  text-shadow: 2px 2px 0 rgba(0, 0, 0, 0.5);
}

.client__stage {
  position: relative;
  display: flex;
  flex-direction: column;
  align-items: center;
  width: 100%;
}

.client__window {
  position: relative;
  width: 100%;
  aspect-ratio: 16 / 9;
  overflow: hidden;
  background-color: #000;
  border: 4px solid #000;
  box-shadow:
    inset 0 0 0 2px #555,
    0 0 0 4px #c6c6c6,
    0 0 0 6px #fff,
    0 0 0 8px #555,
    0 18px 0 8px rgba(0, 0, 0, 0.35);
}

.client__shot {
  display: block;
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.client__tooltip {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 0.2rem;
  width: min(40rem, 100%);
  min-height: 6.4rem;
  margin-top: 2.25rem;
  padding: 0.6rem 1rem 0.65rem;
  text-align: center;
  background-color: rgba(16, 0, 16, 0.94);
  border: 2px solid #28007f;
  outline: 2px solid rgba(16, 0, 16, 0.94);
  box-shadow: inset 0 0 0 1px #5000ff;
  font-family: var(--font-pixel);
}

.client__tooltip-title {
  font-size: 1rem;
  color: #fff;
}

.client__tooltip-text {
  font-family: var(--font-body);
  font-size: 0.95rem;
  line-height: 1.4;
  color: #c7c7d7;
}

.client__tooltip-key {
  font-size: 0.8rem;
  color: #5555ff;
}

.client__hotbar {
  display: flex;
  gap: 0;
  margin-top: 1.5rem;
  padding: 3px;
  background-color: rgba(0, 0, 0, 0.55);
  border: 3px solid #000;
}

.client__slot {
  position: relative;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 54px;
  height: 54px;
  color: #e8e8e8;
  background-color: #8b8b8b;
  border: 3px solid;
  border-color: #373737 #fff #fff #373737;
  cursor: pointer;

  &:hover {
    color: #fff;
    background-color: #a0a0a0;
  }

  &:focus-visible {
    outline: 3px solid #ffff55;
    outline-offset: 0;
    z-index: 1;
  }
}

.client__slot--active {
  color: #fff;
  background-color: #6f6f6f;
  box-shadow: 0 0 0 3px #fff, 0 0 0 6px #000;
  z-index: 1;
}

.client__slot-number {
  position: absolute;
  right: 3px;
  bottom: 1px;
  font-family: var(--font-pixel);
  font-size: 0.75rem;
  color: #fff;
  text-shadow: 1px 1px 0 #3f3f3f;
}

.client__versions {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: center;
  gap: 0.4rem;
}

.client__versions-label {
  margin-right: 0.3rem;
  font-family: var(--font-pixel);
  font-size: 0.85rem;
  color: #a8b0c2;
}

.client__version {
  padding: 0.2rem 0.45rem;
  font-family: var(--font-pixel);
  font-size: 0.8rem;
  color: #55ff55;
  background-color: rgba(0, 0, 0, 0.5);
  border: 1px solid #1f3f1f;
}

.client__actions {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: center;
  gap: 1.25rem;
}

.client__button {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-width: 18rem;
  padding: 0.85rem 1.5rem;
  font-family: var(--font-pixel);
  font-size: 1.05rem;
  color: #fff;
  text-decoration: none;
  text-shadow: 2px 2px 0 #3f3f3f;
  background-color: #6d6d6d;
  border: 2px solid #000;
  box-shadow: inset 2px 2px 0 #a9a9a9, inset -2px -3px 0 #4a4a4a;

  &:hover {
    color: #ffffa0;
    background-color: #7a86c6;
    box-shadow: inset 2px 2px 0 #b9c3f0, inset -2px -3px 0 #4f5a92;
  }

  &:focus-visible {
    outline: 3px solid #fff;
    outline-offset: 2px;
  }
}

.client__link {
  font-family: var(--font-pixel);
  font-size: 0.9rem;
  color: #d6dbe6;
  text-underline-offset: 4px;

  &:hover {
    color: #ffff55;
  }
}

.client__note {
  max-width: 40rem;
  margin: 0;
  font-size: 0.9rem;
  line-height: 1.5;
  text-align: center;
  color: #8f98ab;
}

.client-shot-enter-active,
.client-shot-leave-active {
  transition: opacity 0.25s steps(4, end);
}

.client-shot-enter-from,
.client-shot-leave-to {
  opacity: 0;
}

@media (prefers-reduced-motion: reduce) {
  .client-shot-enter-active,
  .client-shot-leave-active {
    transition: none;
  }
}

@media (max-width: 720px) {
  .client {
    padding: 3rem 1rem 2.5rem;
  }

  .client__tooltip {
    min-height: 8rem;
    margin-top: 1.5rem;
  }

  .client__hotbar {
    margin-top: 1rem;
  }

  .client__slot {
    width: 42px;
    height: 42px;
  }

  .client__button {
    min-width: 0;
    width: 100%;
  }
}
</style>
