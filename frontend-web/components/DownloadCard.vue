<template>
  <div
    class="download-card"
    :class="{ 'download-card--featured': featured }"
  >
    <div class="download-card__top">
      <span class="download-card__platform">{{ platformLabel }}</span>
      <span v-if="featured" class="download-card__badge">Your system</span>
    </div>

    <h2 class="download-card__title">{{ title }}</h2>
    <p class="download-card__description">{{ description }}</p>

    <a
      :href="url"
      class="btn download-card__button"
      :class="{ 'btn--secondary': !featured }"
      rel="nofollow"
    >
      {{ buttonText }}
    </a>

    <div v-if="alternatives?.length" class="download-card__alternatives">
      <a v-for="link in alternatives" :key="link.url" :href="link.url" class="download-card__alt" rel="nofollow">{{ link.label }}</a>
    </div>

    <span class="download-card__meta">{{ meta }}</span>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'

const props = defineProps<{
  title: string
  description: string
  url: string
  buttonText: string
  meta: string
  featured?: boolean
  alternatives?: { label: string; url: string }[]
}>()

const platformLabel = computed(() => {
  const lower = props.title.toLowerCase()
  if (lower.includes('windows')) return 'Windows'
  if (lower.includes('linux')) return 'Linux'
  if (lower.includes('macos') || lower.includes('mac')) return 'macOS'
  return props.title
})
</script>

<style scoped lang="scss">
@use 'design-system/mixins' as *;

.download-card {
  display: flex;
  flex-direction: column;
  gap: 1rem;
  padding: 2rem;
  background-color: var(--bg-secondary);
  border: 2px solid var(--border-color);
  border-radius: var(--edge-radius);
  transition: transform var(--transition-normal), box-shadow var(--transition-normal), border-color var(--transition-normal);
}

.download-card:hover {
  transform: translate(-3px, -3px);
  @include block-shadow(var(--shadow-color));
}

.download-card--featured {
  border-color: var(--accent);
  background-color: var(--bg-tertiary);
}

.download-card--featured:hover {
  border-color: var(--accent);
}


.download-card__top {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 0.75rem;
}

.download-card__platform {
  font-family: var(--font-body);
  font-weight: 600;
  font-size: 0.85rem;
  text-transform: uppercase;
  letter-spacing: 0.05em;
  color: var(--accent);
}

.download-card__badge {
  font-family: var(--font-body);
  font-weight: 600;
  letter-spacing: 0.08em;
  font-size: 0.55rem;
  text-transform: uppercase;
  color: var(--accent-text);
  background-color: var(--accent);
  padding: 0.2rem 0.5rem;
  border-radius: var(--edge-radius);
}

.download-card__title {
  font-family: var(--font-pixel);
  font-weight: 700;
  font-size: 1.25rem;
  margin: 0;
}

.download-card__description {
  font-size: 1rem;
  color: var(--text-secondary);
  margin: 0;
  flex: 1;
}

.download-card__button {
  align-self: flex-start;
}

.download-card__alternatives {
  display: flex;
  flex-wrap: wrap;
  gap: 0.35rem 1rem;
}

.download-card__alt {
  font-size: 0.88rem;
  color: var(--text-secondary);
  text-decoration: underline;
  text-underline-offset: 3px;

  &:hover {
    color: var(--accent);
  }
}

.download-card__meta {
  font-size: 0.85rem;
  color: var(--text-secondary);
}
</style>