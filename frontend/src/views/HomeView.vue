<template>
  <div class="home">
    <section class="home__hero surface-dark">
      <PixelScenery :seed="featured?.id ?? authStore.profile?.id ?? 'pretupid'" :moving="settingsStore.settings.sceneryMotion" />

      <div class="home__hero-inner">
        <div class="home__hero-copy">
          <template v-if="!authStore.authenticated">
            <p class="home__eyebrow">Welcome to Pretupid</p>
            <h1 class="home__title">Your Minecraft,<br />ready in one click.</h1>
            <p class="home__lead">Sign in with your Microsoft account to create worlds, install modpacks and change your skin.</p>
            <div class="home__actions">
              <button class="btn btn--large" :disabled="authStore.loading" @click="signIn">
                <AppIcon name="login" :size="18" />
                {{ authStore.loading ? 'Waiting for Microsoft' : 'Sign in with Microsoft' }}
              </button>
            </div>
          </template>

          <template v-else-if="featured">
            <p class="home__eyebrow">{{ greeting }}, {{ authStore.profile?.name }}</p>
            <h1 class="home__title home__title--instance" :title="featured.name">{{ featured.name }}</h1>
            <div class="home__facts">
              <span class="home__fact">Minecraft {{ featured.minecraft_version }}</span>
              <span class="home__fact">{{ loaderLabel(featured.mod_loader) }}</span>
              <span class="home__fact">
                <AppIcon name="clock" :size="12" />
                {{ featured.last_played ? `Played ${formatRelative(featured.last_played).toLowerCase()}` : 'Not played yet' }}
              </span>
            </div>
            <div class="home__actions">
              <div class="home__launch">
                <PlayButton :instance-id="featured.id" size="large" />
                <DropdownMenu v-if="instanceStore.instances.length > 1" align="left">
                  <template #trigger="{ toggle, open: menuOpen }">
                    <button
                      class="btn btn--secondary btn--large home__switch"
                      title="Switch instance"
                      aria-label="Switch instance"
                      aria-haspopup="menu"
                      :aria-expanded="menuOpen"
                      @click="toggle"
                    >
                      <AppIcon name="chevron-down" :size="18" />
                    </button>
                  </template>
                  <template #default="{ close }">
                    <p class="home__switch-label">Play instead</p>
                    <button
                      v-for="instance in instanceStore.recent"
                      :key="instance.id"
                      class="menu__item home__switch-item"
                      :class="{ 'home__switch-item--active': instance.id === featured.id }"
                      role="menuitem"
                      @click="selectHero(instance.id, close)"
                    >
                      <InstanceIcon :instance="instance" :size="22" />
                      <span class="home__switch-name">{{ instance.name }}</span>
                      <span class="home__switch-meta">{{ instance.minecraft_version }}</span>
                    </button>
                  </template>
                </DropdownMenu>
              </div>
              <RouterLink :to="{ name: 'instance-detail', params: { id: featured.id } }" class="btn btn--secondary btn--large home__manage">
                <AppIcon name="sliders" :size="16" />
                Manage
              </RouterLink>
            </div>
          </template>

          <template v-else-if="instanceStore.error && !instanceStore.loaded">
            <p class="home__eyebrow">Something went wrong</p>
            <h1 class="home__title">Your instances could not be loaded</h1>
            <p class="home__lead">{{ instanceStore.error }}</p>
            <div class="home__actions">
              <button class="btn btn--large" :disabled="instanceStore.loading" @click="retryLoad">
                <AppIcon name="refresh" :size="16" />
                Try again
              </button>
            </div>
          </template>

          <template v-else-if="instanceStore.loaded">
            <p class="home__eyebrow">{{ greeting }}, {{ authStore.profile?.name }}</p>
            <h1 class="home__title">Let's build your first world.</h1>
            <p class="home__lead">Start with plain Minecraft, add a mod loader, or grab a ready-made modpack.</p>
            <div class="home__actions">
              <button class="btn btn--large" @click="createOpen = true">
                <AppIcon name="plus" :size="18" />
                Create instance
              </button>
              <RouterLink to="/browse?type=modpack" class="btn btn--secondary btn--large">
                <AppIcon name="box" :size="16" />
                Find a modpack
              </RouterLink>
            </div>
          </template>

          <div v-else class="pixel-loader">
            Loading
            <span class="pixel-loader__dots"><span class="pixel-loader__dot" /><span class="pixel-loader__dot" /><span class="pixel-loader__dot" /></span>
          </div>
        </div>

        <div
          v-if="authStore.authenticated"
          class="home__avatar"
          title="Drag to rotate · click to change skin"
          @pointerdown="onAvatarDown"
          @click="onAvatarClick"
        >
          <SkinPreview3D :skin-url="previewSkinUrl" :width="300" :height="470" compact />
        </div>
      </div>
    </section>

    <div class="home__body">
      <RouterLink
        v-if="clientPromo && featured"
        :to="{ name: 'instance-detail', params: { id: featured.id } }"
        class="home__promo"
      >
        <img src="@shared/branding/pretupid_logo_512x.png" alt="" class="home__promo-logo" />
        <span class="home__promo-text">
          <span class="home__promo-title">Add Pretupid Client to {{ featured.name }}</span>
          <span class="home__promo-hint">Zoom, Freelook, a movable HUD, emotes, cosmetics your friends can see and culling for smoother frames. Free, one click.</span>
        </span>
        <span class="btn btn--small home__promo-cta">Get it</span>
      </RouterLink>

      <section v-if="others.length > 0" class="home__section">
        <header class="home__section-header">
          <h2 class="home__section-title">Jump back in</h2>
          <RouterLink to="/library" class="home__section-link">
            Library
            <AppIcon name="back" :size="12" class="home__chevron" />
          </RouterLink>
        </header>
        <div class="home__recent">
          <RouterLink
            v-for="instance in others"
            :key="instance.id"
            :to="{ name: 'instance-detail', params: { id: instance.id } }"
            class="home__tile"
          >
            <InstanceIcon :instance="instance" :size="44" />
            <span class="home__tile-text">
              <span class="home__tile-name">{{ instance.name }}</span>
              <span class="home__tile-meta">{{ instance.minecraft_version }} · {{ loaderLabel(instance.mod_loader) }}</span>
            </span>
            <span class="home__tile-play" @click.prevent>
              <PlayButton :instance-id="instance.id" size="small" compact />
            </span>
          </RouterLink>
        </div>
      </section>

      <section v-if="news.length > 0" class="home__section">
        <header class="home__section-header">
          <h2 class="home__section-title">What's new</h2>
          <button class="home__section-link" @click="openWebsite('/news')">
            All news
            <AppIcon name="back" :size="12" class="home__chevron" />
          </button>
        </header>
        <div class="home__news">
          <button v-for="post in news" :key="post.id" class="home__news-card" @click="openWebsite(`/news/${encodeURIComponent(post.id)}`)">
            <span class="home__news-tag" :class="`home__news-tag--${post.category}`">{{ categoryLabel(post.category) }}</span>
            <span class="home__news-title">{{ post.title }}</span>
            <span class="home__news-summary">{{ post.summary }}</span>
            <span v-if="post.publishedAt" class="home__news-date">{{ formatRelative(new Date(post.publishedAt * 1000).toISOString()) }}</span>
          </button>
        </div>
      </section>

      <section v-if="authStore.authenticated" class="home__section">
        <header class="home__section-header">
          <h2 class="home__section-title">Do more</h2>
        </header>
        <div class="home__shortcuts">
          <button class="home__shortcut" @click="createOpen = true">
            <span class="home__shortcut-icon"><AppIcon name="plus" :size="20" /></span>
            <span class="home__shortcut-title">New instance</span>
            <span class="home__shortcut-hint">Any version, any loader</span>
          </button>
          <RouterLink to="/browse?type=modpack" class="home__shortcut">
            <span class="home__shortcut-icon"><AppIcon name="box" :size="20" /></span>
            <span class="home__shortcut-title">Modpacks</span>
            <span class="home__shortcut-hint">Installed in one click</span>
          </RouterLink>
          <RouterLink to="/browse?type=shader" class="home__shortcut">
            <span class="home__shortcut-icon"><AppIcon name="sparkle" :size="20" /></span>
            <span class="home__shortcut-title">Shaders</span>
            <span class="home__shortcut-hint">Make it look stunning</span>
          </RouterLink>
          <RouterLink to="/profile" class="home__shortcut">
            <span class="home__shortcut-icon"><AppIcon name="user" :size="20" /></span>
            <span class="home__shortcut-title">Skins</span>
            <span class="home__shortcut-hint">Swap your look</span>
          </RouterLink>
        </div>
      </section>

      <section v-else class="home__section">
        <div class="home__shortcuts">
          <div v-for="point in guestPoints" :key="point.title" class="home__shortcut home__shortcut--static">
            <span class="home__shortcut-icon"><AppIcon :name="point.icon" :size="20" /></span>
            <span class="home__shortcut-title">{{ point.title }}</span>
            <span class="home__shortcut-hint">{{ point.hint }}</span>
          </div>
        </div>
      </section>
    </div>

    <CreateInstanceModal v-if="createOpen" @close="createOpen = false" @created="openInstance" />
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { RouterLink, useRouter } from 'vue-router'
import AppIcon, { type IconName } from '@/components/AppIcon.vue'
import InstanceIcon from '@/components/InstanceIcon.vue'
import PlayButton from '@/components/PlayButton.vue'
import PixelScenery from '@ui/PixelScenery.vue'
import SkinPreview3D from '@/components/SkinPreview3D.vue'
import CreateInstanceModal from '@/components/CreateInstanceModal.vue'
import DropdownMenu from '@/components/DropdownMenu.vue'
import { WEBSITE_URL } from '@/config'
import { fetchNews, openExternalLink, pretupidClientStatus, type NewsPost } from '@/api'
import { useAuthStore } from '@/stores/auth'
import { useInstanceStore } from '@/stores/instances'
import { useProfileStore } from '@/stores/profile'
import { useSettingsStore } from '@/stores/settings'
import { useNotificationsStore } from '@/stores/notifications'
import { formatRelative, loaderLabel } from '@/utils/format'
import type { Instance } from '@/types'

