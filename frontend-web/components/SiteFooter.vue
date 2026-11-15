<template>
  <footer class="footer">
    <div class="footer__inner">
      <div class="footer__brand">
        <div class="footer__brand-row">
          <img :src="logo" alt="" width="32" height="32" class="footer__logo" />
          <span class="footer__brand-name">Pretupid Launcher</span>
        </div>
        <p class="footer__tagline">
          A fast, safe Minecraft launcher and client.
        </p>
        <div class="footer__cta">
          <NuxtLink to="/download" class="btn btn--small">Download</NuxtLink>
          <button type="button" class="btn btn--ghost btn--small" @click="openLauncher">
            Open Launcher
          </button>
        </div>
      </div>

      <div class="footer__columns">
        <div class="footer__col">
          <h4 class="footer__title">Product</h4>
          <NuxtLink to="/" class="footer__link">Home</NuxtLink>
          <NuxtLink to="/features" class="footer__link">Features</NuxtLink>
          <NuxtLink to="/security" class="footer__link">Security</NuxtLink>
          <NuxtLink to="/download" class="footer__link">Download</NuxtLink>
        </div>

        <div class="footer__col">
          <h4 class="footer__title">Company</h4>
          <NuxtLink to="/about" class="footer__link">About</NuxtLink>
          <NuxtLink to="/contact" class="footer__link">Contact</NuxtLink>
        </div>

        <div class="footer__col">
          <h4 class="footer__title">Legal</h4>
          <NuxtLink to="/privacy" class="footer__link">Privacy</NuxtLink>
          <NuxtLink to="/terms" class="footer__link">Terms</NuxtLink>
        </div>
      </div>
    </div>

    <div class="footer__bottom">
      <p class="footer__copy">
        Copyright {{ year }} Yannosay Productions. Licensed under AGPL-3.0.
      </p>
      <p class="footer__disclaimer">
        NOT AN OFFICIAL MINECRAFT SERVICE. NOT APPROVED BY OR ASSOCIATED WITH MOJANG OR MICROSOFT.
      </p>
    </div>
  </footer>
</template>

<script setup lang="ts">
import logo from '@shared/branding/logo.png'
import { useLauncherBridge } from '~/composables/useLauncherBridge'

const { openLauncher } = useLauncherBridge()
const year = new Date().getFullYear()
</script>

<style scoped lang="scss">
@use 'design-system/mixins' as *;

.footer {
  background-color: var(--bg-secondary);
  border-top: 2px solid var(--border-color);
  margin-top: 4rem;
}

.footer__inner {
  max-width: 1200px;
  margin: 0 auto;
  padding: 3rem 2rem 2rem;
  display: grid;
  grid-template-columns: minmax(0, 1.2fr) minmax(0, 1.6fr);
  gap: 3rem;
}

.footer__brand-row {
  display: flex;
  align-items: center;
  gap: 0.75rem;
}

.footer__logo {
  width: 40px;
  height: 40px;
  border-radius: var(--edge-radius);
  border: 2px solid var(--border-color);
}

.footer__brand-name {
  font-family: var(--font-pixel);
  font-weight: 700;
  font-size: 1rem;
  color: var(--accent);
}

.footer__tagline {
  margin: 1rem 0 1.5rem;
  font-size: 1.05rem;
  color: var(--text-secondary);
  max-width: 24rem;
}

.footer__cta {
  display: flex;
  flex-wrap: wrap;
  gap: 0.6rem;
}

.footer__columns {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 2rem;
}

.footer__col {
  display: flex;
  flex-direction: column;
  gap: 0.5rem;
}

.footer__title {
  font-family: var(--font-body);
  font-weight: 600;
  font-size: 0.85rem;
  text-transform: uppercase;
  letter-spacing: 0.05em;
  color: var(--text-secondary);
  margin: 0 0 0.25rem;
}

.footer__link {
  font-family: var(--font-body);
  font-size: 1.05rem;
  color: var(--text-primary);
  text-decoration: none;
  transition: color var(--transition-fast);
}

.footer__link:hover {
  color: var(--accent);
}

.footer__bottom {
  border-top: 1px solid var(--border-color);
  padding: 1.25rem 2rem;
  max-width: 1200px;
  margin: 0 auto;
  display: flex;
  flex-direction: column;
  gap: 0.35rem;
  text-align: center;
}

.footer__copy {
  margin: 0;
  font-size: 0.9rem;
  color: var(--text-secondary);
}

.footer__disclaimer {
  margin: 0;
  font-family: var(--font-body);
  font-weight: 600;
  font-size: 0.55rem;
  letter-spacing: 0.05em;
  color: var(--text-secondary);
  opacity: 0.85;
}

@media (max-width: 800px) {
  .footer__inner {
    grid-template-columns: 1fr;
    gap: 2rem;
    padding: 2rem 1.25rem 1.5rem;
  }

  .footer__columns {
    grid-template-columns: 1fr 1fr;
  }
}
</style>