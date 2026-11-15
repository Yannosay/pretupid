<template>
  <div class="features-page">
    <header class="features-page__header">
      <span class="features-page__eyebrow">Capabilities</span>
      <h1 class="features-page__title">Features</h1>
      <p class="features-page__subtitle">
        Everything in Pretupid Launcher, grouped so you can find what matters to you.
      </p>
      <nav class="features-page__jump" aria-label="Feature groups">
        <a v-for="group in featureGroups" :key="group.id" :href="`#${group.id}`" class="features-page__jump-link">
          {{ group.title }}
        </a>
      </nav>
    </header>

    <section v-for="group in featureGroups" :id="group.id" :key="group.id" class="features-page__group">
      <div class="features-page__group-header">
        <h2 class="features-page__group-title">{{ group.title }}</h2>
        <p class="features-page__group-subtitle">{{ group.subtitle }}</p>
      </div>
      <div class="features-page__grid">
        <FeatureCard
          v-for="feature in group.features"
          :key="feature.title"
          :icon="feature.icon"
          :title="feature.title"
          :description="feature.description"
        />
      </div>
    </section>

    <section class="features-page__cta">
      <h2 class="features-page__group-title">Want the details on security?</h2>
      <p class="features-page__group-subtitle">Read exactly how Pretupid protects your account, your files and your PC.</p>
      <div class="features-page__cta-actions">
        <NuxtLink to="/security" class="btn">How we keep you safe</NuxtLink>
        <NuxtLink to="/download" class="btn btn--secondary">Download</NuxtLink>
      </div>
    </section>
  </div>
</template>

<script setup lang="ts">
import { featureGroups } from '~/composables/features'

useSeoMeta({
  title: 'Features — Pretupid Launcher',
  description: 'Fabric, Quilt, Forge and NeoForge support, Modrinth mods with dependencies, one-click updates, crash help and verified downloads.'
})
</script>

<style scoped lang="scss">
.features-page {
  display: flex;
  flex-direction: column;
  gap: 4rem;
}

.features-page__header {
  display: flex;
  flex-direction: column;
  align-items: center;
  text-align: center;
  gap: 0.75rem;
  max-width: 46rem;
  margin: 0 auto;
}

.features-page__eyebrow {
  display: inline-block;
  font-family: var(--font-pixel);
  font-size: 0.85rem;
  text-transform: uppercase;
  letter-spacing: 0.1em;
  color: var(--accent);
  background-color: var(--bg-secondary);
  border: 2px solid var(--border-color);
  border-radius: var(--edge-radius);
  padding: 0.4rem 0.8rem;
}

.features-page__title {
  font-family: var(--font-pixel);
  font-size: clamp(1.8rem, 4vw, 2.6rem);
  text-transform: uppercase;
  margin: 0;
}

.features-page__subtitle {
  font-family: var(--font-body);
  font-size: 1.25rem;
  color: var(--text-secondary);
  margin: 0;
}

.features-page__jump {
  display: flex;
  flex-wrap: wrap;
  justify-content: center;
  gap: 0.5rem;
  margin-top: 0.75rem;
}

.features-page__jump-link {
  padding: 0.4rem 0.8rem;
  font-family: var(--font-body);
  font-size: 1.1rem;
  color: var(--text-secondary);
  text-decoration: none;
  background-color: var(--bg-secondary);
  border: 2px solid var(--border-color);
  border-radius: var(--edge-radius);

  &:hover {
    color: var(--text-primary);
    border-color: var(--accent);
  }
}

.features-page__group {
  display: flex;
  flex-direction: column;
  gap: 1.5rem;
  scroll-margin-top: 6rem;
}

.features-page__group-header {
  display: flex;
  flex-direction: column;
  gap: 0.5rem;
}

.features-page__group-title {
  margin: 0;
  font-family: var(--font-pixel);
  font-size: clamp(1rem, 2vw, 1.3rem);
  text-transform: uppercase;
}

.features-page__group-subtitle {
  margin: 0;
  font-size: 1.2rem;
  color: var(--text-secondary);
}

.features-page__grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(260px, 1fr));
  gap: 1.25rem;
}

.features-page__cta {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 0.75rem;
  padding: 2.5rem 1.5rem;
  text-align: center;
  background-color: var(--bg-secondary);
  border: 2px solid var(--border-color);
  border-radius: var(--edge-radius);
}

.features-page__cta-actions {
  display: flex;
  flex-wrap: wrap;
  justify-content: center;
  gap: 0.75rem;
  margin-top: 0.5rem;
}
</style>
