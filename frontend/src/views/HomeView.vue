<template>
  <div class="home">
    <section v-if="!authStore.authenticated" class="home__guest">
      <img src="@/assets/branding/ptd_logo_main.webp" alt="Pretupid Launcher" class="home__logo" />
      <h1 class="home__guest-title">Pretupid Launcher</h1>
      <p class="home__guest-tagline">Fast, safe and simple Minecraft — with mods and modpacks one click away.</p>
      <button class="btn btn--large" :disabled="authStore.loading" @click="signIn">
        <AppIcon name="login" :size="18" />
        {{ authStore.loading ? 'Signing in' : 'Sign in with Microsoft' }}
      </button>
      <ul class="home__guest-points">
        <li><AppIcon name="box" :size="18" />Fabric, Quilt, Forge and NeoForge out of the box</li>
        <li><AppIcon name="compass" :size="18" />Install mods and modpacks from Modrinth with dependencies</li>
        <li><AppIcon name="check" :size="18" />Every download is checksum-verified</li>
      </ul>
    </section>

    <template v-else>
      <section class="home__hero">
        <div class="home__hero-main">
          <p class="home__eyebrow">{{ greeting }}, {{ authStore.profile?.name }}</p>

          <template v-if="featured">
            <h1 class="home__hero-title">Jump back in</h1>
            <RouterLink :to="{ name: 'instance-detail', params: { id: featured.id } }" class="home__featured">
              <InstanceIcon :instance="featured" :size="72" />
              <div class="home__featured-info">
                <span class="home__featured-name">{{ featured.name }}</span>
                <span class="home__featured-meta">
                  {{ featured.minecraft_version }} · {{ loaderLabel(featured.mod_loader) }}
                </span>
                <span class="home__featured-meta">
                  <AppIcon name="clock" :size="12" />
                  {{ featured.last_played ? formatRelative(featured.last_played) : 'Not played yet' }}
                  <template v-if="featured.playtime_seconds"> · {{ formatPlaytime(featured.playtime_seconds) }}</template>
                </span>
              </div>
            </RouterLink>
            <div class="home__hero-actions">
              <PlayButton :instance-id="featured.id" size="large" />
              <RouterLink to="/library" class="btn btn--ghost">All instances</RouterLink>
            </div>
          </template>

          <template v-else-if="instanceStore.loaded">
            <h1 class="home__hero-title">Let's set you up</h1>
            <p class="home__hero-text">Pick how you want to play. You can always add more later.</p>
            <div class="home__start">
              <button class="home__start-card" @click="createOpen = true">
                <AppIcon name="plus" :size="24" />
                <span class="home__start-title">Create an instance</span>
                <span class="home__start-hint">Choose a Minecraft version and an optional mod loader.</span>
              </button>
              <RouterLink to="/browse?type=modpack" class="home__start-card">
                <AppIcon name="box" :size="24" />
                <span class="home__start-title">Install a modpack</span>
                <span class="home__start-hint">Ready-made packs from Modrinth, installed in one click.</span>
              </RouterLink>
            </div>
          </template>

          <div v-else class="pixel-loader">
            Loading
            <span class="pixel-loader__dots"><span class="pixel-loader__dot" /><span class="pixel-loader__dot" /><span class="pixel-loader__dot" /></span>
          </div>
        </div>

        <RouterLink to="/profile" class="home__skin" title="Change skin">
          <SkinPreview3D :skin-url="previewSkinUrl" :model-type="previewModelType" :width="200" :height="280" compact />
          <span class="home__skin-label">Change skin</span>
        </RouterLink>
      </section>

      <section v-if="others.length > 0" class="home__section">
        <div class="home__section-header">
          <h2 class="home__section-title">Recent</h2>
          <RouterLink to="/library" class="btn btn--ghost btn--small">View all</RouterLink>
        </div>
        <div class="home__grid">
          <InstanceCard v-for="instance in others" :key="instance.id" :instance="instance" @delete="goToLibrary" />
        </div>
      </section>

      <section class="home__stats">
        <div class="home__stat">
          <span class="home__stat-value">{{ instanceStore.instances.length }}</span>
          <span class="home__stat-label">Instances</span>
        </div>
        <div class="home__stat">
          <span class="home__stat-value">{{ formatPlaytime(totalPlaytime) }}</span>
          <span class="home__stat-label">Total playtime</span>
        </div>
        <RouterLink to="/browse" class="home__stat home__stat--link">
          <AppIcon name="compass" :size="20" />
          <span class="home__stat-label">Discover mods</span>
        </RouterLink>
      </section>
    </template>

    <CreateInstanceModal v-if="createOpen" @close="createOpen = false" @created="openInstance" />
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { RouterLink, useRouter } from 'vue-router'
import AppIcon from '@/components/AppIcon.vue'
import InstanceIcon from '@/components/InstanceIcon.vue'
import InstanceCard from '@/components/InstanceCard.vue'
import PlayButton from '@/components/PlayButton.vue'
import SkinPreview3D from '@/components/SkinPreview3D.vue'
import CreateInstanceModal from '@/components/CreateInstanceModal.vue'
import { useAuthStore } from '@/stores/auth'
import { useInstanceStore } from '@/stores/instances'
import { useProfileStore } from '@/stores/profile'
import { useNotificationsStore } from '@/stores/notifications'
import { formatPlaytime, formatRelative, loaderLabel } from '@/utils/format'
import type { Instance } from '@/types'

