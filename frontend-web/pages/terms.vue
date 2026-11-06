<template>
  <div class="legal-page">
    <h1 class="legal-page__title">Terms of Service</h1>
    <section class="legal-page__section">
      <h2>Acceptance of Terms</h2>
      <p>
        By downloading, installing, or using Pretupid Launcher, you agree to be bound by these Terms of Service.
        If you do not agree, do not use the launcher.
      </p>
    </section>
    <section class="legal-page__section">
      <h2>License</h2>
      <p>
        Pretupid Launcher is licensed under the GNU General Public License version 2.0.
        You may use, modify, and distribute the software under the terms of that license.
      </p>
    </section>
    <section class="legal-page__section">
      <h2>Acceptable Use</h2>
      <ul>
        <li>You may not use the launcher for any illegal or unauthorized purpose.</li>
        <li>You must comply with the terms of service of Minecraft and related services.</li>
        <li>You may not distribute modified versions that violate the GNU AGPL-3.0 license.</li>
      </ul>
    </section>
    <section class="legal-page__section">
      <h2>Disclaimer</h2>
      <p>
        Pretupid Launcher is not an official Minecraft service. It is not approved by or associated with Mojang or Microsoft.
        The software is provided "as is" without warranty of any kind.
      </p>
    </section>
    <section class="legal-page__section">
      <h2>Limitation of Liability</h2>
      <p>
        In no event shall Yannosay Productions be liable for any damages arising from the use of Pretupid Launcher.
      </p>
    </section>
  </div>
</template>

<style scoped lang="scss">
@use 'design-system/mixins' as *;

.legal-page__title {
  font-family: var(--font-pixel);
  font-size: 2.5rem;
  text-align: center;
  margin-bottom: 2rem;
  text-transform: uppercase;
}

.legal-page__section {
  background-color: var(--bg-secondary);
  border: 2px solid var(--border-color);
  border-radius: var(--edge-radius);
  padding: 1.5rem;
  margin-bottom: 1.5rem;
}

.legal-page__section h2 {
  font-family: var(--font-pixel);
  font-size: 1.3rem;
  text-transform: uppercase;
  margin-bottom: 1rem;
}

.legal-page__section p,
.legal-page__section li {
  font-size: 1.1rem;
  color: var(--text-secondary);
  line-height: 1.5;
}

.legal-page__section ul {
  list-style: square;
  padding-left: 1.5rem;
}
</style>