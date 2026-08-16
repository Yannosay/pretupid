<template>
  <div class="home">
    <div class="home__hero">
      <img
        src="@/assets/branding/ptd_logo_main.webp"
        alt="Pretupid Launcher"
        class="home__logo"
      />
      <h1 class="home__title">Pretupid Launcher</h1>
      <p class="home__tagline text-secondary">
        The awesome and super fast easy-to-use Minecraft Launcher
      </p>
    </div>

    <div class="home__actions">
      <RouterLink to="/library" class="home__action-card">
        <span class="home__action-icon">📚</span>
        <h3 class="home__action-title">Library</h3>
        <p class="home__action-desc text-secondary">
          Manage your Minecraft instances and mods easily.
        </p>
      </RouterLink>

      <button class="home__action-card" @click="$emit('open-settings')">
        <span class="home__action-icon">⚙️</span>
        <h3 class="home__action-title">Settings</h3>
        <p class="home__action-desc text-secondary">
          Customize theme, colors, and corner style and make this launcher your own!
        </p>
      </button>
    </div>
  </div>
</template>

<script setup lang="ts">
import { RouterLink } from 'vue-router'

defineEmits<{
  (e: 'open-settings'): void
}>()
</script>

<style scoped lang="scss">
.home {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  min-height: 70vh;
  gap: 2.5rem;
}

.home__hero {
  display: flex;
  flex-direction: column;
  align-items: center;
  text-align: center;
  gap: 1rem;
}

.home__logo {
  width: 96px;
  height: 96px;
  border-radius: var(--edge-radius);
  border: 2px solid var(--border-color);
}

.home__title {
  font-size: 2.5rem;
}

.home__tagline {
  font-size: 1.2rem;
}

.home__actions {
  display: flex;
  gap: 1.5rem;
  flex-wrap: wrap;
  justify-content: center;
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
</style>