<template>
  <div class="error-page">
    <h1 class="error-page__title">404</h1>
    <p class="error-page__text">Page not found.</p>
    <NuxtLink to="/" class="btn">Back to Home</NuxtLink>
  </div>
</template>

<style scoped lang="scss">
@use '@/assets/styles/_mixins' as *;

.error-page {
  min-height: 60vh;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 1rem;
  text-align: center;
}

.error-page__title {
  font-family: var(--font-pixel);
  font-size: 5rem;
  color: var(--accent);
}

.error-page__text {
  font-size: 1.5rem;
  color: var(--text-secondary);
}
</style>