defineEmits<{ (e: 'open-settings'): void }>()

const router = useRouter()
const authStore = useAuthStore()
const instanceStore = useInstanceStore()
const profileStore = useProfileStore()
const notifications = useNotificationsStore()
const createOpen = ref(false)

const DEFAULT_SKIN_URL = 'https://mc-heads.net/skin/MHF_Steve'

const featured = computed(() => instanceStore.recent[0] ?? null)
const others = computed(() => instanceStore.recent.slice(1, 7))
const totalPlaytime = computed(() => instanceStore.instances.reduce((sum, i) => sum + (i.playtime_seconds ?? 0), 0))

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
const previewModelType = computed(() => profileStore.activeSlot?.model_type || profileStore.currentModelType)

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

function goToLibrary() {
  router.push({ name: 'library' })
}

onMounted(async () => {
  await authStore.check()
  if (authStore.authenticated) {
    await instanceStore.load(instanceStore.loaded)
    if (authStore.profile?.id) profileStore.load(authStore.profile.id).catch(() => void 0)
  }
})
</script>

<style scoped lang="scss">
.home {
  display: flex;
  flex-direction: column;
  gap: 1.75rem;
}

.home__guest {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 1.1rem;
  min-height: 70vh;
  text-align: center;
}

.home__logo {
  width: 96px;
  height: 96px;
  border: 2px solid var(--border-color);
  border-radius: var(--edge-radius);
}

.home__guest-title {
  margin: 0;
  font-family: var(--font-pixel);
  font-size: 1.6rem;
}

.home__guest-tagline {
  max-width: 34rem;
  margin: 0;
  font-size: 1.3rem;
  color: var(--text-secondary);
}

.home__guest-points {
  display: flex;
  flex-direction: column;
  gap: 0.6rem;
  margin: 1rem 0 0;
  padding: 0;
  list-style: none;
  text-align: left;

  li {
    display: flex;
    align-items: center;
    gap: 0.75rem;
    color: var(--text-secondary);
  }

  .app-icon {
    color: var(--accent);
  }
}

.home__hero {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 220px;
  gap: 1.5rem;
  padding: 1.5rem;
  background:
    linear-gradient(135deg, color-mix(in srgb, var(--accent) 16%, transparent), transparent 60%),
    var(--bg-secondary);
  border: 2px solid var(--border-color);
  border-radius: var(--edge-radius);
}

