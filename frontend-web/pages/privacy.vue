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
        <li>Authentication tokens required for Microsoft login.</li>
        <li>Local instance and mod data stored on your device.</li>
        <li>Anonymous usage statistics to improve the launcher (if enabled).</li>
      </ul>
      <p>We do not sell your personal information to third parties.</p>
    </section>
    <section class="legal-page__section">
      <h2>Data Storage</h2>
      <p>
        All personal data is stored locally on your device in the Pretupid Launcher data directory.
        Authentication tokens are used solely to connect to Minecraft services and are not transmitted to us.
      </p>
    </section>
    <section class="legal-page__section">
      <h2>Third-Party Services</h2>
      <p>
        The launcher integrates with Modrinth for mod and modpack downloads.
        Please refer to Modrinth's own privacy policy for details on their data practices.
      </p>
    </section>
    <section class="legal-page__section">
      <h2>Contact</h2>
      <p>
        If you have any questions about this Privacy Policy, please contact us through the website.
      </p>
    </section>
  </div>
</template>

<style scoped lang="scss">
@use '@/assets/styles/_mixins' as *;

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