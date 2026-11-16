<template>
  <div class="about-page">
    <header class="about-page__header">
      <h1 class="about-page__title">About Pretupid Launcher</h1>
      <p class="about-page__subtitle">
        A launcher built by a Minecraft player who got tired of waiting.
      </p>
    </header>

    <section class="about-page__card">
      <h2 class="about-page__card-title">My idea</h2>
      <p>
        Pretupid Launcher exists because modern Minecraft launchers are slow, bloated, full of ads and filled with
        in-game purchases. So let's just have a fast, reliable launcher that respects your time and your machine, and
        stays out of the way while you play.
      </p>
    </section>

    <section class="about-page__card">
      <h2 class="about-page__card-title">Technology</h2>
      <ul class="about-page__list">
        <li><span class="about-page__bullet"></span><strong>Tauri 2</strong> for a lightweight desktop shell that uses the OS WebView.</li>
        <li><span class="about-page__bullet"></span><strong>Rust</strong> for the launch pipeline, download orchestration, and version resolution.</li>
        <li><span class="about-page__bullet"></span><strong>Vue 3 and Pinia</strong> for the reactive interface and state management.</li>
        <li><span class="about-page__bullet"></span><strong>SQLite</strong> via sqlx for local persistence with zero external dependencies.</li>
        <li><span class="about-page__bullet"></span><strong>Modrinth API</strong> for mod, modpack, and metadata integration.</li>
      </ul>
    </section>

    <section class="about-page__card">
      <h2 class="about-page__card-title">License</h2>
      <p>
        Pretupid Launcher is licensed under the GNU Affero General Public License version 3.0.
        You may use, modify, and redistribute it under the terms of that license. Contributions
        are welcome.
      </p>
    </section>

    <section class="about-page__cta">
      <p class="about-page__cta-text">Curious what it looks like in action?</p>
      <div class="about-page__cta-actions">
        <NuxtLink to="/download" class="btn">Download</NuxtLink>
        <button type="button" class="btn btn--secondary" @click="openLauncher">
          Open Launcher
        </button>
      </div>
    </section>
  </div>
</template>

<script setup lang="ts">
import { useLauncherBridge } from '~/composables/useLauncherBridge'

const { openLauncher } = useLauncherBridge()
</script>

<style scoped lang="scss">
@use 'design-system/mixins' as *;

.about-page {
  display: flex;
  flex-direction: column;
  gap: 2rem;
  max-width: 780px;
  margin: 0 auto;
}

.about-page__header {
  display: flex;
  flex-direction: column;
  align-items: center;
  text-align: center;
  gap: 0.75rem;
}


.about-page__title {
  font-family: var(--font-pixel);
  font-weight: 700;
  font-size: clamp(1.8rem, 4vw, 2.4rem);
  margin: 0;
}

.about-page__subtitle {
  font-family: var(--font-body);
  font-size: 1.2rem;
  color: var(--text-secondary);
  margin: 0;
}

.about-page__card {
  padding: 1.75rem;
  background-color: var(--bg-secondary);
  border: 2px solid var(--border-color);
  border-radius: var(--edge-radius);
}

.about-page__card-title {
  font-family: var(--font-pixel);
  font-weight: 700;
  font-size: 1.1rem;
  margin: 0 0 1rem;
}

.about-page__card p {
  font-family: var(--font-body);
  font-size: 1.15rem;
  line-height: 1.5;
  color: var(--text-secondary);
  margin: 0 0 1rem;
}

.about-page__card p:last-child {
  margin-bottom: 0;
}

.about-page__list {
  list-style: none;
  margin: 0;
  padding: 0;
  display: flex;
  flex-direction: column;
  gap: 0.75rem;
}

.about-page__list li {
  display: flex;
  align-items: flex-start;
  gap: 0.75rem;
  font-family: var(--font-body);
  font-size: 1.15rem;
  color: var(--text-secondary);
  line-height: 1.5;
}

.about-page__bullet {
  flex-shrink: 0;
  width: 10px;
  height: 10px;
  margin-top: 0.6rem;
  background-color: var(--accent);
  border-radius: var(--edge-radius);
}

.about-page__list strong {
  color: var(--text-primary);
}

.about-page__cta {
  padding: 2rem;
  background-color: var(--bg-secondary);
  border: 2px dashed var(--border-color);
  border-radius: var(--edge-radius);
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 1.5rem;
  flex-wrap: wrap;
}

.about-page__cta-text {
  font-family: var(--font-body);
  font-size: 1.2rem;
  margin: 0;
}

.about-page__cta-actions {
  display: flex;
  gap: 0.75rem;
  flex-wrap: wrap;
}
</style>