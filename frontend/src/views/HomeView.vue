<template>
  <div class="home">
    <div v-if="!authStore.authenticated" class="home__guest">
      <img
        src="@/assets/branding/ptd_logo_main.webp"
        alt="Pretupid Launcher"
        class="home__logo"
      />
      <h1 class="home__title">Pretupid Launcher</h1>
      <p class="home__tagline text-secondary">
        The awesome and super fast easy-to-use Minecraft Launcher
      </p>

      <button class="btn" :disabled="authStore.loading" @click="authStore.login">
        {{ authStore.loading ? 'Logging in...' : 'Login with Microsoft' }}
      </button>

      <div class="home__guest-actions">
        <a href="#/library" class="home__action-card">
          <span class="home__action-icon">📚</span>
          <h3 class="home__action-title">Library</h3>
          <p class="home__action-desc text-secondary">Manage your Minecraft instances and mods.</p>
        </a>

        <button class="home__action-card" @click="$emit('open-settings')">
          <span class="home__action-icon">⚙️</span>
          <h3 class="home__action-title">Settings</h3>
          <p class="home__action-desc text-secondary">Customize theme, colors, and corner style.</p>
        </button>
      </div>
    </div>

    <template v-else>
      <section class="home__welcome">
        <div class="home__profile">
          <img
            class="home__avatar"
            :src="`https://mc-heads.net/avatar/${authStore.profile?.id}/100`"
            :alt="authStore.profile?.name"
          />
          <div class="home__profile-meta">
            <p class="home__overline">Welcome back</p>
            <h1 class="home__title">{{ authStore.profile?.name }}</h1>
            <p class="home__uuid text-secondary">{{ authStore.profile?.id }}</p>
          </div>
          <button class="btn btn--ghost btn--small" @click="copyUuid">Copy UUID</button>
        </div>

        <div class="home__welcome-actions">
          <a href="#/library" class="btn">Instances</a>
          <a href="#/modrinth" class="btn btn--secondary">Modrinth</a>
          <a href="#/profile" class="btn btn--secondary">Profile</a>
          <button class="btn btn--ghost" @click="$emit('open-settings')">Settings</button>
        </div>
      </section>

      <section class="home__dashboard">
        <div class="home__dashboard-grid">
          <section class="home__panel home__panel--preview">
            <div class="home__panel-heading">
              <h3>Player Preview</h3>
              <span class="home__panel-hint">Your current Minecraft skin</span>
            </div>

            <div class="home__preview">
              <span class="home__preview-name">{{ authStore.profile?.name }}</span>
              <SkinPreview3D
                :skin-url="previewSkinUrl"
                :model-type="previewModelType"
              />
            </div>
          </section>

          <section class="home__panel">
            <div class="home__panel-heading">
              <h3>Quick Start</h3>
            </div>

            <div class="home__quick-grid">
              <a href="#/library" class="home__quick-card">
                <span class="home__quick-icon">📚</span>
                <span class="home__quick-label">Instances</span>
              </a>
              <a href="#/modrinth" class="home__quick-card">
                <span class="home__quick-icon">🔎</span>
                <span class="home__quick-label">Modrinth</span>
              </a>
              <a href="#/profile" class="home__quick-card">
                <span class="home__quick-icon">🧍</span>
                <span class="home__quick-label">Profile</span>
              </a>
              <button class="home__quick-card" @click="$emit('open-settings')">
                <span class="home__quick-icon">⚙️</span>
                <span class="home__quick-label">Settings</span>
              </button>
            </div>
          </section>
        </div>

        <section class="home__panel">
          <div class="home__panel-heading">
            <h3>Recent Instances</h3>
            <a href="#/library" class="btn btn--ghost btn--small">View All</a>
          </div>

          <div v-if="instanceStore.loading" class="pixel-loader">
            Loading Instances <span class="pixel-loader__dots"><span class="pixel-loader__dot"></span><span class="pixel-loader__dot"></span><span class="pixel-loader__dot"></span></span>
          </div>

          <div v-else-if="instanceStore.instances.length === 0" class="home__empty">
            <p class="text-secondary">No instances yet. Create one from the Library.</p>
            <a href="#/library" class="btn">Go to Library</a>
          </div>

          <div v-else class="home__recent-list">
            <a
              v-for="instance in recentInstances"
              :key="instance.id"
              :href="`#/instance/${instance.id}`"
              class="home__recent-card"
            >
              <div class="home__recent-info">
                <span class="home__recent-name">{{ instance.name }}</span>
                <span class="home__recent-meta">{{ instance.minecraft_version }} · {{ instance.mod_loader }}</span>
              </div>
              <span class="home__recent-arrow">→</span>
            </a>
          </div>
        </section>
      </section>
    </template>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted } from 'vue'
