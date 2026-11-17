<template>
  <div class="profile-view">
    <header class="profile-view__header">
      <h1 class="profile-view__title">Skins</h1>
      <p class="profile-view__subtitle">Pick how you look in game. Changes reach every server you join.</p>
    </header>

    <div v-if="!authStore.authenticated" class="profile-view__auth">
      <p class="text-secondary">Sign in with your Microsoft account to manage skins and capes.</p>
      <button class="btn" :disabled="authStore.loading" @click="authStore.login">
        {{ authStore.loading ? 'Signing in...' : 'Sign in with Microsoft' }}
      </button>
    </div>

    <template v-else>
      <div v-if="profileStore.error" class="profile-view__notice profile-view__notice--danger">{{ profileStore.error }}</div>
      <div v-if="profileStore.success" class="profile-view__notice profile-view__notice--success">{{ profileStore.success }}</div>

      <div class="profile-view__grid">
        <section class="profile-stage">
          <SkinPreview3D
            :skin-url="previewSkinUrl"
            :cape-url="profileStore.activeCape?.data_url ?? null"
            :width="320"
            :height="440"
          >
            <template #backdrop>
              <PixelScenery
                :seed="authStore.profile?.id ?? 'skins'"
                :moving="settingsStore.settings.sceneryMotion"
                :fade="false"
                class="profile-stage__scenery"
              />
              <span class="profile-stage__ground" />
              <span class="profile-stage__nametag">{{ authStore.profile?.name }}</span>
            </template>
          </SkinPreview3D>

          <div class="profile-stage__actions">
            <button class="btn" :disabled="!activeSlot || profileStore.applying" @click="handleApplyActive">
              {{ profileStore.applying ? 'Applying...' : 'Apply to Minecraft' }}
            </button>
            <button class="btn btn--ghost" :disabled="!activeSlot || profileStore.exporting" @click="handleExportActive">
              <AppIcon name="download" :size="14" />
              {{ profileStore.exporting ? 'Exporting...' : 'Export PNG' }}
            </button>
          </div>
        </section>

        <div class="profile-view__side">
          <section class="profile-card">
            <img
              class="profile-card__avatar"
              :src="`https://mc-heads.net/avatar/${authStore.profile?.id}/100`"
              :alt="authStore.profile?.name"
            />
            <div class="profile-card__info">
              <span class="profile-card__name">{{ authStore.profile?.name }}</span>
              <button class="profile-card__uuid" :title="copied ? 'Copied' : 'Copy UUID'" @click="copyUuid">
                <span>{{ authStore.profile?.id }}</span>
                <AppIcon :name="copied ? 'check' : 'copy'" :size="12" />
              </button>
            </div>
            <button class="btn btn--ghost btn--small" @click="refreshProfile">Refresh</button>
          </section>

          <section class="profile-view__panel">
            <div class="profile-view__panel-header">
              <div>
                <h3>Wardrobe</h3>
                <span class="profile-view__panel-hint">{{ profileStore.skinSlots.length }} of {{ MAX_SLOTS }} slots · drag to reorder</span>
              </div>
            </div>

            <div v-if="profileStore.loading" class="pixel-loader">
              Loading skins
              <span class="pixel-loader__dots">
                <span class="pixel-loader__dot"></span>
                <span class="pixel-loader__dot"></span>
                <span class="pixel-loader__dot"></span>
              </span>
            </div>

            <div v-else class="wardrobe">
              <SkinSlotCard
                v-for="slot in profileStore.skinSlots"
                :key="slot.id"
                :slot="slot"
                :active="slot.id === activeSlot?.id"
                :preview-url="slot.data_url || mcHeadsSkinUrl"
                @select="handleSelectSlot(slot.id)"
                @export="handleExportSlot(slot.id)"
                @delete="pendingDelete = slot.id"
                @drag-start="draggedSlotId = slot.id"
                @drop="handleDrop(slot.id)"
              />
              <button
                v-if="profileStore.skinSlots.length < MAX_SLOTS"
                type="button"
                class="wardrobe__add"
                :disabled="profileStore.importing"
                @click="importModalOpen = true"
              >
                <AppIcon name="plus" :size="20" />
                <span>Add skin</span>
              </button>
            </div>
          </section>

          <section class="profile-view__panel">
            <div class="profile-view__panel-header">
              <div>
                <h3>Minecraft Capes</h3>
                <span class="profile-view__panel-hint">Capes on your Microsoft account. Everyone sees the one you equip.</span>
              </div>
            </div>

            <div v-if="profileStore.capeError" class="profile-view__notice profile-view__notice--danger">{{ profileStore.capeError }}</div>

            <div v-if="profileStore.capesLoading && !profileStore.capesLoaded" class="pixel-loader">
              Loading capes
              <span class="pixel-loader__dots">
                <span class="pixel-loader__dot"></span>
                <span class="pixel-loader__dot"></span>
                <span class="pixel-loader__dot"></span>
              </span>
            </div>

            <div v-else-if="profileStore.capesLoaded" class="cape-grid">
              <button
                type="button"
                class="cape-card"
                :class="{ 'cape-card--active': !profileStore.activeCape }"
                :disabled="profileStore.capeSaving"
                @click="handleEquipCape(null)"
              >
                <span class="cape-card__art cape-card__art--empty"><AppIcon name="x" :size="18" /></span>
                <span class="cape-card__name">No cape</span>
              </button>
              <button
                v-for="cape in profileStore.capes"
                :key="cape.id"
                type="button"
                class="cape-card"
                :class="{ 'cape-card--active': cape.active }"
                :disabled="profileStore.capeSaving"
                @click="handleEquipCape(cape.id)"
              >
                <span
                  class="cape-card__art"
                  :style="cape.data_url ? { backgroundImage: `url(${cape.data_url})` } : undefined"
                ></span>
                <span class="cape-card__name">{{ cape.alias }}</span>
              </button>
              <p v-if="profileStore.capes.length === 0" class="cape-grid__empty">
                This account has no Minecraft capes yet.
              </p>
            </div>
          </section>
        </div>
      </div>
    </template>

    <SkinImportModal
      v-if="importModalOpen"
      :importing="profileStore.importing"
      :error="profileStore.error"
      @close="importModalOpen = false"
      @import-file="handleImportFile"
      @import-url="handleImportUrl"
      @import-username="handleImportUsername"
    />

    <ConfirmDialog
      v-if="pendingDelete"
      title="Remove skin"
      :message="`Remove “${pendingDeleteName}” from your wardrobe? You can add it again later.`"
      confirm-label="Remove"
      danger
      @cancel="pendingDelete = null"
      @confirm="handleDelete"
    />
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import SkinPreview3D from '@/components/SkinPreview3D.vue'
import SkinSlotCard from '@/components/SkinSlotCard.vue'
import SkinImportModal from '@/components/SkinImportModal.vue'
import ConfirmDialog from '@/components/ConfirmDialog.vue'
import AppIcon from '@/components/AppIcon.vue'
import PixelScenery from '@ui/PixelScenery.vue'
import { useAuthStore } from '@/stores/auth'
import { useProfileStore } from '@/stores/profile'
import { useNotificationsStore } from '@/stores/notifications'
import { useSettingsStore } from '@/stores/settings'
import type { SkinModelType } from '@/types/profile'

