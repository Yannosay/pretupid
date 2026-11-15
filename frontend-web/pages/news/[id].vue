<template>
  <article class="news-post">
    <NuxtLink to="/news" class="news-post__back">← All news</NuxtLink>
    <template v-if="post">
      <div class="news-post__meta">
        <span class="news-post__category">{{ post.category }}</span>
        <time v-if="post.publishedAt" :datetime="new Date(post.publishedAt * 1000).toISOString()">
          {{ new Date(post.publishedAt * 1000).toLocaleDateString('en-GB', { day: 'numeric', month: 'long', year: 'numeric' }) }}
        </time>
      </div>
      <h1 class="news-post__title">{{ post.title }}</h1>
      <div class="news-post__body markdown" v-html="html" />
    </template>
    <p v-else class="news-post__missing">This post does not exist or is no longer published.</p>
  </article>
</template>

<script setup lang="ts">
interface NewsPost {
  id: string
  title: string
  summary: string
  category: string
  body: string
  publishedAt: number | null
}

const route = useRoute()
const { data: post } = await useFetch<NewsPost>(`/api/news/${encodeURIComponent(String(route.params.id))}`)
const html = computed(() => (post.value ? renderMarkdown(post.value.body) : ''))

useHead(() => ({
  title: post.value ? `${post.value.title} · Pretupid News` : 'News · Pretupid',
  meta: post.value?.summary ? [{ name: 'description', content: post.value.summary }] : []
}))
</script>

<style scoped lang="scss">
.news-post {
  display: flex;
  flex-direction: column;
  gap: 1rem;
  max-width: 760px;
  margin: 0 auto;
}

.news-post__back {
  color: var(--text-secondary);
  text-decoration: none;

  &:hover {
    color: var(--accent);
  }
}

.news-post__meta {
  display: flex;
  gap: 0.75rem;
  color: var(--text-secondary);
}

.news-post__category {
  text-transform: uppercase;
  color: var(--accent);
}

.news-post__title {
  margin: 0;
  font-family: var(--font-pixel);
  font-weight: 700;
  font-size: 2rem;
  line-height: 1.3;
}

.news-post__missing {
  color: var(--text-secondary);
}
</style>