.home__hero-main {
  display: flex;
  flex-direction: column;
  gap: 1rem;
  min-width: 0;
}

.home__eyebrow {
  margin: 0;
  font-family: var(--font-pixel);
  font-size: 0.6rem;
  text-transform: uppercase;
  color: var(--accent);
}

.home__hero-title {
  margin: 0;
  font-family: var(--font-pixel);
  font-size: 1.4rem;
  text-transform: uppercase;
}

.home__hero-text {
  margin: 0;
  color: var(--text-secondary);
}

.home__featured {
  display: flex;
  align-items: center;
  gap: 1rem;
  padding: 0.9rem;
  color: var(--text-primary);
  text-decoration: none;
  background-color: var(--bg-tertiary);
  border: 2px solid var(--border-color);
  border-radius: var(--edge-radius);
  transition: border-color var(--transition-fast);

  &:hover {
    border-color: var(--accent);
  }
}

.home__featured-info {
  display: flex;
  flex-direction: column;
  gap: 0.35rem;
  min-width: 0;
}

.home__featured-name {
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
  font-family: var(--font-pixel);
  font-size: 0.85rem;
}

.home__featured-meta {
  display: flex;
  align-items: center;
  gap: 0.4rem;
  color: var(--text-secondary);
}

.home__hero-actions {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 0.75rem;
}

.home__start {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(220px, 1fr));
  gap: 0.75rem;
}

.home__start-card {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 0.5rem;
  padding: 1.1rem;
  text-align: left;
  color: var(--text-primary);
  text-decoration: none;
  background-color: var(--bg-tertiary);
  border: 2px solid var(--border-color);
  border-radius: var(--edge-radius);
  cursor: pointer;
  transition: transform var(--transition-fast), border-color var(--transition-fast), box-shadow var(--transition-fast);

  .app-icon {
    color: var(--accent);
  }

  &:hover {
    border-color: var(--accent);
    transform: translate(-2px, -2px);
    box-shadow: 4px 4px 0 0 var(--shadow-color);
  }
}

.home__start-title {
  font-family: var(--font-pixel);
  font-size: 0.65rem;
  text-transform: uppercase;
}

.home__start-hint {
  color: var(--text-secondary);
  line-height: 1.15;
}

.home__skin {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 0.5rem;
  color: var(--text-secondary);
  text-decoration: none;

  &:hover .home__skin-label {
    color: var(--accent);
  }
}

.home__skin-label {
  font-family: var(--font-pixel);
  font-size: 0.5rem;
  text-transform: uppercase;
}

.home__section {
  display: flex;
  flex-direction: column;
  gap: 0.9rem;
}

.home__section-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.home__section-title {
  margin: 0;
  font-family: var(--font-pixel);
  font-size: 0.8rem;
  text-transform: uppercase;
}

.home__grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(240px, 1fr));
  gap: 1rem;
}

.home__stats {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(180px, 1fr));
  gap: 1rem;
}

.home__stat {
  display: flex;
  flex-direction: column;
  gap: 0.4rem;
  padding: 1rem;
  color: var(--text-primary);
  text-decoration: none;
  background-color: var(--bg-secondary);
  border: 2px solid var(--border-color);
  border-radius: var(--edge-radius);
}

.home__stat--link {
  flex-direction: row;
  align-items: center;
  justify-content: center;
  gap: 0.6rem;
  color: var(--accent);

  &:hover {
    border-color: var(--accent);
  }
}

.home__stat-value {
  font-family: var(--font-pixel);
  font-size: 0.9rem;
  color: var(--accent);
}

.home__stat-label {
  font-size: 1rem;
  color: var(--text-secondary);
}

.home__stat--link .home__stat-label {
  color: inherit;
}

@media (max-width: 900px) {
  .home__hero {
    grid-template-columns: 1fr;
  }

  .home__skin {
    display: none;
  }
}
</style>