defineEmits<{ (e: 'open-settings'): void }>()

const router = useRouter()
const authStore = useAuthStore()
const instanceStore = useInstanceStore()
const profileStore = useProfileStore()
const settingsStore = useSettingsStore()
const notifications = useNotificationsStore()
const createOpen = ref(false)

const DEFAULT_SKIN_URL = 'https://mc-heads.net/skin/MHF_Steve'

const guestPoints: { icon: IconName; title: string; hint: string }[] = [
  { icon: 'box', title: 'Every loader', hint: 'Fabric, Quilt, Forge and NeoForge' },
  { icon: 'compass', title: 'Modrinth built in', hint: 'Mods and packs with dependencies' },
  { icon: 'user', title: 'Skin library', hint: 'Save skins and switch in seconds' },
  { icon: 'check', title: 'Verified files', hint: 'Every download is checksum-checked' }
]

const HERO_KEY = 'pretupid-home-instance'
const NEWS_CATEGORY_LABELS: Record<string, string> = {
  news: 'News',
  update: 'Update',
  announcement: 'Announcement',
  event: 'Event',
  maintenance: 'Maintenance'
}

function readHeroId(): string | null {
  try {
    return localStorage.getItem(HERO_KEY)
  } catch {
    return null
  }
}

const heroId = ref<string | null>(readHeroId())

