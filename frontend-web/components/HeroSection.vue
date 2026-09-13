<template>
  <section class="hero">
    <div class="hero__content">
      <h1 class="hero__title">Pretupid Launcher</h1>
      <p class="hero__subtitle">The fastest way to play Minecraft. Built with Rust and Tauri.</p>
      <div class="hero__actions">
        <NuxtLink to="/download" class="btn hero__download">Download for Windows</NuxtLink>
        <NuxtLink to="/#features" class="btn btn--secondary hero__learn">Learn More</NuxtLink>
      </div>
    </div>
    <div class="hero__visual">
      <img src="/assets/showcase-ptd-launcher.webp" alt="Pretupid Launcher Showcase" class="hero__image" />
    </div>
  </section>
</template>

<style scoped lang="scss">
@use '@/assets/styles/_mixins' as *;

.hero {
  display: flex;
  align-items: center;
  gap: 3rem;
  min-height: 70vh;
  flex-wrap: wrap;
}

.hero__content {
  flex: 1 1 400px;
}

.hero__title {
  font-family: var(--font-pixel);
  font-size: 3rem;
  margin-bottom: 1rem;
  color: var(--accent);
  text-transform: uppercase;
}

.hero__subtitle {
  font-family: var(--font-body);
  font-size: 1.8rem;
  color: var(--text-secondary);
  margin-bottom: 2rem;
  line-height: 1.2;
}

.hero__actions {
  display: flex;
  gap: 1rem;
  flex-wrap: wrap;
}

.hero__visual {
  flex: 1 1 400px;
  display: flex;
  justify-content: center;
}

.hero__image {
  width: 100%;
  max-width: 500px;
  border-radius: var(--edge-radius);
  border: 2px solid var(--border-color);
  @include block-shadow(var(--shadow-color));
  object-fit: cover;
}
</style>