const MAX_SLOTS = 5

const authStore = useAuthStore()
const profileStore = useProfileStore()
const notifications = useNotificationsStore()
const settingsStore = useSettingsStore()
const importModalOpen = ref(false)
const draggedSlotId = ref<string | null>(null)
const pendingDelete = ref<string | null>(null)
const copied = ref(false)

const activeSlot = computed(() => profileStore.activeSlot)

const pendingDeleteName = computed(
  () => profileStore.skinSlots.find((slot) => slot.id === pendingDelete.value)?.name ?? 'this skin'
)

const mcHeadsSkinUrl = computed(() =>
  authStore.profile?.id
    ? `https://mc-heads.net/skin/${authStore.profile.id}`
    : 'https://mc-heads.net/skin/MHF_Steve'
)

const previewSkinUrl = computed(
  () => profileStore.previewUrl || profileStore.currentPreviewUrl || mcHeadsSkinUrl.value
)

async function loadProfileData() {
  await authStore.check()

  if (authStore.profile) {
    await Promise.all([profileStore.load(authStore.profile.id), profileStore.loadCapes()])
  }
}

async function copyUuid() {
  if (!authStore.profile?.id) return
  try {
    await navigator.clipboard.writeText(authStore.profile.id)
    copied.value = true
    window.setTimeout(() => (copied.value = false), 1500)
  } catch {
    notifications.notifyError('Copy failed', 'The clipboard is not available right now.')
  }
}

