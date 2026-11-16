<template>
  <div class="download-page">
    <header class="download-page__hero">
      <h1 class="download-page__title">Download Pretupid Launcher</h1>
      <p class="download-page__subtitle">
        Pick your system. The launcher updates itself after that.
      </p>
      <p v-if="info?.launcher" class="download-page__version">Version {{ info.launcher.version }}</p>
      <p v-if="unavailable" class="download-page__notice" role="status">
        That download is not published yet. Please try another option or check back soon.
      </p>
    </header>

    <div class="download-page__grid">
      <DownloadCard
        v-for="option in options"
        :key="option.id"
        :title="option.title"
        :description="option.description"
        :url="option.url"
        :alternatives="option.alternatives"
        :button-text="option.button"
        :meta="option.meta"
        :featured="platform === option.id"
      />
    </div>

    <section class="download-page__client" aria-labelledby="client-download-title">
      <img src="@shared/branding/pretupid_logo_512x.png" alt="" width="72" height="72" class="download-page__client-icon" />
      <div class="download-page__client-copy">
        <h2 id="client-download-title" class="download-page__client-title">Pretupid Client</h2>
        <p class="download-page__client-text">
          The easiest way is the launcher: open any Fabric instance and press Install Pretupid Client. Using another launcher?
          Download the mod and put it into your mods folder together with Fabric API.
        </p>
        <span v-if="info?.client" class="download-page__client-meta">
          Version {{ info.client.version }}<template v-if="info.client.minecraft"> · Minecraft {{ info.client.minecraft }}</template>
        </span>
      </div>
      <div class="download-page__client-actions">
        <a href="/api/download/client" class="btn" rel="nofollow">Download the mod</a>
        <a href="https://modrinth.com/mod/fabric-api" class="download-page__client-link" target="_blank" rel="noopener noreferrer">Get Fabric API</a>
      </div>
    </section>

    <section class="download-page__requirements">
      <h2 class="download-page__requirements-title">System requirements</h2>
      <div class="download-page__requirements-grid">
        <div class="download-page__requirement">
          <span class="download-page__requirement-label">Operating system</span>
          <span class="download-page__requirement-value">Windows 10/11 (64-bit), macOS 11 or newer, or 64-bit Linux with WebKitGTK 4.1</span>
        </div>
        <div class="download-page__requirement">
          <span class="download-page__requirement-label">Memory</span>
          <span class="download-page__requirement-value">4 GB RAM minimum, 8 GB recommended</span>
        </div>
        <div class="download-page__requirement">
          <span class="download-page__requirement-label">Java</span>
          <span class="download-page__requirement-value">Not needed, the right Java is downloaded for each Minecraft version</span>
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
import { useVisitorPlatform, type VisitorPlatform } from '~/composables/useVisitorPlatform'

interface DownloadInfo {
  launcher: { version: string } | null
  client: { version: string; minecraft: string } | null
}

const route = useRoute()
const { openLauncher } = useLauncherBridge()
const { platform } = useVisitorPlatform()
const { data: info } = useFetch<DownloadInfo>('/api/download/info', { lazy: true, server: false })
const unavailable = computed(() => typeof route.query.unavailable === 'string')

const options: {
  id: VisitorPlatform
  title: string
  description: string
  meta: string
  button: string
  url: string
  alternatives?: { label: string; url: string }[]
}[] = [
  {
    id: 'windows',
    title: 'Windows',
    description: 'Installer for Windows 10 and 11, 64-bit. No admin rights needed.',
    meta: '.exe installer',
    button: 'Download for Windows',
    url: '/api/download/launcher/windows'
  },
  {
    id: 'macos',
    title: 'macOS',
    description: 'For Apple Silicon and Intel Macs on macOS 11 or newer.',
    meta: '.dmg disk image',
    button: 'Download for Apple Silicon',
    url: '/api/download/launcher/macos?variant=arm64',
    alternatives: [{ label: 'Intel Mac', url: '/api/download/launcher/macos?variant=x64' }]
  },
  {
    id: 'linux',
    title: 'Linux',
    description: 'AppImage that runs on most distributions, plus .deb and .rpm packages.',
    meta: 'AppImage, .deb, .rpm',
    button: 'Download AppImage',
    url: '/api/download/launcher/linux?variant=appimage',
    alternatives: [
      { label: '.deb', url: '/api/download/launcher/linux?variant=deb' },
      { label: '.rpm', url: '/api/download/launcher/linux?variant=rpm' }
    ]
  }
]
</script>

<style scoped lang="scss">
@use 'design-system/mixins' as *;

.download-page__version {
  margin: 0;
  font-size: 0.9rem;
  color: var(--text-secondary);
}

.download-page__notice {
  margin: 0;
  padding: 0.6rem 1rem;
  color: var(--warning);
  border: 1px solid color-mix(in srgb, var(--warning) 45%, transparent);
  border-radius: var(--edge-radius);
}

.download-page__client {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 1.5rem;
  padding: 1.75rem;
  background:
    radial-gradient(500px 220px at 0% 0%, color-mix(in srgb, #8f94f5 22%, transparent), transparent 70%),
    var(--bg-secondary);
  border: 2px solid color-mix(in srgb, #8f94f5 45%, var(--border-color));
  border-radius: var(--edge-radius);
}

.download-page__client-icon {
  flex-shrink: 0;
  width: 72px;
  height: 72px;
}

.download-page__client-copy {
  display: flex;
  flex: 1;
  flex-direction: column;
  gap: 0.4rem;
  min-width: 260px;
}

.download-page__client-title {
  margin: 0;
  font-family: var(--font-pixel);
  font-size: 1.35rem;
}

.download-page__client-text {
  margin: 0;
  color: var(--text-secondary);
}

.download-page__client-meta {
  font-size: 0.85rem;
  color: var(--text-secondary);
}

.download-page__client-link {
  font-size: 0.88rem;
  color: var(--text-secondary);
  text-decoration: underline;
  text-underline-offset: 3px;

  &:hover {
    color: var(--accent);
  }
}

.download-page__client-actions {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 0.5rem;
}

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


.download-page__title {
  font-family: var(--font-pixel);
  font-weight: 700;
  font-size: clamp(1.8rem, 4vw, 2.6rem);
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
  font-weight: 700;
  font-size: 1rem;
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
  font-family: var(--font-body);
  font-weight: 600;
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
  font-weight: 700;
  font-size: 1rem;
  margin: 0;
}

.download-page__open-text {
  font-family: var(--font-body);
  font-size: 1.1rem;
  color: var(--text-secondary);
  margin: 0;
}
</style>