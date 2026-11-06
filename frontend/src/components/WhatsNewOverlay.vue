<template>
  <Transition name="whats-new">
    <div v-if="visible" class="whats-new-overlay" @click.self="dismiss">
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

          <div v-if="parsedNotes.length > 0" class="whats-new__notes">
            <section v-for="(section, i) in parsedNotes" :key="i" class="whats-new__section">
              <h3 v-if="section.title" class="whats-new__section-title">{{ section.title }}</h3>
              <ul class="whats-new__list">
                <li v-for="(item, j) in section.items" :key="j">{{ item }}</li>
              </ul>
            </section>
          </div>

          <p v-else class="whats-new__empty">
            This release contains performance and stability improvements. See the full release notes
            on GitHub for details.
          </p>

          <a :href="release.htmlUrl" target="_blank" rel="noopener noreferrer" class="whats-new__link">
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
import { computed, onMounted, ref } from 'vue'
import { useUpdaterStore, type PendingReleaseNotes } from '@/stores/updater'

interface NotesSection {
  title: string
  items: string[]
}

const updater = useUpdaterStore()
const visible = ref(false)
const release = ref<PendingReleaseNotes>({
  version: '',
  body: '',
  publishedAt: null,
  htmlUrl: 'https://github.com/Yannosay/pretupid/releases'
})

const displayVersion = computed(() => release.value.version.replace(/^v/, ''))

const formattedDate = computed(() => {
  if (!release.value.publishedAt) return ''
  try {
    const date = new Date(release.value.publishedAt)
    if (Number.isNaN(date.getTime())) return ''
    return date.toLocaleDateString(undefined, {
      year: 'numeric',
      month: 'long',
      day: 'numeric'
    })
  } catch {
    return ''
  }
})

const parsedNotes = computed<NotesSection[]>(() => {
  const body = release.value.body
  if (!body || body.trim().length === 0) return []

  const sections: NotesSection[] = []
  let current: NotesSection | null = null

  for (const rawLine of body.split(/\r?\n/)) {
    const line = rawLine.trim()
    if (line.length === 0) continue

    if (line.startsWith('#')) {
      const title = line.replace(/^#+\s*/, '').trim()
      if (title.length > 0) {
        current = { title, items: [] }
        sections.push(current)
      }
      continue
    }

    if (line.startsWith('- ') || line.startsWith('* ')) {
      const item = line.slice(2).trim()
      if (item.length === 0) continue
      if (!current) {
        current = { title: '', items: [] }
        sections.push(current)
      }
      current.items.push(item)
      continue
    }

    if (current && current.items.length > 0 && !line.startsWith('http')) {
      const lastIndex = current.items.length - 1
      current.items[lastIndex] = `${current.items[lastIndex]} ${line}`.trim()
    }
  }

  return sections.filter((section) => section.items.length > 0)
})

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
  border: 2px solid var(--border-color);
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
  border-bottom: 2px solid var(--border-color);
}

.whats-new__eyebrow {
  display: block;
  font-family: var(--font-pixel);
  font-size: 0.55rem;
  text-transform: uppercase;
  letter-spacing: 0.1em;
  color: var(--accent);
  margin-bottom: 0.35rem;
}

.whats-new__title {
  font-family: var(--font-pixel);
  font-size: 1rem;
  text-transform: uppercase;
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

.whats-new__notes {
  display: flex;
  flex-direction: column;
  gap: 1.25rem;
}

.whats-new__section-title {
  font-family: var(--font-pixel);
  font-size: 0.75rem;
  text-transform: uppercase;
  color: var(--accent);
  margin: 0 0 0.5rem;
}

.whats-new__list {
  list-style: none;
  margin: 0;
  padding: 0;
  display: flex;
  flex-direction: column;
  gap: 0.4rem;
}

.whats-new__list li {
  position: relative;
  padding-left: 1.25rem;
  font-size: 1rem;
  line-height: 1.45;
  color: var(--text-primary);
}

.whats-new__list li::before {
  content: '';
  position: absolute;
  left: 0;
  top: 0.55rem;
  width: 8px;
  height: 8px;
  background-color: var(--accent);
  border-radius: var(--edge-radius);
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
  border-top: 2px solid var(--border-color);
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