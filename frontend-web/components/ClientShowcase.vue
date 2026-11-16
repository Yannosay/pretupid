<template>
  <section class="client" aria-labelledby="client-title">
    <div class="client__inner">
      <div class="client__copy">
        <div class="client__badge">
          <img src="@shared/branding/pretupid_logo_512x.png" alt="" width="28" height="28" />
          <span>Pretupid Client</span>
        </div>
        <h2 id="client-title" class="client__title">The client that stays out of your way</h2>
        <p class="client__lead">
          A free Fabric mod for Minecraft {{ minecraft }}. It keeps the vanilla look and adds a movable HUD, performance
          tweaks, cosmetics other players can see, emotes and friends.
        </p>

        <ul class="client__stats">
          <li v-for="stat in stats" :key="stat.label">
            <strong>{{ stat.value }}</strong>
            <span>{{ stat.label }}</span>
          </li>
        </ul>

        <div class="client__actions">
          <a href="/api/download/client" class="btn client__download" rel="nofollow">
            <SiteIcon name="download" :size="18" />
            Download Pretupid Client
          </a>
          <NuxtLink to="/features#client" class="btn btn--secondary">All modules</NuxtLink>
        </div>
        <p class="client__note">
          Requires Fabric Loader and Fabric API. In Pretupid Launcher it is one click from any Fabric instance.
        </p>
      </div>

      <div class="client__visual">
        <figure class="client__shot client__shot--back">
          <img
            src="/screenshots/client-title.webp"
            alt="The Minecraft title screen with Pretupid account, friends and news panels"
            width="1600"
            height="900"
            loading="lazy"
          />
        </figure>
        <figure class="client__shot client__shot--front">
          <img
            src="/screenshots/client-ingame.webp"
            alt="Pretupid Client in game with FPS, CPS, coordinates and keystrokes on the HUD"
            width="1600"
            height="900"
            loading="lazy"
          />
        </figure>
        <span class="client__hint" aria-hidden="true"><kbd>Right Shift</kbd> opens the menu</span>
      </div>
    </div>

    <ul class="client__features">
      <li v-for="item in highlights" :key="item.title" class="client__feature">
        <span class="client__icon"><SiteIcon :name="item.icon" :size="18" /></span>
        <strong>{{ item.title }}</strong>
        <span>{{ item.text }}</span>
      </li>
    </ul>
  </section>
</template>

<script setup lang="ts">
import type { SiteIconName } from '~/components/SiteIcon.vue'

interface DownloadInfo {
  client: { version: string; minecraft: string } | null
}

const { data: info } = useFetch<DownloadInfo>('/api/download/info', { lazy: true, server: false })
const minecraft = computed(() => info.value?.client?.minecraft || '26.3')

const stats = [
  { value: '45', label: 'modules' },
  { value: '1 click', label: 'from the launcher' },
  { value: 'Free', label: 'and open source' }
]

const highlights: { icon: SiteIconName; title: string; text: string }[] = [
  { icon: 'bolt', title: 'Smoother frames', text: 'Entity culling, particle limits and lower FPS while the game is in the background.' },
  { icon: 'layers', title: 'Your HUD, your layout', text: 'Drag FPS, CPS, keystrokes, armor, potions and coordinates anywhere.' },
  { icon: 'palette', title: 'Cosmetics others see', text: 'Capes, hats, animated wings, back items, pets and emotes for every Pretupid player.' },
  { icon: 'shield', title: 'Server friendly', text: 'Modules a server disallows pause on their own.' }
]
</script>

