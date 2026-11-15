<template>
  <div v-if="splash.visible" class="splash-overlay">
    <div class="splash-overlay__card">
      <img
        src="@shared/branding/pretupid_logo_512x.png"
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
  background:
    radial-gradient(600px 400px at 50% 40%, color-mix(in srgb, var(--accent) 14%, transparent), transparent 70%),
    var(--bg-primary);
}

.splash-overlay__card {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 1.1rem;
  width: min(360px, 90vw);
}

.splash-overlay__logo {
  width: 88px;
  height: 88px;
  filter: drop-shadow(0 10px 30px color-mix(in srgb, var(--accent) 40%, transparent));
  animation: splash-float 2.4s ease-in-out infinite;
}

@keyframes splash-float {
  50% {
    transform: translateY(-4px);
  }
}

@media (prefers-reduced-motion: reduce) {
  .splash-overlay__logo {
    animation: none;
  }
}

.splash-overlay__title {
  font-family: var(--font-pixel);
  font-size: 1.4rem;
  color: var(--text-primary);
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