let avatarDown: { x: number; y: number } | null = null

function onAvatarDown(event: PointerEvent) {
  avatarDown = { x: event.clientX, y: event.clientY }
}

function onAvatarClick(event: MouseEvent) {
  const start = avatarDown
  avatarDown = null
  if (start && Math.hypot(event.clientX - start.x, event.clientY - start.y) > 4) return
  router.push('/profile')
}

const news = ref<NewsPost[]>([])
const clientPromo = ref(false)

const featured = computed(() => (heroId.value ? instanceStore.byId(heroId.value) : undefined) ?? instanceStore.recent[0] ?? null)
const others = computed(() => instanceStore.recent.filter((i) => i.id !== featured.value?.id).slice(0, 6))

function selectHero(id: string, close: () => void) {
  close()
  heroId.value = id
  try {
    localStorage.setItem(HERO_KEY, id)
  } catch {
    void 0
  }
}

function categoryLabel(category: string): string {
  return NEWS_CATEGORY_LABELS[category] ?? 'News'
}

function openWebsite(path: string) {
  openExternalLink(`${WEBSITE_URL}${path}`).catch((e) => notifications.notifyError('Could not open the link', String(e)))
}

async function loadNews() {
  try {
    news.value = (await fetchNews()).slice(0, 3)
  } catch {
    news.value = []
  }
}