import SkinPreview3D from '@/components/SkinPreview3D.vue'
import { useAuthStore } from '@/stores/auth'
import { useInstanceStore } from '@/stores/instances'
import { useProfileStore } from '@/stores/profile'

defineEmits<{
  (e: 'open-settings'): void
}>()

const authStore = useAuthStore()
const instanceStore = useInstanceStore()
const profileStore = useProfileStore()

const DEFAULT_SKIN_URL = 'https://mc-heads.net/skin/MHF_Steve'
const recentInstances = computed(() => instanceStore.instances.slice(0, 3))

const mcHeadsSkinUrl = computed(() =>
  authStore.profile?.id
    ? `https://mc-heads.net/skin/${authStore.profile.id}`
    : DEFAULT_SKIN_URL
)

const activeSlot = computed(() => profileStore.activeSlot)

const previewSkinUrl = computed(
  () => activeSlot.value?.data_url || profileStore.currentSkinDataUrl || mcHeadsSkinUrl.value
)

const previewModelType = computed(
  () => activeSlot.value?.model_type || profileStore.currentModelType
)

function copyUuid() {
  if (authStore.profile?.id) {
    navigator.clipboard.writeText(authStore.profile.id)
  }
}

onMounted(async () => {
  await authStore.check()

  if (authStore.authenticated) {
    await instanceStore.load()

    if (authStore.profile?.id) {
      await profileStore.load(authStore.profile.id)
    }
  }
})
</script>

<style scoped lang="scss">
.home {
  display: flex;
  flex-direction: column;
  gap: 2rem;
  min-height: 70vh;
}

.home__guest {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  text-align: center;
  gap: 1.25rem;
  min-height: 70vh;
}

.home__logo {
  width: 96px;
  height: 96px;
  border-radius: var(--edge-radius);
  border: 2px solid var(--border-color);
}

.home__title {
  font-size: 2.5rem;
  margin: 0;
}

.home__tagline {
  font-size: 1.2rem;
  margin: 0;
}

.home__guest-actions {
  display: flex;
  gap: 1.5rem;
  flex-wrap: wrap;
  justify-content: center;
  margin-top: 1rem;
}

.home__action-card {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 0.75rem;
  width: 280px;
  padding: 1.5rem;
  background-color: var(--bg-secondary);
  border: 2px solid var(--border-color);
  border-radius: var(--edge-radius);
  cursor: pointer;
  text-align: left;
  transition: transform var(--transition-fast),
              box-shadow var(--transition-fast),
              border-color var(--transition-fast);
  text-decoration: none;
  color: var(--text-primary);
}

.home__action-card:hover {
  border-color: var(--accent);
  transform: translate(-2px, -2px);
  box-shadow: 4px 4px 0 0 var(--shadow-color);
}

.home__action-icon {
  font-size: 2rem;
}

.home__action-title {
  font-size: 1.1rem;
  margin: 0;
}

.home__action-desc {
  font-size: 1rem;
  margin: 0;
}

.home__welcome {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 1.5rem;
  padding: 1.5rem;
  background-color: var(--bg-secondary);
  border: 2px solid var(--border-color);
  border-radius: var(--edge-radius);
  flex-wrap: wrap;
}

.home__profile {
  display: flex;
  align-items: center;
  gap: 1rem;
  flex: 1;
  min-width: 260px;
}

