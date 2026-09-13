<template>
  <div class="download-page">
    <h1 class="download-page__title">Download Pretupid Launcher</h1>
    <p class="download-page__subtitle">Choose your platform below.</p>
    <div class="download-page__grid">
      <DownloadCard
        title="Linux"
        description="Coming soon"
        url="#"
        button-text="Join Waitlist"
        meta="Early access"
        :featured="false"
      />
      <DownloadCard
        title="Windows"
        description="Windows 10/11 installer"
        :url="config.public.downloadUrl"
        button-text="Download for Windows"
        meta="Free"
        :featured="true"
      />
      <DownloadCard
        title="macOS"
        description="Coming soon"
        url="#"
        button-text="Join Waitlist"
        meta="Early access"
        :featured="false"
      />
    </div>
    <section class="download-page__notes">
      <h2>System Requirements</h2>
      <ul>
        <li>Windows 10 or 11 (64-bit)</li>
        <li>4 GB RAM minimum</li>
        <li>Java 17 or newer (auto-installed if missing)</li>
      </ul>
      <p>For other platforms, join our <NuxtLink to="/about" class="download-page__link">contact list</NuxtLink> to be notified.</p>
    </section>
  </div>
</template>

<script setup lang="ts">
const config = useRuntimeConfig()
</script>

<style scoped lang="scss">
@use '@/assets/styles/_mixins' as *;

.download-page__title {
  font-family: var(--font-pixel);
  font-size: 2.5rem;
  text-align: center;
  margin-bottom: 0.5rem;
  text-transform: uppercase;
}

.download-page__subtitle {
  font-size: 1.2rem;
  color: var(--text-secondary);
  text-align: center;
  margin-bottom: 2rem;
}

.download-page__grid {
  display: flex;
  justify-content: center;
  align-items: stretch;
  gap: 1.5rem;
  margin-bottom: 3rem;
  flex-wrap: wrap;
}

.download-page__grid > * {
  flex: 1 1 280px;
  max-width: 350px;
}

.download-page__grid > *:nth-child(1) {
  order: 1;
}

.download-page__grid > *:nth-child(2) {
  order: 2;
}

.download-page__grid > *:nth-child(3) {
  order: 3;
}

.download-page__notes {
  background-color: var(--bg-secondary);
  border: 2px solid var(--border-color);
  border-radius: var(--edge-radius);
  padding: 1.5rem;
}

.download-page__notes h2 {
  font-family: var(--font-pixel);
  font-size: 1.2rem;
  text-transform: uppercase;
  margin-bottom: 1rem;
}

.download-page__notes ul {
  list-style: square;
  padding-left: 1.5rem;
  color: var(--text-secondary);
  margin-bottom: 1rem;
}

.download-page__link {
  color: var(--accent);
  text-decoration: none;
}

.download-page__link:hover {
  text-decoration: underline;
}
</style>