async function refreshProfile() {
  await loadProfileData()
}

function finishImport() {
  if (!profileStore.error) {
    importModalOpen.value = false
    notifications.notifySuccess('Skin added', 'It is now in your wardrobe.')
  }
}

async function handleImportFile(payload: { file: File; name: string; modelType: SkinModelType }) {
  await profileStore.importFromFile(payload.file, payload.name, payload.modelType)
  finishImport()
}

async function handleImportUrl(payload: { url: string; name: string; modelType: SkinModelType }) {
  await profileStore.importFromUrl(payload.url, payload.name, payload.modelType)
  finishImport()
}

async function handleImportUsername(payload: { username: string; name: string }) {
  await profileStore.importFromUsername(payload.username, payload.name)
  finishImport()
}

async function handleEquipCape(capeId: string | null) {
  const current = profileStore.activeCape?.id ?? null
  if (current === capeId) return
  const saved = await profileStore.equipCape(capeId)
  if (saved) {
    notifications.notifySuccess(
      capeId ? 'Cape equipped' : 'Cape hidden',
      'Players see the change after they reload your skin.'
    )
  }
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

async function handleDelete() {
  const slotId = pendingDelete.value
  pendingDelete.value = null
  if (slotId) await profileStore.removeSlot(slotId)
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
  gap: 1.25rem;
}

.profile-view__title {
  font-size: 2rem;
  margin: 0;
}

.profile-view__subtitle {
  margin: 0.35rem 0 0;
  color: var(--text-secondary);
}

.profile-view__auth {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 1rem;
  padding: 2rem;
  background-color: var(--bg-secondary);
  border: 1px solid var(--border-color);
  border-radius: var(--edge-radius);
}

.profile-view__notice {
  padding: 0.75rem 1rem;
  border: 1px solid;
  border-radius: var(--edge-radius);

  &--danger {
    color: var(--danger);
    border-color: var(--danger);
    background-color: color-mix(in srgb, var(--danger) 10%, transparent);
  }

  &--success {
    color: var(--success);
    border-color: var(--success);
    background-color: color-mix(in srgb, var(--success) 10%, transparent);
  }
}

.profile-view__grid {
  display: grid;
  grid-template-columns: 362px minmax(0, 1fr);
  gap: 1.25rem;
  align-items: start;
}

.profile-stage {
  position: sticky;
  top: 0;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 1rem;
  padding: 1.25rem 1.25rem 1rem;
  background-color: var(--bg-secondary);
  border: 1px solid var(--border-color);
  border-radius: var(--edge-radius);

  :deep(.skin-preview__stage) {
    background-color: #0b1020;
    border: 2px solid var(--border-strong);
  }
}

.profile-stage__scenery {
  position: absolute;
  inset: 0;
  z-index: 0;
}

.profile-stage__ground {
  position: absolute;
  left: 0;
  right: 0;
  bottom: 0;
  height: 86px;
  z-index: 0;
  background:
    linear-gradient(180deg, #6aa84f 0 8px, #4f8a37 8px 14px, transparent 14px),
    repeating-linear-gradient(90deg, #7a5638 0 16px, #6a4a30 16px 32px);
  box-shadow: inset 0 2px 0 rgba(255, 255, 255, 0.18);
}

.profile-stage__nametag {
  position: absolute;
  top: 16px;
  left: 50%;
  z-index: 2;
  transform: translateX(-50%);
  padding: 0.2rem 0.55rem;
  font-family: var(--font-pixel);
  font-size: 0.8rem;
  color: #ffffff;
  white-space: nowrap;
  background-color: rgba(0, 0, 0, 0.4);
  pointer-events: none;
}

.profile-stage__actions {
  display: flex;
  flex-wrap: wrap;
  justify-content: center;
  gap: 0.6rem;
}

.profile-view__side {
  display: flex;
  flex-direction: column;
  gap: 1.25rem;
  min-width: 0;
}

.profile-card {
  display: flex;
  align-items: center;
  gap: 1rem;
  padding: 1rem 1.25rem;
  background-color: var(--bg-secondary);
  border: 1px solid var(--border-color);
  border-radius: var(--edge-radius);
}

.profile-card__avatar {
  width: 52px;
  height: 52px;
  flex-shrink: 0;
  border: 2px solid var(--border-strong);
  image-rendering: pixelated;
}

.profile-card__info {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 0.3rem;
}

.profile-card__name {
  font-family: var(--font-pixel);
  font-size: 1.15rem;
  color: var(--text-primary);
}

.profile-card__uuid {
  display: inline-flex;
  align-items: center;
  gap: 0.4rem;
  align-self: flex-start;
  max-width: 100%;
  padding: 0;
  font-family: var(--font-mono);
  font-size: 0.78rem;
  color: var(--text-secondary);
  background: none;
  border: none;
  cursor: pointer;

  span {
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  &:hover {
    color: var(--accent);
  }
}

.profile-view__panel {
  background-color: var(--bg-secondary);
  border: 1px solid var(--border-color);
  border-radius: var(--edge-radius);
  padding: 1.25rem;
}

.profile-view__panel-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 1rem;
  margin-bottom: 1rem;
}

.profile-view__panel h3 {
  font-size: 1.1rem;
  margin: 0 0 0.25rem;
}

.profile-view__panel-hint {
  font-size: 0.85rem;
  color: var(--text-secondary);
}

.wardrobe {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(150px, 1fr));
  gap: 0.75rem;
}

.wardrobe__add {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 0.5rem;
  min-height: 188px;
  font-family: var(--font-pixel);
  font-size: 0.8rem;
  color: var(--text-secondary);
  background-color: transparent;
  border: 2px dashed var(--border-strong);
  border-radius: var(--edge-radius);
  cursor: pointer;
  transition: color var(--transition-fast), border-color var(--transition-fast), background-color var(--transition-fast);

  &:hover:not(:disabled) {
    color: var(--accent);
    border-color: var(--accent);
    background-color: color-mix(in srgb, var(--accent) 8%, transparent);
  }

  &:disabled {
    opacity: 0.5;
    cursor: progress;
  }
}

.cape-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(96px, 1fr));
  gap: 0.75rem;
}