async function refreshPromo() {
  const target = featured.value
  if (!target || target.mod_loader === 'vanilla') {
    clientPromo.value = false
    return
  }
  try {
    const status = await pretupidClientStatus(target.id)
    clientPromo.value = status.supported && !status.installed && featured.value?.id === target.id
  } catch {
    clientPromo.value = false
  }
}

const greeting = computed(() => {
  const hour = new Date().getHours()
  if (hour < 5) return 'Up late'
  if (hour < 12) return 'Good morning'
  if (hour < 18) return 'Good afternoon'
  return 'Good evening'
})

const previewSkinUrl = computed(
  () =>
    profileStore.activeSlot?.data_url ||
    profileStore.currentSkinDataUrl ||
    (authStore.profile?.id ? `https://mc-heads.net/skin/${authStore.profile.id}` : DEFAULT_SKIN_URL)
)

async function signIn() {
  try {
    await authStore.login()
  } catch (e) {
    notifications.notifyError('Sign-in failed', String(e))
  }
}

function openInstance(instance: Instance) {
  router.push({ name: 'instance-detail', params: { id: instance.id } })
}

function retryLoad() {
  instanceStore.load().catch(() => void 0)
}

function loadAccountData() {
  if (!authStore.authenticated) return
  instanceStore.load(instanceStore.loaded).catch(() => void 0)
  if (authStore.profile?.id && profileStore.profileId !== authStore.profile.id && !profileStore.loading) {
    profileStore.load(authStore.profile.id).catch(() => void 0)
  }
}

onMounted(() => {
  loadAccountData()
  loadNews()
})

watch(() => authStore.profile?.id, loadAccountData)
watch(() => featured.value?.id, refreshPromo, { immediate: true })
</script>

<style scoped lang="scss">
.home {
  display: flex;
  flex-direction: column;
  min-height: 100%;
}

.home__hero {
  position: relative;
  display: flex;
  align-items: flex-end;
  min-height: clamp(340px, 52vh, 460px);
  overflow: hidden;
  isolation: isolate;
}

.home__hero-inner {
  position: relative;
  z-index: 1;
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 2rem;
  width: 100%;
  padding: 3rem 2.5rem 1.75rem;
}

.home__hero-copy {
  position: relative;
  z-index: 1;
  display: flex;
  flex-direction: column;
  gap: 0.85rem;
  min-width: 0;
  max-width: 560px;
  padding-bottom: 0.5rem;
  animation: home-rise 0.5s cubic-bezier(0.22, 1, 0.36, 1) both;
}

