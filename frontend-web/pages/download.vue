<template>
  <div class="download-page">
    <header class="download-page__hero">
      <span class="download-page__eyebrow">Free and open source</span>
      <h1 class="download-page__title">Download Pretupid Launcher</h1>
      <p class="download-page__subtitle">
        Choose your platform. Windows is available today. Linux and macOS are on the way.
      </p>
    </header>

    <div class="download-page__grid">
      <DownloadCard
        title="Linux"
        description="Distribution-agnostic AppImage and tarball. Coming soon."
        :url="repositoryUrl"
        button-text="Follow progress"
        meta="In development"
        :featured="false"
      />
      <DownloadCard
        title="Windows"
        description="Native installer for Windows 10 and 11, 64-bit."
        :url="`${repositoryUrl}/releases/latest`"
        button-text="Download for Windows"
        meta="Free and open source"
        :featured="true"
      />
      <DownloadCard
        title="macOS"
        description="Universal build for Apple Silicon and Intel Macs. Coming soon."
        :url="repositoryUrl"
        button-text="Follow progress"
        meta="In development"
        :featured="false"
      />
    </div>

    <section class="download-page__requirements">
      <h2 class="download-page__requirements-title">System requirements</h2>
      <div class="download-page__requirements-grid">
        <div class="download-page__requirement">
          <span class="download-page__requirement-label">Operating system</span>
          <span class="download-page__requirement-value">Windows 10 or Windows 11 (64-bit)</span>
        </div>
        <div class="download-page__requirement">
          <span class="download-page__requirement-label">Memory</span>
          <span class="download-page__requirement-value">4 GB RAM minimum, 8 GB recommended</span>
        </div>
        <div class="download-page__requirement">
          <span class="download-page__requirement-label">Java</span>
          <span class="download-page__requirement-value">Java 17 or newer, auto-installed if missing</span>
        </div>
        <div class="download-page__requirement">
          <span class="download-page__requirement-label">Disk</span>
          <span class="download-page__requirement-value">500 MB for the launcher, plus space for instances</span>
        </div>
      </div>
    </section>

    <section class="download-page__open">
      <div class="download-page__open-content">
        <h2 class="download-page__open-title">Already installed?</h2>
        <p class="download-page__open-text">
          Open the launcher directly from your browser without downloading again.
        </p>
      </div>
      <button type="button" class="btn" @click="openLauncher">Open Launcher</button>
    </section>
  </div>
</template>

<script setup lang="ts">
import { useLauncherBridge } from '~/composables/useLauncherBridge'

const config = useRuntimeConfig()
const repositoryUrl = `https://github.com/${config.public.repository}`
const { openLauncher } = useLauncherBridge()
</script>

<style scoped lang="scss">
@use 'design-system/mixins' as *;

.download-page {
  display: flex;
  flex-direction: column;
  gap: 3rem;
}

.download-page__hero {
  display: flex;
  flex-direction: column;
  align-items: center;
  text-align: center;
  gap: 0.75rem;
  max-width: 42rem;
  margin: 0 auto;
}

.download-page__eyebrow {
  display: inline-block;
  font-family: var(--font-pixel);
  font-size: 0.85rem;
  text-transform: uppercase;
  letter-spacing: 0.1em;
  color: var(--accent);
  background-color: var(--bg-secondary);
  border: 2px solid var(--border-color);
  border-radius: var(--edge-radius);
  padding: 0.4rem 0.8rem;
}

.download-page__title {
  font-family: var(--font-pixel);
  font-size: clamp(1.8rem, 4vw, 2.6rem);
  text-transform: uppercase;
  margin: 0;
}

.download-page__subtitle {
  font-family: var(--font-body);
  font-size: 1.25rem;
  color: var(--text-secondary);
  margin: 0;
}

.download-page__grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(280px, 1fr));
  gap: 1.5rem;
}

.download-page__requirements {
  display: flex;
  flex-direction: column;
  gap: 1.5rem;
  padding: 2rem;
  background-color: var(--bg-secondary);
  border: 2px solid var(--border-color);
  border-radius: var(--edge-radius);
}

.download-page__requirements-title {
  font-family: var(--font-pixel);
  font-size: 1rem;
  text-transform: uppercase;
  margin: 0;
}

.download-page__requirements-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(220px, 1fr));
  gap: 1.5rem;
}

.download-page__requirement {
  display: flex;
  flex-direction: column;
  gap: 0.35rem;
}

.download-page__requirement-label {
  font-family: var(--font-pixel);
  font-size: 0.85rem;
  text-transform: uppercase;
  letter-spacing: 0.05em;
  color: var(--accent);
}

.download-page__requirement-value {
  font-family: var(--font-body);
  font-size: 1.1rem;
  color: var(--text-primary);
}

.download-page__open {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 1.5rem;
  padding: 2rem;
  background-color: var(--bg-secondary);
  border: 2px dashed var(--border-color);
  border-radius: var(--edge-radius);
  flex-wrap: wrap;
}

.download-page__open-content {
  display: flex;
  flex-direction: column;
  gap: 0.35rem;
}

.download-page__open-title {
  font-family: var(--font-pixel);
  font-size: 1rem;
  text-transform: uppercase;
  margin: 0;
}

.download-page__open-text {
  font-family: var(--font-body);
  font-size: 1.1rem;
  color: var(--text-secondary);
  margin: 0;
}
</style>