.cape-grid__empty {
  grid-column: 1 / -1;
  margin: 0;
  color: var(--text-secondary);
}

.cape-card {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 0.6rem;
  padding: 0.85rem 0.5rem 0.7rem;
  background-color: var(--bg-tertiary);
  border: 1px solid var(--border-color);
  border-radius: var(--edge-radius);
  color: var(--text-primary);
  cursor: pointer;
  transition: border-color 0.15s ease, transform 0.15s ease;

  &:hover:not(:disabled) {
    border-color: var(--accent);
    transform: translateY(-2px);
  }

  &:disabled {
    cursor: progress;
    opacity: 0.7;
  }

  &--active {
    border-color: var(--accent);
    box-shadow: inset 0 0 0 1px var(--accent), 0 0 18px color-mix(in srgb, var(--accent) 30%, transparent);
  }
}

.cape-card__art {
  width: 60px;
  height: 96px;
  background-color: var(--well-bg);
  background-repeat: no-repeat;
  background-size: 384px 192px;
  background-position: -6px -6px;
  image-rendering: pixelated;
  border: 1px solid var(--border-color);

  &--empty {
    display: flex;
    align-items: center;
    justify-content: center;
    color: var(--text-dim);
  }
}

.cape-card__name {
  font-size: 0.85rem;
  text-align: center;
  line-height: 1.2;
}

@media (max-width: 1000px) {
  .profile-view__grid {
    grid-template-columns: 1fr;
  }

  .profile-stage {
    position: static;
  }
}
</style>
