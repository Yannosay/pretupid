<template>
  <div class="about-page">
    <h1 class="about-page__title">About Pretupid Launcher</h1>
    <section class="about-page__section">
      <h2>Our Mission</h2>
      <p>
        Pretupid Launcher is a modern Minecraft launcher focused on performance, customization, and a clean user experience.
        It is built with Tauri, Rust, Vue 3, and SQLite to deliver a lightweight and secure desktop application.
      </p>
      <p>
        Created by Yannosay Productions, Pretupid Launcher is licensed under the GNU 2.0 license.
      </p>
    </section>
    <section class="about-page__section">
      <h2>Technology Stack</h2>
      <ul>
        <li>Tauri 2 — Lightweight desktop shell</li>
        <li>Rust — Backend performance and safety</li>
        <li>Vue 3 — Reactive frontend</li>
        <li>SQLite — Local data storage</li>
        <li>Modrinth API — Mod and modpack integration</li>
      </ul>
    </section>
    <section class="about-page__section">
      <h2>License</h2>
      <p>
        Pretupid Launcher is open source under the GNU General Public License version 2.0.
        You are free to use, modify, and distribute it under the terms of the license.
      </p>
    </section>
  </div>
</template>

<style scoped lang="scss">
@use '@/assets/styles/_mixins' as *;

.about-page__title {
  font-family: var(--font-pixel);
  font-size: 2.5rem;
  text-align: center;
  margin-bottom: 2rem;
  text-transform: uppercase;
}

.about-page__section {
  background-color: var(--bg-secondary);
  border: 2px solid var(--border-color);
  border-radius: var(--edge-radius);
  padding: 1.5rem;
  margin-bottom: 1.5rem;
}

.about-page__section h2 {
  font-family: var(--font-pixel);
  font-size: 1.3rem;
  text-transform: uppercase;
  margin-bottom: 1rem;
}

.about-page__section p,
.about-page__section li {
  font-size: 1.1rem;
  color: var(--text-secondary);
  line-height: 1.5;
}

.about-page__section ul {
  list-style: square;
  padding-left: 1.5rem;
}
</style>