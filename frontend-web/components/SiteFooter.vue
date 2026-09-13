<template>
  <footer class="footer">
    <div class="footer__grid">
      <div class="footer__col">
        <h4 class="footer__title">Pretupid Launcher</h4>
        <p class="footer__text">The modern Minecraft launcher built with Tauri and Rust.</p>
      </div>
      <div class="footer__col">
        <h4 class="footer__title">Links</h4>
        <NuxtLink to="/" class="footer__link">Home</NuxtLink>
        <NuxtLink to="/#features" class="footer__link">Features</NuxtLink>
        <NuxtLink to="/download" class="footer__link">Download</NuxtLink>
        <NuxtLink to="/about" class="footer__link">About</NuxtLink>
      </div>
      <div class="footer__col">
        <h4 class="footer__title">Legal</h4>
        <NuxtLink to="/privacy" class="footer__link">Privacy Policy</NuxtLink>
        <NuxtLink to="/terms" class="footer__link">Terms of Service</NuxtLink>
      </div>
    </div>
    <div class="footer__bottom">
      <p>&copy; {{ new Date().getFullYear() }} Yannosay Productions. All rights reserved.</p>
      <p>Licensed under GNU 2.0.</p>
      <p class="footer__disclaimer">NOT AN OFFICIAL MINECRAFT SERVICE. NOT APPROVED BY OR ASSOCIATED WITH MOJANG OR MICROSOFT.</p>
    </div>
  </footer>
</template>

<style scoped lang="scss">
@use '@/assets/styles/_mixins' as *;

.footer {
  background-color: var(--bg-secondary);
  border-top: 2px solid var(--border-color);
  padding: 2rem;
  margin-top: 2rem;
}

.footer__grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(200px, 1fr));
  gap: 2rem;
  max-width: 1200px;
  margin: 0 auto;
}

.footer__title {
  font-family: var(--font-pixel);
  font-size: 0.8rem;
  text-transform: uppercase;
  margin-bottom: 0.75rem;
  color: var(--text-primary);
}

.footer__text {
  font-size: 1rem;
  color: var(--text-secondary);
}

.footer__col {
  display: flex;
  flex-direction: column;
  gap: 0.5rem;
}

.footer__link {
  font-family: var(--font-body);
  font-size: 1.1rem;
  color: var(--text-secondary);
  text-decoration: none;
  transition: color var(--transition-fast);
}

.footer__link:hover {
  color: var(--text-primary);
}

.footer__bottom {
  border-top: 1px solid var(--border-color);
  margin-top: 2rem;
  padding-top: 1rem;
  text-align: center;
  font-size: 0.9rem;
  color: var(--text-secondary);
  display: flex;
  flex-direction: column;
  gap: 0.25rem;
}

.footer__disclaimer {
  font-size: 0.7rem;
  letter-spacing: 0.05em;
  margin-top: 0.5rem;
}
</style>