<style scoped lang="scss">
.client {
  --brand: #8f94f5;
  position: relative;
  display: flex;
  flex-direction: column;
  gap: 2.5rem;
  padding: 3.5rem 2.5rem 2.5rem;
  overflow: hidden;
  color: #eef0ff;
  background:
    radial-gradient(900px 420px at 85% 0%, color-mix(in srgb, var(--brand) 32%, transparent), transparent 70%),
    radial-gradient(700px 380px at 0% 100%, rgba(93, 91, 245, 0.22), transparent 70%),
    linear-gradient(160deg, #15163a, #0d0e22 60%, #0a0b1a);
  border: 2px solid color-mix(in srgb, var(--brand) 40%, transparent);
  border-radius: var(--edge-radius);
}

.client__inner {
  display: grid;
  grid-template-columns: minmax(0, 0.95fr) minmax(0, 1.05fr);
  align-items: center;
  gap: 3rem;
}

.client__copy {
  display: flex;
  flex-direction: column;
  gap: 1.1rem;
}

.client__badge {
  display: inline-flex;
  align-self: flex-start;
  align-items: center;
  gap: 0.55rem;
  padding: 0;
  font-family: var(--font-pixel);
  font-size: 0.95rem;
  color: #fff;
  border-radius: var(--edge-radius);

  img {
    width: 28px;
    height: 28px;
  }
}

.client__title {
  margin: 0;
  font-size: clamp(1.7rem, 3.4vw, 2.5rem);
  line-height: 1.15;
  color: #fff;
}

.client__lead {
  margin: 0;
  max-width: 34rem;
  font-size: 1.08rem;
  line-height: 1.55;
  color: #c3c7ef;
}

.client__stats {
  display: flex;
  flex-wrap: wrap;
  gap: 0.75rem;
  margin: 0.25rem 0;
  padding: 0;
  list-style: none;

  li {
    display: flex;
    flex-direction: column;
    min-width: 7.5rem;
    padding: 0.6rem 0.9rem;
    background-color: rgba(255, 255, 255, 0.05);
    border: 1px solid rgba(255, 255, 255, 0.1);
    border-radius: var(--edge-radius);
  }

  strong {
    font-family: var(--font-pixel);
    font-size: 1.35rem;
    color: var(--brand);
  }

  span {
    font-size: 0.85rem;
    color: #a9aedb;
  }
}

.client__actions {
  display: flex;
  flex-wrap: wrap;
  gap: 0.75rem;
}

.client__download {
  display: inline-flex;
  align-items: center;
  gap: 0.5rem;
  color: #fff;
  background-color: #5d5bf5;
  border-color: #5d5bf5;

  &:hover {
    background-color: #6f6dff;
    border-color: #6f6dff;
  }
}

.client__note {
  margin: 0;
  font-size: 0.85rem;
  color: #9095c4;
}

.client__visual {
  position: relative;
  aspect-ratio: 16 / 11;
}

.client__shot {
  position: absolute;
  margin: 0;
  overflow: hidden;
  border: 2px solid rgba(255, 255, 255, 0.14);
  border-radius: var(--edge-radius);
  box-shadow: 0 30px 70px rgba(0, 0, 0, 0.55);
  transition: transform 0.4s ease;

  img {
    display: block;
    width: 100%;
    height: auto;
  }
}

.client__shot--back {
  top: 0;
  right: 0;
  width: 82%;
  transform: rotate(1.5deg);
}

.client__shot--front {
  bottom: 0;
  left: 0;
  width: 70%;
  border-color: color-mix(in srgb, var(--brand) 60%, transparent);
  transform: rotate(-2deg);
}

.client__visual:hover .client__shot--back {
  transform: rotate(0.5deg) translate(6px, -6px);
}

.client__visual:hover .client__shot--front {
  transform: rotate(-1deg) translate(-6px, 6px);
}

.client__hint {
  position: absolute;
  right: 0.5rem;
  bottom: 0.75rem;
  padding: 0.35rem 0.6rem;
  font-size: 0.8rem;
  color: #d9dcff;
  background-color: rgba(10, 11, 26, 0.85);
  border: 1px solid rgba(255, 255, 255, 0.12);
  border-radius: var(--edge-radius);

  kbd {
    padding: 0 0.3rem;
    font-family: var(--font-mono);
    color: #fff;
    background-color: rgba(143, 148, 245, 0.25);
    border-radius: 3px;
  }
}

.client__features {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 1rem;
  margin: 0;
  padding: 1.5rem 0 0;
  list-style: none;
  border-top: 1px solid rgba(255, 255, 255, 0.08);
}

.client__feature {
  display: flex;
  flex-direction: column;
  gap: 0.35rem;

  strong {
    color: #fff;
  }

  span:last-child {
    font-size: 0.9rem;
    line-height: 1.45;
    color: #a9aedb;
  }
}

.client__icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 34px;
  height: 34px;
  margin-bottom: 0.2rem;
  color: var(--brand);
  background-color: rgba(143, 148, 245, 0.14);
  border-radius: var(--edge-radius);
}

@media (prefers-reduced-motion: reduce) {
  .client__shot {
    transition: none;
  }
}

@media (max-width: 960px) {
  .client {
    padding: 2rem 1.25rem 1.5rem;
  }

  .client__inner {
    grid-template-columns: 1fr;
    gap: 2rem;
  }

  .client__features {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 560px) {
  .client__features {
    grid-template-columns: 1fr;
  }

  .client__hint {
    display: none;
  }
}
</style>
