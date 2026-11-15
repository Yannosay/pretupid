<template>
  <div v-if="splash.visible" class="splash-overlay">
    <div class="splash-overlay__card">
      <img
        src="@shared/branding/logo.png"
        alt="Pretupid Launcher"
        class="splash-overlay__logo"
      />

      <span class="splash-overlay__title">Pretupid</span>

      <div v-if="splash.determinate" class="splash-overlay__progress">
        <div class="splash-overlay__progress-track">
          <div
            class="splash-overlay__progress-fill"
            :style="{ width: `${splash.progress ?? 0}%` }"
          />
        </div>
        <span class="splash-overlay__progress-text">{{ splash.progress ?? 0 }}%</span>
      </div>

      <div v-else class="pixel-loader">
        {{ splash.message }}
        <span class="pixel-loader__dots">
          <span class="pixel-loader__dot"></span>
          <span class="pixel-loader__dot"></span>
          <span class="pixel-loader__dot"></span>
        </span>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { useSplashStore } from '@/stores/splash'

const splash = useSplashStore()
</script>

<style scoped lang="scss">
.splash-overlay {
  position: fixed;
  top: 0;
  left: 0;
  width: 100%;
  height: 100%;
  z-index: 10000;
  display: flex;
  align-items: center;
  justify-content: center;
  background-color: rgba(13, 13, 13, 0.92);
}

.splash-overlay__card {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 1.25rem;
  width: min(420px, 90vw);
  padding: 2rem;
  background-color: var(--bg-secondary);
  border: 1px solid var(--border-color);
  border-radius: var(--edge-radius);
  box-shadow: 8px 8px 0 0 var(--shadow-color);
}

.splash-overlay__logo {
  width: 96px;
  height: 96px;
  border-radius: var(--edge-radius);
  border: 1px solid var(--border-color);
}

.splash-overlay__title {
  font-family: var(--font-pixel);
  font-size: 1.4rem;
  color: var(--accent);
}

.splash-overlay__progress {
  display: flex;
  align-items: center;
  gap: 0.75rem;
  width: 100%;
}

.splash-overlay__progress-track {
  flex: 1;
  height: 20px;
  background-color: var(--bg-tertiary);
  border: 1px solid var(--border-color);
  border-radius: var(--edge-radius);
  overflow: hidden;
}

.splash-overlay__progress-fill {
  height: 100%;
  background-color: var(--accent);
  transition: width var(--transition-normal);
}

.splash-overlay__progress-text {
  font-family: var(--font-pixel);
  font-size: 0.85rem;
  color: var(--text-secondary);
  white-space: nowrap;
}
</style>