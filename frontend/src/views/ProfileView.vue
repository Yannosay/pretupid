<template>
  <div class="profile-view">
    <header class="profile-view__header">
      <h1 class="profile-view__title">Profile</h1>
    </header>

    <div v-if="!authStore.authenticated" class="profile-view__auth">
      <p class="text-secondary">You must be logged in to manage skins.</p>
      <button class="btn" :disabled="authStore.loading" @click="authStore.login">
        {{ authStore.loading ? 'Logging in...' : 'Login with Microsoft' }}
      </button>
    </div>

    <template v-else>
      <section class="profile-card">
        <div class="profile-card__body">
          <img
            class="profile-card__avatar"
            :src="`https://mc-heads.net/avatar/${authStore.profile?.id}/100`"
            :alt="authStore.profile?.name"
          />

          <div class="profile-card__info">
            <span class="profile-card__label">Player</span>
            <span class="profile-card__name">{{ authStore.profile?.name }}</span>

            <span class="profile-card__label profile-card__uuid-label">UUID</span>
            <span class="profile-card__uuid">{{ authStore.profile?.id }}</span>
          </div>

          <div class="profile-card__actions">
            <button class="btn btn--ghost btn--small" @click="copyUuid">Copy UUID</button>
            <button class="btn btn--ghost btn--small" @click="refreshProfile">Refresh</button>
          </div>
        </div>
      </section>

      <div v-if="profileStore.error" class="profile-view__error">{{ profileStore.error }}</div>
      <div v-if="profileStore.success" class="profile-view__success">{{ profileStore.success }}</div>

      <div class="profile-view__grid">
        <section class="profile-view__panel">
          <div class="profile-view__panel-heading">
            <h3>3D Preview</h3>
            <span class="profile-view__panel-hint">Scroll to zoom, drag to rotate</span>
          </div>

          <div class="profile-view__preview">
            <span class="profile-view__preview-name">{{ authStore.profile?.name }}</span>
            <SkinPreview3D
              :skin-url="previewSkinUrl"
              :model-type="previewModelType"
            />
          </div>

          <div class="profile-view__panel-actions">
            <button
              class="btn"
              :disabled="!activeSlot || profileStore.applying"
              @click="handleApplyActive"
            >
              {{ profileStore.applying ? 'Applying...' : 'Apply to Minecraft' }}
            </button>
            <button
              class="btn btn--ghost"
              :disabled="!activeSlot || profileStore.exporting"
              @click="handleExportActive"
            >
              {{ profileStore.exporting ? 'Exporting...' : 'Export PNG' }}
            </button>
          </div>
        </section>

        <section class="profile-view__panel">
          <div class="profile-view__panel-header">
            <h3>Skin Slots</h3>
            <button
              class="btn"
              :disabled="profileStore.skinSlots.length >= 5 || profileStore.importing"
              @click="importModalOpen = true"
            >
              Add Skin
            </button>
          </div>

          <div v-if="profileStore.loading" class="pixel-loader">
            Loading Slots
            <span class="pixel-loader__dots">
              <span class="pixel-loader__dot"></span>
              <span class="pixel-loader__dot"></span>
              <span class="pixel-loader__dot"></span>
            </span>
          </div>

          <div v-else-if="profileStore.skinSlots.length === 0" class="profile-view__empty">
            No skin slots saved yet.
          </div>

          <div v-else class="profile-view__slot-list">
            <SkinSlotCard
              v-for="slot in profileStore.skinSlots"
              :key="slot.id"
              :slot="slot"
              :active="slot.id === activeSlot?.id"
              :preview-url="slot.data_url || mcHeadsSkinUrl"
              @select="handleSelectSlot(slot.id)"
              @export="handleExportSlot(slot.id)"
              @delete="confirmDelete(slot.id)"
              @drag-start="draggedSlotId = slot.id"
              @drop="handleDrop(slot.id)"
            />
          </div>
        </section>
      </div>
    </template>

    <SkinImportModal
      v-if="importModalOpen"
      :importing="profileStore.importing"
      :error="profileStore.error"
      @close="importModalOpen = false"
      @import-file="handleImportFile"
      @import-url="handleImportUrl"
    />
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import SkinPreview3D from '@/components/SkinPreview3D.vue'
import SkinSlotCard from '@/components/SkinSlotCard.vue'
import SkinImportModal from '@/components/SkinImportModal.vue'
import { useAuthStore } from '@/stores/auth'
import { useProfileStore } from '@/stores/profile'
import { useNotificationsStore } from '@/stores/notifications'
import type { SkinModelType } from '@/types/profile'

