<template>
  <div class="news-page">
    <header class="news-page__header">
      <h1 class="news-page__title">News</h1>
      <p class="news-page__subtitle">Updates, announcements and events from the Pretupid team.</p>
    </header>

    <p v-if="error" class="news-page__empty">News could not be loaded right now.</p>
    <p v-else-if="posts.length === 0" class="news-page__empty">No posts yet.</p>

    <div v-else class="news-page__list">
      <NuxtLink v-for="post in posts" :key="post.id" :to="`/news/${post.id}`" class="news-card">
        <div class="news-card__meta">
          <span class="news-card__category">{{ post.category }}</span>
          <span v-if="post.pinned" class="news-card__pinned">Pinned</span>
          <time v-if="post.publishedAt" :datetime="new Date(post.publishedAt * 1000).toISOString()">
            {{ formatDate(post.publishedAt) }}
          </time>
        </div>
        <h2 class="news-card__title">{{ post.title }}</h2>
        <p v-if="post.summary" class="news-card__summary">{{ post.summary }}</p>
      </NuxtLink>
    </div>
  </div>
</template>

<script setup lang="ts">
interface NewsSummary {
  id: string
  title: string
  summary: string
  category: string
  pinned: boolean
  publishedAt: number | null
}

useHead({ title: 'News · Pretupid' })

const { data, error } = await useFetch<{ posts: NewsSummary[] }>('/api/news')
const posts = computed(() => data.value?.posts ?? [])

function formatDate(seconds: number): string {
  return new Date(seconds * 1000).toLocaleDateString('en-GB', { day: 'numeric', month: 'short', year: 'numeric' })
}
</script>

<style scoped lang="scss">
.news-page {
  display: flex;
  flex-direction: column;
  gap: 2rem;
  max-width: 820px;
  margin: 0 auto;
}

.news-page__title {
  margin: 0;
  font-family: var(--font-pixel);
  font-weight: 700;
  font-size: 2.2rem;
}

.news-page__subtitle,
.news-page__empty {
  color: var(--text-secondary);
}

.news-page__list {
  display: flex;
  flex-direction: column;
  gap: 1rem;
}

.news-card {
  display: flex;
  flex-direction: column;
  gap: 0.5rem;
  padding: 1.25rem 1.5rem;
  color: inherit;
  text-decoration: none;
  background-color: var(--bg-secondary);
  border: 2px solid var(--border-color);
  border-radius: var(--edge-radius);
  transition: transform var(--transition-fast), border-color var(--transition-fast);

  &:hover {
    transform: translate(-2px, -2px);
    border-color: var(--accent);
  }
}

.news-card__meta {
  display: flex;
  gap: 0.75rem;
  align-items: center;
  font-size: 0.85rem;
  color: var(--text-secondary);
}

.news-card__category,
.news-card__pinned {
  padding: 0.1rem 0.5rem;
  text-transform: uppercase;
  border: 1px solid var(--border-color);
  border-radius: var(--edge-radius);
}

.news-card__pinned {
  color: var(--accent);
  border-color: var(--accent);
}

.news-card__title {
  margin: 0;
  font-size: 1.35rem;
}

.news-card__summary {
  margin: 0;
  color: var(--text-secondary);
}
</style>
