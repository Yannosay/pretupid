<template>
  <div class="feature-card">
    <div class="feature-card__icon">{{ icon }}</div>
    <h3 class="feature-card__title">{{ title }}</h3>
    <p class="feature-card__description">{{ description }}</p>
  </div>
</template>

<script setup lang="ts">
defineProps<{
  icon: string
  title: string
  description: string
}>()
</script>

<style scoped lang="scss">
@use '@/assets/styles/_mixins' as *;

.feature-card {
  @include card-base;
  background-color: var(--bg-secondary);
  border: 2px solid var(--border-color);
  border-radius: var(--edge-radius);
  padding: 1.5rem;
  display: flex;
  flex-direction: column;
  gap: 0.75rem;
  transition: transform var(--transition-normal), box-shadow var(--transition-normal);
}

.feature-card:hover {
  transform: translate(-2px, -2px);
  @include block-shadow(var(--shadow-color));
}

.feature-card__icon {
  font-size: 2.5rem;
  line-height: 1;
}

.feature-card__title {
  font-family: var(--font-pixel);
  font-size: 1rem;
  text-transform: uppercase;
  margin: 0;
}

.feature-card__description {
  font-size: 1rem;
  color: var(--text-secondary);
  margin: 0;
}
</style>