const authStore = useAuthStore()
const profileStore = useProfileStore()
const notifications = useNotificationsStore()
const importModalOpen = ref(false)
const draggedSlotId = ref<string | null>(null)

const activeSlot = computed(() => profileStore.activeSlot)

const mcHeadsSkinUrl = computed(() =>
  authStore.profile?.id
    ? `https://mc-heads.net/skin/${authStore.profile.id}`
    : 'https://mc-heads.net/skin/MHF_Steve'
)

const previewSkinUrl = computed(
  () => profileStore.previewUrl || profileStore.currentPreviewUrl || mcHeadsSkinUrl.value
)

const previewModelType = computed(
  () => activeSlot.value?.model_type || profileStore.currentModelType
)

async function loadProfileData() {
  await authStore.check()

  if (authStore.profile) {
    await profileStore.load(authStore.profile.id)
  }
}

function copyUuid() {
  if (authStore.profile?.id) {
    navigator.clipboard.writeText(authStore.profile.id)
  }
}

async function refreshProfile() {
  await authStore.check()
  await loadProfileData()
}

function handleImportFile(payload: { file: File; name: string; modelType: SkinModelType }) {
  profileStore
    .importFromFile(payload.file, payload.name, payload.modelType)
    .then(() => {
      importModalOpen.value = false
    })
}

function handleImportUrl(payload: { url: string; name: string; modelType: SkinModelType }) {
  profileStore
    .importFromUrl(payload.url, payload.name, payload.modelType)
    .then(() => {
      importModalOpen.value = false
    })
}

async function handleApplyActive() {
  if (!activeSlot.value) return
  await profileStore.applyActive()
}

async function handleExportSlot(slotId: string) {
  const savedPath = await profileStore.exportSlotToDownloads(slotId)
  if (savedPath) {
    notifications.notifySuccess('Skin exported', `Saved to ${savedPath}`, 7000)
  } else if (profileStore.error) {
    notifications.notifyError('Export failed', profileStore.error)
  }
}

async function handleExportActive() {
  if (activeSlot.value) {
    await handleExportSlot(activeSlot.value.id)
  }
}

function confirmDelete(slotId: string) {
  if (window.confirm('Remove this skin slot?')) {
    profileStore.removeSlot(slotId)
  }
}

async function handleSelectSlot(slotId: string) {
  await profileStore.setActive(slotId)
}

function handleDrop(targetSlotId: string) {
  if (!draggedSlotId.value || draggedSlotId.value === targetSlotId) return

  const current = [...profileStore.skinSlots]
  const dragIndex = current.findIndex((slot) => slot.id === draggedSlotId.value)
  const targetIndex = current.findIndex((slot) => slot.id === targetSlotId)

  if (dragIndex < 0 || targetIndex < 0) return

  const [moved] = current.splice(dragIndex, 1)
  current.splice(targetIndex, 0, moved)

  const orderedIds = current.map((slot) => slot.id)
  profileStore.reorder(orderedIds)
  draggedSlotId.value = null
}

onMounted(loadProfileData)
</script>

<style scoped lang="scss">
.profile-view {
  display: flex;
  flex-direction: column;
  gap: 1.5rem;
}

