<template>
  <Transition name="whats-new">
    <div v-if="visible" class="whats-new-overlay" v-backdrop-close="dismiss">
      <div class="whats-new">
        <header class="whats-new__header">
          <div>
            <span class="whats-new__eyebrow">Update installed</span>
            <h2 class="whats-new__title">What's new in {{ displayVersion }}</h2>
          </div>
          <button class="whats-new__close" aria-label="Close" @click="dismiss">×</button>
        </header>

        <div class="whats-new__body">
          <p v-if="formattedDate" class="whats-new__meta">Released {{ formattedDate }}</p>

          <div v-if="notesHtml" class="whats-new__markdown" @click="onNotesClick" v-html="notesHtml" />

          <p v-else class="whats-new__empty">
            This release contains performance and stability improvements. See the full release notes
            on GitHub for details.
          </p>

          <a :href="release.htmlUrl" class="whats-new__link" @click.prevent="openLink(release.htmlUrl)">
            View full release on GitHub
          </a>
        </div>

        <footer class="whats-new__footer">
          <button class="btn" @click="dismiss">Got it</button>
        </footer>
      </div>
    </div>
  </Transition>
</template>

<script setup lang="ts">
import { vBackdropClose } from '@ui/backdropClose'
import { renderMarkdown } from '@ui/markdown'
import { computed, onMounted, ref } from 'vue'
import { openExternalLink } from '@/api'
import { useUpdaterStore, type PendingReleaseNotes } from '@/stores/updater'
import { REPOSITORY_URL } from '@/config'

const updater = useUpdaterStore()
const visible = ref(false)
const release = ref<PendingReleaseNotes>({
  version: '',
  body: '',
  publishedAt: null,
  htmlUrl: `${REPOSITORY_URL}/releases`
})

const displayVersion = computed(() => release.value.version.replace(/^v/, ''))

const formattedDate = computed(() => {
  if (!release.value.publishedAt) return ''
  const date = new Date(release.value.publishedAt)
  if (Number.isNaN(date.getTime())) return ''
  return date.toLocaleDateString(undefined, { year: 'numeric', month: 'long', day: 'numeric' })
})

const IN_APP_SKIPPED_SECTIONS = /^(downloads?|install(ation)?|assets)$/i

