<template>
  <div class="modal-overlay" v-backdrop-close="() => emit('close')" @keydown.esc="emit('close')">
    <article class="modal modal--wide news-reader" role="dialog" aria-modal="true" :aria-label="article?.title ?? preview.title">
      <header class="news-reader__hero">
        <span class="news-reader__tag" :class="`news-reader__tag--${category}`">{{ categoryLabel }}</span>
        <h2 class="news-reader__title">{{ article?.title ?? preview.title }}</h2>
        <span v-if="published" class="news-reader__date">{{ published }}</span>
        <button ref="closeButton" class="modal__close news-reader__close" aria-label="Close" @click="emit('close')">×</button>
      </header>

      <div class="news-reader__body" @click="onBodyClick">
        <div v-if="loading" class="pixel-loader">
          Loading
          <span class="pixel-loader__dots"><span class="pixel-loader__dot" /><span class="pixel-loader__dot" /><span class="pixel-loader__dot" /></span>
        </div>
        <template v-else-if="error">
          <p class="news-reader__summary">{{ preview.summary }}</p>
          <p class="form-error">{{ error }}</p>
        </template>
        <div v-else class="news-reader__markdown" v-html="html" />
      </div>

      <footer class="modal__footer">
        <button class="btn btn--ghost" @click="openOnWebsite">
          <AppIcon name="external" :size="14" />
          Open on website
        </button>
        <button class="btn" @click="emit('close')">Done</button>
      </footer>
    </article>
  </div>
</template>

<script setup lang="ts">
import { vBackdropClose } from '@ui/backdropClose'
import { computed, onMounted, ref } from 'vue'
import AppIcon from '@/components/AppIcon.vue'
import { renderMarkdown } from '@ui/markdown'
import { fetchNewsPost, openExternalLink, type NewsArticle, type NewsPost } from '@/api'
import { WEBSITE_URL } from '@/config'
import { useNotificationsStore } from '@/stores/notifications'

const CATEGORY_LABELS: Record<string, string> = {
  news: 'News',
  update: 'Update',
  announcement: 'Announcement',
  event: 'Event',
  maintenance: 'Maintenance'
}

const props = defineProps<{ preview: NewsPost }>()
const emit = defineEmits<{ (e: 'close'): void }>()

const notifications = useNotificationsStore()
const article = ref<NewsArticle | null>(null)
const loading = ref(true)
const error = ref<string | null>(null)
const closeButton = ref<HTMLButtonElement | null>(null)

const category = computed(() => article.value?.category ?? props.preview.category)
const categoryLabel = computed(() => CATEGORY_LABELS[category.value] ?? 'News')
const html = computed(() => (article.value ? renderMarkdown(article.value.body || article.value.summary) : ''))
const published = computed(() => {
  const seconds = article.value?.publishedAt ?? props.preview.publishedAt
  if (!seconds) return ''
  return new Date(seconds * 1000).toLocaleDateString(undefined, { year: 'numeric', month: 'long', day: 'numeric' })
})

function openLink(url: string) {
  openExternalLink(url).catch((e) => notifications.notifyError('Could not open the link', String(e)))
}

function openOnWebsite() {
  openLink(`${WEBSITE_URL}/news/${encodeURIComponent(props.preview.id)}`)
}

function onBodyClick(event: MouseEvent) {
  const anchor = (event.target as HTMLElement | null)?.closest('a')
  if (!anchor) return
  event.preventDefault()
  if (anchor.href) openLink(anchor.href)
}

onMounted(async () => {
  closeButton.value?.focus()
  try {
    article.value = await fetchNewsPost(props.preview.id)
  } catch (e) {
    error.value = String(e)
  } finally {
    loading.value = false
  }
})
</script>

<style scoped lang="scss">
.news-reader {
  max-width: 720px;
}

.news-reader__hero {
  position: relative;
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 0.55rem;
  padding: 1.5rem 3.5rem 1.25rem 1.5rem;
  border-bottom: 1px solid var(--border-color);
  background:
    linear-gradient(135deg, color-mix(in srgb, var(--accent) 22%, transparent), transparent 70%),
    var(--bg-elevated);
}

