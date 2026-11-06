<template>
  <section class="hero">
    <div class="hero__glow" aria-hidden="true"></div>

    <div class="hero__content">
      <span class="hero__eyebrow">Built with Tauri, Rust, and Vue 3</span>
      <h1 class="hero__title">Pretupid Launcher</h1>
      <p class="hero__subtitle">
        A fast, safe Minecraft launcher for Windows. Fabric, Quilt, Forge and NeoForge, Modrinth mods that install with
        their dependencies, and downloads that are verified before they run.
      </p>

      <div class="hero__actions">
        <NuxtLink to="/download" class="btn hero__cta hero__cta--primary">
          Download for Windows
        </NuxtLink>
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
        <li><span class="hero__meta-dot" aria-hidden="true"></span>Verified downloads, no telemetry</li>
      </ul>
    </div>

    <div class="hero__visual">
      <div class="hero__visual-frame">
        <img
          src="/assets/showcase-ptd-launcher.webp"
          alt="Pretupid Launcher interface"
          class="hero__image"
          loading="eager"
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
import { useLauncherBridge } from '~/composables/useLauncherBridge'

const { openLauncher } = useLauncherBridge()
</script>

<style scoped lang="scss">
@use 'design-system/mixins' as *;

.hero {
  position: relative;
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(0, 1fr);
  align-items: center;
  gap: 4rem;
  min-height: 72vh;
  padding: 3rem 0;
  overflow: hidden;
}

.hero__glow {
  position: absolute;
  top: 20%;
  right: -10%;
  width: 620px;
  height: 620px;
  background: radial-gradient(closest-side, rgba(74, 158, 255, 0.18), transparent 70%);
  filter: blur(20px);
  pointer-events: none;
  z-index: 0;
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
  font-family: var(--font-pixel);
  font-size: 0.6rem;
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
  font-size: clamp(2.4rem, 5vw, 3.6rem);
  line-height: 1.1;
  text-transform: uppercase;
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
  max-width: 560px;
  background-color: var(--bg-secondary);
  border: 2px solid var(--border-color);
  border-radius: var(--edge-radius);
  padding: 0.75rem;
  @include block-shadow(var(--shadow-color));
  transition: transform var(--transition-normal);
}

.hero__visual-frame:hover {
  transform: translate(-3px, -3px);
}

.hero__visual-chrome {
  position: absolute;
  top: 0.4rem;
  left: 0.6rem;
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

  .hero__glow {
    display: none;
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