.home__eyebrow {
  margin: 0;
  font-size: 0.85rem;
  font-weight: 600;
  letter-spacing: 0.08em;
  text-transform: uppercase;
  color: color-mix(in srgb, var(--accent) 70%, #ffffff);
  text-shadow: 0 1px 8px rgba(0, 0, 0, 0.6);
}

.home__title {
  margin: 0;
  font-size: clamp(1.7rem, 3.2vw, 2.5rem);
  line-height: 1.15;
  color: #ffffff;
  text-shadow: 0 2px 0 rgba(0, 0, 0, 0.35), 0 4px 24px rgba(0, 0, 0, 0.5);
}

.home__title--instance {
  display: -webkit-box;
  overflow: hidden;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow-wrap: anywhere;
}

.home__lead {
  margin: 0;
  max-width: 460px;
  font-size: 1rem;
  color: rgba(255, 255, 255, 0.82);
  text-shadow: 0 1px 8px rgba(0, 0, 0, 0.6);
}

.home__facts {
  display: flex;
  flex-wrap: wrap;
  gap: 0.4rem;
}

.home__fact {
  display: inline-flex;
  align-items: center;
  gap: 0.35rem;
  padding: 0.25rem 0.6rem;
  font-size: 0.82rem;
  color: rgba(255, 255, 255, 0.9);
  background-color: rgba(0, 0, 0, 0.38);
  border: 1px solid rgba(255, 255, 255, 0.12);
  border-radius: var(--edge-radius);
  backdrop-filter: blur(6px);
}

.home__actions {
  display: flex;
  flex-wrap: wrap;
  gap: 0.6rem;
  margin-top: 0.4rem;
}

.home__launch {
  display: flex;
  gap: 2px;

  :deep(.play-btn) {
    min-width: 9rem;
  }
}

.home__switch {
  padding-left: 0.75rem;
  padding-right: 0.75rem;
  background-color: rgba(0, 0, 0, 0.35);
  backdrop-filter: blur(6px);
}

.home__switch-label {
  margin: 0;
  padding: 0.35rem 0.75rem 0.25rem;
  font-size: 0.72rem;
  font-weight: 700;
  letter-spacing: 0.06em;
  text-transform: uppercase;
  color: var(--text-dim);
}

.home__switch-item {
  min-width: 260px;
  gap: 0.6rem;
}

.home__switch-item--active {
  color: var(--accent);
}

.home__switch-name {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
  text-align: left;
}

.home__switch-meta {
  font-size: 0.78rem;
  color: var(--text-dim);
}

.home__manage {
  background-color: rgba(0, 0, 0, 0.35);
  backdrop-filter: blur(6px);
}

.home__avatar {
  position: absolute;
  cursor: grab;
  right: clamp(0.5rem, 4vw, 3rem);
  bottom: -120px;
  z-index: 0;
  mask-image: linear-gradient(180deg, #000 0%, #000 52%, transparent 78%);
  transition: transform var(--transition-normal);

  &:hover {
    transform: translateY(-6px);
  }
}

.home__body {
  display: flex;
  flex-direction: column;
  gap: 2rem;
  padding: 1.5rem 2.5rem 2.5rem;
}

.home__section {
  display: flex;
  flex-direction: column;
  gap: 0.85rem;
}

.home__section-header {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
}

.home__section-title {
  font-size: 1rem;
}

.home__section-link {
  padding: 0;
  font: inherit;
  background: none;
  border: none;
  cursor: pointer;
  display: inline-flex;
  align-items: center;
  gap: 0.3rem;
  font-size: 0.85rem;
  color: var(--text-secondary);
  text-decoration: none;

  &:hover {
    color: var(--accent);
  }
}

.home__chevron {
  transform: rotate(180deg);
}

.home__recent {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(min(100%, 250px), 1fr));
  gap: 0.75rem;
}

.home__tile {
  display: flex;
  align-items: center;
  gap: 0.75rem;
  min-width: 0;
  padding: 0.7rem;
  color: inherit;
  text-decoration: none;
  background-color: var(--bg-secondary);
  border: 1px solid var(--border-color);
  border-radius: var(--edge-radius);
  transition: border-color var(--transition-fast), transform var(--transition-fast), background-color var(--transition-fast);

  &:hover {
    border-color: var(--border-strong);
    background-color: var(--bg-elevated);
    transform: translateY(-2px);
  }

  &:focus-visible {
    outline: 2px solid var(--accent);
    outline-offset: 2px;
  }
}

.home__tile-text {
  display: flex;
  flex: 1;
  flex-direction: column;
  gap: 0.15rem;
  min-width: 0;
}

.home__tile-name {
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
  font-weight: 600;
}

.home__tile-meta {
  font-size: 0.8rem;
  color: var(--text-dim);
}

.home__tile-play {
  flex-shrink: 0;
  opacity: 0.85;
  transition: opacity var(--transition-fast);

  .home__tile:hover & {
    opacity: 1;
  }
}

.home__promo {
  display: flex;
  align-items: center;
  gap: 1rem;
  padding: 0.9rem 1rem;
  color: inherit;
  text-decoration: none;
  background:
    linear-gradient(100deg, color-mix(in srgb, var(--accent) 22%, transparent), color-mix(in srgb, var(--accent) 4%, transparent) 70%),
    var(--bg-secondary);
  border: 1px solid color-mix(in srgb, var(--accent) 45%, transparent);
  border-radius: var(--edge-radius);
  transition: transform var(--transition-fast), border-color var(--transition-fast);

  &:hover {
    transform: translateY(-2px);
    border-color: var(--accent);
  }
}

.home__promo-logo {
  width: 40px;
  height: 40px;
  flex-shrink: 0;
  image-rendering: pixelated;
}

.home__promo-text {
  display: flex;
  flex: 1;
  flex-direction: column;
  gap: 0.2rem;
  min-width: 0;
}

.home__promo-title {
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
  font-family: var(--font-pixel);
  font-size: 0.92rem;
}

.home__promo-hint {
  font-size: 0.85rem;
  color: var(--text-secondary);
}

.home__promo-cta {
  flex-shrink: 0;
}

.home__news {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(min(100%, 240px), 1fr));
  gap: 0.75rem;
}