.news-reader__close {
  position: absolute;
  top: 0.75rem;
  right: 0.75rem;
}

.news-reader__tag {
  padding: 0.1rem 0.45rem;
  font-size: 0.7rem;
  font-weight: 700;
  letter-spacing: 0.06em;
  text-transform: uppercase;
  color: var(--accent);
  background-color: color-mix(in srgb, var(--accent) 14%, transparent);
  border-radius: var(--edge-radius);
}

.news-reader__tag--update {
  color: var(--success);
  background-color: color-mix(in srgb, var(--success) 14%, transparent);
}

.news-reader__tag--event {
  color: #c084fc;
  background-color: rgba(192, 132, 252, 0.14);
}

.news-reader__tag--maintenance {
  color: var(--warning);
  background-color: color-mix(in srgb, var(--warning) 14%, transparent);
}

.news-reader__title {
  margin: 0;
  font-family: var(--font-pixel);
  font-size: 1.35rem;
  line-height: 1.3;
}

.news-reader__date {
  font-size: 0.82rem;
  color: var(--text-secondary);
}

.news-reader__body {
  flex: 1;
  min-height: 160px;
  overflow-y: auto;
  padding: 1.25rem 1.5rem 1.5rem;
  user-select: text;
}

.news-reader__summary {
  margin: 0 0 1rem;
  color: var(--text-secondary);
}

.news-reader__markdown {
  font-size: 1rem;
  line-height: 1.65;
  overflow-wrap: anywhere;
  color: var(--text-primary);

  :deep(h2),
  :deep(h3),
  :deep(h4) {
    margin: 1.4rem 0 0.5rem;
    font-family: var(--font-pixel);
    line-height: 1.3;
  }

  :deep(h2) {
    font-size: 1.15rem;
  }

  :deep(h3) {
    font-size: 1rem;
  }

  :deep(h4) {
    font-size: 0.92rem;
  }

  :deep(> :first-child) {
    margin-top: 0;
  }

  :deep(p),
  :deep(ul),
  :deep(ol),
  :deep(blockquote),
  :deep(pre) {
    margin: 0 0 1rem;
  }

  :deep(ul),
  :deep(ol) {
    padding-left: 1.4rem;
  }

  :deep(ul) {
    list-style: square;
  }

  :deep(ol) {
    list-style: decimal;
  }

  :deep(li::marker) {
    color: var(--accent);
  }

  :deep(li) {
    margin-bottom: 0.3rem;
  }

  :deep(a) {
    color: var(--accent);
    cursor: pointer;
  }

  :deep(blockquote) {
    padding: 0.6rem 1rem;
    color: var(--text-secondary);
    background-color: var(--bg-tertiary);
    border-left: 3px solid var(--accent);
  }

  :deep(code) {
    padding: 0.1rem 0.3rem;
    font-family: var(--font-mono);
    font-size: 0.88em;
    background-color: var(--well-bg);
  }

  :deep(pre) {
    padding: 0.85rem 1rem;
    overflow-x: auto;
    background-color: var(--well-bg);
    border: 1px solid var(--border-color);

    code {
      padding: 0;
      background: none;
    }
  }

  :deep(hr) {
    margin: 1.5rem 0;
    border: none;
    border-top: 1px solid var(--border-color);
  }

  :deep(.markdown__figure) {
    margin: 1.25rem 0;
  }

  :deep(.markdown__figure img) {
    display: block;
    max-width: 100%;
    height: auto;
    border: 1px solid var(--border-color);
  }

  :deep(.markdown__actions) {
    margin: 1.25rem 0;
  }

  :deep(.markdown__button) {
    display: inline-flex;
    align-items: center;
    padding: 0.6rem 1.2rem;
    font-family: var(--font-pixel);
    font-size: 0.85rem;
    color: var(--accent-text);
    text-decoration: none;
    background-color: var(--accent);
    border-radius: var(--edge-radius);

    &:hover {
      filter: brightness(1.1);
    }
  }
}
</style>
