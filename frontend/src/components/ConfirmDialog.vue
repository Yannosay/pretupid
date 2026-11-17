<template>
  <div class="modal-overlay" v-backdrop-close="() => emit('cancel')" @keydown.esc="emit('cancel')">
    <div class="modal modal--narrow" role="alertdialog" aria-modal="true" :aria-label="title">
      <div class="modal__header">
        <h3 class="modal__title">{{ title }}</h3>
        <button class="modal__close" aria-label="Close" @click="emit('cancel')">×</button>
      </div>
      <div class="modal__body">
        <p class="confirm__message">{{ message }}</p>
        <slot />
      </div>
      <div class="modal__footer">
        <button class="btn btn--ghost" @click="emit('cancel')">Cancel</button>
        <button ref="confirmButton" class="btn" :class="danger ? 'btn--danger' : ''" :disabled="busy" @click="emit('confirm')">
          {{ busy ? 'Working' : confirmLabel }}
        </button>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { vBackdropClose } from '@ui/backdropClose'
import { onMounted, ref } from 'vue'

withDefaults(
  defineProps<{ title: string; message: string; confirmLabel?: string; danger?: boolean; busy?: boolean }>(),
  { confirmLabel: 'Confirm', danger: false, busy: false }
)

const emit = defineEmits<{ (e: 'confirm'): void; (e: 'cancel'): void }>()
const confirmButton = ref<HTMLButtonElement | null>(null)

onMounted(() => confirmButton.value?.focus())
</script>

<style scoped>
.confirm__message {
  margin: 0;
  line-height: 1.3;
}
</style>