.profile-view__header {
  margin-bottom: 1rem;
}

.profile-view__title {
  font-size: 2rem;
  margin: 0;
}

.profile-view__auth {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 1rem;
  padding: 2rem;
  background-color: var(--bg-secondary);
  border: 2px solid var(--border-color);
  border-radius: var(--edge-radius);
}

.profile-view__error {
  padding: 1rem;
  background-color: #e74c3c;
  color: white;
  border: 2px solid #c0392b;
  border-radius: var(--edge-radius);
}

.profile-view__success {
  padding: 1rem;
  background-color: #2ecc71;
  color: white;
  border: 2px solid #27ae60;
  border-radius: var(--edge-radius);
}

.profile-card {
  background-color: var(--bg-secondary);
  border: 2px solid var(--border-color);
  border-radius: var(--edge-radius);
  padding: 1.5rem;
}

.profile-card__body {
  display: flex;
  align-items: center;
  gap: 1.5rem;
  flex-wrap: wrap;
}

.profile-card__avatar {
  width: 72px;
  height: 72px;
  flex-shrink: 0;
  border-radius: var(--edge-radius);
  border: 2px solid var(--border-color);
  image-rendering: pixelated;
}

.profile-card__info {
  flex: 1;
  min-width: 240px;
}

.profile-card__label,
.profile-card__name,
.profile-card__uuid {
  display: block;
}

.profile-card__label {
  font-family: var(--font-pixel);
  font-size: 0.6rem;
  text-transform: uppercase;
  color: var(--text-secondary);
  margin-bottom: 0.35rem;
}

.profile-card__uuid-label {
  margin-top: 1rem;
}

.profile-card__name {
  font-size: 1.5rem;
  color: var(--text-primary);
}

.profile-card__uuid {
  font-family: 'Courier New', monospace;
  color: var(--text-secondary);
  word-break: break-all;
}

.profile-card__actions {
  display: flex;
  flex-wrap: wrap;
  gap: 0.5rem;
  justify-content: flex-end;
}

.profile-view__grid {
  display: grid;
  grid-template-columns: minmax(320px, 1fr) minmax(360px, 1.4fr);
  gap: 1.5rem;
  align-items: start;
}

.profile-view__panel {
  background-color: var(--bg-secondary);
  border: 2px solid var(--border-color);
  border-radius: var(--edge-radius);
  padding: 1.5rem;
}

.profile-view__panel-heading {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 1rem;
  margin-bottom: 1rem;
}

.profile-view__panel-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 1rem;
  margin-bottom: 1rem;
}

.profile-view__panel h3 {
  font-size: 1.2rem;
  margin: 0;
}

.profile-view__panel-heading h3 {
  margin: 0;
}

.profile-view__panel-hint {
  font-size: 0.75rem;
  color: var(--text-secondary);
}

.profile-view__preview {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 0.75rem;
}

.profile-view__preview-name {
  font-family: var(--font-pixel);
  font-size: 0.75rem;
  text-transform: uppercase;
  color: var(--text-primary);
  background-color: var(--bg-tertiary);
  padding: 0.5rem 1rem;
  border: 2px solid var(--border-color);
  border-radius: var(--edge-radius);
}

.profile-view__empty {
  padding: 2rem;
  text-align: center;
  color: var(--text-secondary);
  border: 2px dashed var(--border-color);
  border-radius: var(--edge-radius);
}

.profile-view__panel-actions {
  display: flex;
  flex-wrap: wrap;
  justify-content: center;
  gap: 0.75rem;
  margin-top: 1rem;
}

.profile-view__slot-list {
  display: flex;
  flex-direction: column;
  gap: 0.75rem;
}

@media (max-width: 1000px) {
  .profile-view__grid {
    grid-template-columns: 1fr;
  }

  .profile-card__actions {
    justify-content: flex-start;
  }
}
</style>