.home__avatar {
  width: 72px;
  height: 72px;
  border-radius: var(--edge-radius);
  border: 2px solid var(--border-color);
  image-rendering: pixelated;
}

.home__profile-meta {
  flex: 1;
  min-width: 0;
}

.home__overline {
  font-family: var(--font-pixel);
  font-size: 0.7rem;
  text-transform: uppercase;
  color: var(--accent);
  margin: 0 0 0.5rem;
}

.home__uuid {
  font-size: 0.8rem;
  word-break: break-all;
  margin: 0.5rem 0 0;
}

.home__welcome-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 0.75rem;
}

.home__dashboard {
  display: flex;
  flex-direction: column;
  gap: 1.5rem;
}

.home__dashboard-grid {
  display: grid;
  grid-template-columns: minmax(320px, 1fr) minmax(300px, 0.9fr);
  gap: 1.5rem;
  align-items: stretch;
}

.home__panel {
  background-color: var(--bg-secondary);
  border: 2px solid var(--border-color);
  border-radius: var(--edge-radius);
  padding: 1.5rem;
}

.home__panel-heading {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 1rem;
  margin-bottom: 1rem;
}

.home__panel-heading h3 {
  font-size: 1.2rem;
  margin: 0;
}

.home__panel-hint {
  font-size: 0.75rem;
  color: var(--text-secondary);
}

.home__preview {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 0.75rem;
}

.home__preview-name {
  font-family: var(--font-pixel);
  font-size: 0.75rem;
  text-transform: uppercase;
  color: var(--text-primary);
  background-color: var(--bg-tertiary);
  padding: 0.5rem 1rem;
  border: 2px solid var(--border-color);
  border-radius: var(--edge-radius);
}

.home__quick-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 0.75rem;
}

.home__quick-card {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 0.75rem;
  min-height: 130px;
  background-color: var(--bg-tertiary);
  border: 2px solid var(--border-color);
  border-radius: var(--edge-radius);
  cursor: pointer;
  transition: transform var(--transition-fast),
              box-shadow var(--transition-fast),
              border-color var(--transition-fast);
  color: var(--text-primary);
  text-decoration: none;
}

.home__quick-card:hover {
  border-color: var(--accent);
  transform: translate(-2px, -2px);
  box-shadow: 4px 4px 0 0 var(--shadow-color);
}

.home__quick-icon {
  font-size: 2rem;
}

.home__quick-label {
  font-family: var(--font-pixel);
  font-size: 0.7rem;
  text-transform: uppercase;
}

.home__empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 1rem;
  padding: 2rem;
  text-align: center;
  border: 2px dashed var(--border-color);
  border-radius: var(--edge-radius);
}

.home__recent-list {
  display: flex;
  flex-direction: column;
  gap: 0.75rem;
}

.home__recent-card {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 1rem;
  padding: 0.9rem 1rem;
  background-color: var(--bg-tertiary);
  border: 2px solid var(--border-color);
  border-radius: var(--edge-radius);
  cursor: pointer;
  transition: transform var(--transition-fast),
              box-shadow var(--transition-fast),
              border-color var(--transition-fast);
  color: var(--text-primary);
  text-align: left;
  text-decoration: none;
}

.home__recent-card:hover {
  border-color: var(--accent);
  transform: translate(-2px, -2px);
  box-shadow: 4px 4px 0 0 var(--shadow-color);
}

.home__recent-info {
  display: flex;
  flex-direction: column;
  gap: 0.25rem;
  min-width: 0;
}

.home__recent-name {
  font-weight: bold;
  font-size: 1rem;
}

.home__recent-meta {
  font-size: 0.8rem;
  color: var(--text-secondary);
}

.home__recent-arrow {
  font-size: 1.4rem;
  color: var(--accent);
}

@media (max-width: 900px) {
  .home__dashboard-grid {
    grid-template-columns: 1fr;
  }

  .home__welcome {
    flex-direction: column;
    align-items: flex-start;
  }
}
</style>