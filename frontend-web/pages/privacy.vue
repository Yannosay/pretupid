<template>
  <div class="legal-page">
    <h1 class="legal-page__title">Privacy Policy</h1>
    <section class="legal-page__section">
      <h2>Introduction</h2>
      <p>
        This Privacy Policy explains how Pretupid Launcher collects, uses, and protects your information.
        By using the launcher, you agree to the collection and use of information in accordance with this policy.
      </p>
    </section>
    <section class="legal-page__section">
      <h2>Information We Collect</h2>
      <ul>
        <li>Authentication tokens required for Microsoft login, stored only on your device.</li>
        <li>Local instance, mod, playtime and settings data stored on your device.</li>
      </ul>
      <p>
        Pretupid Launcher has no telemetry or analytics. Nothing is sent to the Pretupid team, and we do not sell or
        share personal information.
      </p>
    </section>
    <section class="legal-page__section">
      <h2>Data Storage</h2>
      <p>
        All personal data is stored locally on your device in the Pretupid Launcher data directory.
        Authentication tokens are encrypted before they are written to disk, and the encryption key is kept in your
        operating system's credential store. Tokens are only ever sent to Microsoft and Mojang services.
      </p>
    </section>
    <section class="legal-page__section">
      <h2>Third-Party Services</h2>
      <p>
        The launcher connects to Microsoft and Xbox Live (sign-in), Mojang (game files and skins), Modrinth (mods and
        modpacks), the Fabric, Quilt, Forge and NeoForge download servers (mod loaders), Adoptium (Java), mc-heads.net
        (player head previews, which receive your public player ID) and GitHub (launcher updates). Each service has its
        own privacy policy.
      </p>
    </section>
    <section class="legal-page__section">
      <h2>Contact</h2>
      <p>
        If you have any questions about this Privacy Policy, please reach out through the Contact page.
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