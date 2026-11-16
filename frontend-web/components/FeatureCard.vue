<template>
  <article class="feature-card spotlight" @pointermove="track">
    <div class="feature-card__icon" aria-hidden="true">
      <SiteIcon :name="icon" :size="22" />
    </div>
    <h3 class="feature-card__title">{{ title }}</h3>
    <p class="feature-card__description">{{ description }}</p>
  </article>
</template>

<script setup lang="ts">
import type { SiteIconName } from '~/components/SiteIcon.vue'

defineProps<{
  icon: SiteIconName
  title: string
  description: string
}>()

function track(event: PointerEvent) {
  const card = event.currentTarget as HTMLElement
  const bounds = card.getBoundingClientRect()
  card.style.setProperty('--spot-x', `${event.clientX - bounds.left}px`)
  card.style.setProperty('--spot-y', `${event.clientY - bounds.top}px`)
}
</script>

<style scoped lang="scss">
@use 'design-system/mixins' as *;

.feature-card {
  position: relative;
  display: flex;
  flex-direction: column;
  gap: 0.9rem;
  padding: 1.5rem;
  background-color: var(--bg-secondary);
  border: 2px solid var(--border-color);
  border-radius: var(--edge-radius);
  transition: transform var(--transition-normal), box-shadow var(--transition-normal), border-color var(--transition-normal);
  overflow: hidden;
}

.feature-card::before {
  content: '';
  position: absolute;
  top: 0;
  left: 0;
  width: 100%;
  height: 3px;
  background: linear-gradient(90deg, var(--accent) 0%, transparent 100%);
  opacity: 0;
  transition: opacity var(--transition-normal);
}

.feature-card:hover {
  transform: translate(-3px, -3px);
  border-color: var(--accent);
  @include block-shadow(var(--shadow-color));
}

.feature-card:hover::before {
  opacity: 1;
}

.feature-card__icon {
  width: 48px;
  height: 48px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--accent);
  background-color: var(--bg-tertiary);
  border: 2px solid var(--border-color);
  border-radius: var(--edge-radius);
}

.feature-card__title {
  margin: 0;
  font-family: var(--font-pixel);
  font-weight: 700;
  font-size: 1.05rem;
  line-height: 1.4;
}

.feature-card__description {
  margin: 0;
  font-family: var(--font-body);
  font-size: 1.15rem;
  line-height: 1.3;
  color: var(--text-secondary);
}
</style>