.home__news-card {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 0.4rem;
  padding: 1rem;
  text-align: left;
  font: inherit;
  color: inherit;
  background-color: var(--bg-secondary);
  border: 1px solid var(--border-color);
  border-radius: var(--edge-radius);
  cursor: pointer;
  transition: border-color var(--transition-fast), transform var(--transition-fast);

  &:hover {
    border-color: var(--border-strong);
    transform: translateY(-2px);
  }

  &:focus-visible {
    outline: 2px solid var(--accent);
    outline-offset: 2px;
  }
}

.home__news-tag {
  padding: 0.1rem 0.45rem;
  font-size: 0.7rem;
  font-weight: 700;
  letter-spacing: 0.06em;
  text-transform: uppercase;
  color: var(--accent);
  background-color: color-mix(in srgb, var(--accent) 14%, transparent);
  border-radius: var(--edge-radius);
}

.home__news-tag--update {
  color: var(--success);
  background-color: color-mix(in srgb, var(--success) 14%, transparent);
}

.home__news-tag--event {
  color: #c084fc;
  background-color: rgba(192, 132, 252, 0.14);
}

.home__news-tag--maintenance {
  color: var(--warning);
  background-color: color-mix(in srgb, var(--warning) 14%, transparent);
}

.home__news-title {
  font-size: 0.98rem;
  font-weight: 700;
}

.home__news-summary {
  display: -webkit-box;
  overflow: hidden;
  font-size: 0.85rem;
  color: var(--text-secondary);
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
}

.home__news-date {
  margin-top: auto;
  font-size: 0.78rem;
  color: var(--text-dim);
}

.home__shortcuts {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(min(100%, 190px), 1fr));
  gap: 0.75rem;
}

.home__shortcut {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 0.3rem;
  padding: 1rem;
  text-align: left;
  color: inherit;
  text-decoration: none;
  font: inherit;
  background:
    linear-gradient(150deg, color-mix(in srgb, var(--accent) 10%, transparent), transparent 60%),
    var(--bg-secondary);
  border: 1px solid var(--border-color);
  border-radius: var(--edge-radius);
  cursor: pointer;
  transition: border-color var(--transition-fast), transform var(--transition-fast);

  &:hover {
    border-color: color-mix(in srgb, var(--accent) 60%, transparent);
    transform: translateY(-2px);
  }

  &:focus-visible {
    outline: 2px solid var(--accent);
    outline-offset: 2px;
  }
}

.home__shortcut--static {
  cursor: default;

  &:hover {
    border-color: var(--border-color);
    transform: none;
  }
}

.home__shortcut-icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 38px;
  height: 38px;
  margin-bottom: 0.35rem;
  color: var(--accent);
  background-color: color-mix(in srgb, var(--accent) 14%, transparent);
  border-radius: var(--edge-radius);
}

.home__shortcut-title {
  font-family: var(--font-pixel);
  font-size: 0.9rem;
}

.home__shortcut-hint {
  font-size: 0.82rem;
  color: var(--text-secondary);
}

@keyframes home-rise {
  from {
    opacity: 0;
    transform: translateY(10px);
  }
}

@media (max-width: 900px) {
  .home__avatar {
    display: none;
  }

  .home__hero-inner,
  .home__body {
    padding-left: 1.5rem;
    padding-right: 1.5rem;
  }
}

@media (prefers-reduced-motion: reduce) {
  .home__hero-copy {
    animation: none;
  }
}
</style>
