<template>
  <div class="faq">
    <div v-for="(item, index) in items" :key="index" class="faq__item">
      <button class="faq__question" :aria-expanded="openIndex === index" @click="openIndex = openIndex === index ? -1 : index">
        {{ item.question }}
        <span class="faq__icon">{{ openIndex === index ? '−' : '+' }}</span>
      </button>
      <div v-if="openIndex === index" class="faq__answer">
        {{ item.answer }}
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
defineProps<{
  items: Array<{ question: string; answer: string }>
}>()

const openIndex = ref(-1)
</script>

<style scoped lang="scss">
@use 'design-system/mixins' as *;

.faq {
  display: flex;
  flex-direction: column;
  gap: 0.75rem;
}

.faq__item {
  background-color: var(--bg-secondary);
  border: 2px solid var(--border-color);
  border-radius: var(--edge-radius);
  overflow: hidden;
}

.faq__question {
  width: 100%;
  text-align: left;
  padding: 1rem;
  font-family: var(--font-body);
  font-size: 1.2rem;
  color: var(--text-primary);
  background: none;
  border: none;
  cursor: pointer;
  display: flex;
  justify-content: space-between;
  align-items: center;
  transition: background-color var(--transition-fast);
}

.faq__question:hover {
  background-color: var(--bg-tertiary);
}

.faq__icon {
  font-size: 1.5rem;
  color: var(--accent);
}

.faq__answer {
  padding: 1rem;
  border-top: 1px solid var(--border-color);
  font-size: 1rem;
  color: var(--text-secondary);
  line-height: 1.4;
}
</style>