function stripSkippedSections(body: string): string {
  const kept: string[] = []
  let skipping = 0
  for (const line of body.replace(/\r\n?/g, '\n').split('\n')) {
    const heading = /^(#{1,6})\s+(.+?)\s*#*\s*$/.exec(line.trim())
    if (heading) {
      const level = heading[1].length
      if (skipping && level <= skipping) skipping = 0
      if (!skipping && IN_APP_SKIPPED_SECTIONS.test(heading[2].replace(/[*_`]/g, '').trim())) {
        skipping = level
        continue
      }
    }
    if (!skipping) kept.push(line)
  }
  return kept.join('\n').trim()
}

function normalizeHeadings(body: string): string {
  return body.replace(/^(\s*)#{4,6}(\s+)/gm, '$1###$2')
}

const notesHtml = computed(() => {
  const body = release.value.body
  if (!body || body.trim().length === 0) return ''
  const cleaned = normalizeHeadings(stripSkippedSections(body))
  return cleaned ? renderMarkdown(cleaned) : ''
})

function openLink(url: string) {
  openExternalLink(url).catch(() => window.open(url, '_blank', 'noopener'))
}

function onNotesClick(event: MouseEvent) {
  const anchor = (event.target as HTMLElement | null)?.closest('a')
  if (!anchor) return
  event.preventDefault()
  if (anchor.href) openLink(anchor.href)
}

function dismiss() {
  visible.value = false
}

onMounted(() => {
  const pending = updater.takePendingReleaseNotes()
  if (!pending) return

  release.value = pending
  visible.value = true
})
</script>

<style scoped lang="scss">
.whats-new-overlay {
  position: fixed;
  inset: 0;
  z-index: 9500;
  background-color: rgba(0, 0, 0, 0.75);
  backdrop-filter: blur(6px);
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 1.5rem;
}

.whats-new {
  width: 100%;
  max-width: 640px;
  max-height: 85vh;
  display: flex;
  flex-direction: column;
  background-color: var(--bg-secondary);
  border: 1px solid var(--border-color);
  border-radius: var(--edge-radius);
  box-shadow: 8px 8px 0 0 var(--shadow-color);
  overflow: hidden;
}

.whats-new__header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 1rem;
  padding: 1.5rem;
  background-color: var(--bg-tertiary);
  border-bottom: 1px solid var(--border-color);
}

.whats-new__eyebrow {
  display: block;
  font-family: var(--font-pixel);
  font-size: 0.85rem;
  letter-spacing: 0.1em;
  color: var(--accent);
  margin-bottom: 0.35rem;
}

.whats-new__title {
  font-family: var(--font-pixel);
  font-size: 1rem;
  margin: 0;
}

.whats-new__close {
  font-size: 1.75rem;
  line-height: 1;
  background: none;
  border: none;
  color: var(--text-secondary);
  cursor: pointer;
  padding: 0 0.25rem;
}

.whats-new__close:hover {
  color: var(--text-primary);
}

.whats-new__body {
  flex: 1;
  overflow-y: auto;
  padding: 1.5rem;
  display: flex;
  flex-direction: column;
  gap: 1.25rem;
}

.whats-new__meta {
  margin: 0;
  font-size: 0.9rem;
  color: var(--text-secondary);
}

.whats-new__markdown {
  font-size: 1rem;
  line-height: 1.55;
  overflow-wrap: anywhere;
  color: var(--text-primary);
  user-select: text;

  :deep(h2),
  :deep(h3),
  :deep(h4) {
    margin: 1.25rem 0 0.5rem;
    font-family: var(--font-pixel);
    line-height: 1.3;
    color: var(--accent);
  }

  :deep(h2) {
    font-size: 0.95rem;
  }

  :deep(h3),
  :deep(h4) {
    font-size: 0.85rem;
  }

  :deep(> :first-child) {
    margin-top: 0;
  }

  :deep(> :last-child) {
    margin-bottom: 0;
  }

  :deep(p),
  :deep(ul),
  :deep(ol),
  :deep(blockquote),
  :deep(pre) {
    margin: 0 0 0.9rem;
  }

  :deep(ul),
  :deep(ol) {
    padding-left: 1.3rem;
  }

  :deep(ul) {
    list-style: square;
  }

  :deep(ol) {
    list-style: decimal;
  }

  :deep(li) {
    margin-bottom: 0.35rem;
  }

  :deep(li::marker) {
    color: var(--accent);
  }

  :deep(strong) {
    font-weight: 700;
  }

  :deep(a) {
    color: var(--accent);
    cursor: pointer;
  }

  :deep(code) {
    padding: 0.1rem 0.3rem;
    font-family: var(--font-mono);
    font-size: 0.88em;
    background-color: var(--well-bg);
  }

  :deep(pre) {
    padding: 0.75rem 1rem;
    overflow-x: auto;
    background-color: var(--well-bg);
    border: 1px solid var(--border-color);

    code {
      padding: 0;
      background: none;
    }
  }

  :deep(blockquote) {
    padding: 0.5rem 0.9rem;
    color: var(--text-secondary);
    background-color: var(--bg-tertiary);
    border-left: 3px solid var(--accent);
  }

  :deep(hr) {
    margin: 1rem 0;
    border: none;
    border-top: 1px solid var(--border-color);
  }

  :deep(img) {
    max-width: 100%;
  }
}

.whats-new__empty {
  margin: 0;
  font-size: 1rem;
  color: var(--text-secondary);
  line-height: 1.5;
}

.whats-new__link {
  font-family: var(--font-body);
  font-size: 1rem;
  color: var(--accent);
  text-decoration: none;
  align-self: flex-start;
}

.whats-new__link:hover {
  text-decoration: underline;
}

.whats-new__footer {
  padding: 1rem 1.5rem;
  border-top: 1px solid var(--border-color);
  background-color: var(--bg-tertiary);
  display: flex;
  justify-content: flex-end;
}

.whats-new-enter-active,
.whats-new-leave-active {
  transition: opacity var(--transition-normal);
}

.whats-new-enter-from,
.whats-new-leave-to {
  opacity: 0;
}
</style>