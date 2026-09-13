<template>
  <div class="download-card" :class="[featured ? 'download-card--featured' : 'download-card--muted']">
    <h2 class="download-card__title">{{ title }}</h2>
    <p class="download-card__description">{{ description }}</p>
    <a :href="url" class="btn download-card__button">{{ buttonText }}</a>
    <span class="download-card__meta">{{ meta }}</span>
  </div>
</template>

<script setup lang="ts">
defineProps<{
  title: string
  description: string
  url: string
  buttonText: string
  meta: string
  featured?: boolean
}>()
</script>

<style scoped lang="scss">
@use '@/assets/styles/_mixins' as *;

.download-card {
  @include card-base;
  background-color: var(--bg-secondary);
  border: 2px solid var(--border-color);
  border-radius: var(--edge-radius);
  padding: 2rem;
  display: flex;
  flex-direction: column;
  gap: 1rem;
  align-items: flex-start;
  transition: transform var(--transition-normal), box-shadow var(--transition-normal);
}

.download-card--featured {
  border-color: var(--accent);
  background-color: var(--bg-tertiary);
  transform: scale(1.02);
  @include block-shadow(var(--shadow-color));
}

.download-card--muted {
  opacity: 0.65;
  filter: grayscale(0.3);
}

.download-card__title {
  font-family: var(--font-pixel);
  font-size: 1.5rem;
  text-transform: uppercase;
  margin: 0;
}

.download-card__description {
  font-size: 1rem;
  color: var(--text-secondary);
  margin: 0;
}

.download-card__button {
  align-self: flex-start;
}

.download-card__meta {
  font-size: 0.8rem;
  color: var(--text-secondary);
}
</style>