<template>
  <div class="security-page">
    <header class="security-page__header">
      <span class="security-page__eyebrow">Security</span>
      <h1 class="security-page__title">How Pretupid keeps you safe</h1>
      <p class="security-page__subtitle">
        A launcher downloads and runs code on your PC and holds the key to your Minecraft account. Here is exactly how
        Pretupid handles that responsibility.
      </p>
    </header>

    <section class="security-page__grid">
      <article v-for="item in measures" :key="item.title" class="security-page__card">
        <div class="security-page__icon"><SiteIcon :name="item.icon" :size="22" /></div>
        <h2 class="security-page__card-title">{{ item.title }}</h2>
        <p class="security-page__card-text">{{ item.text }}</p>
      </article>
    </section>

    <section class="security-page__panel">
      <h2 class="security-page__panel-title">What Pretupid talks to</h2>
      <p class="security-page__card-text">
        The launcher only connects to the services it needs. Nothing is sent to the Pretupid team.
      </p>
      <ul class="security-page__hosts">
        <li v-for="host in hosts" :key="host.name">
          <strong>{{ host.name }}</strong>
          <span>{{ host.purpose }}</span>
        </li>
      </ul>
    </section>

    <section class="security-page__panel">
      <h2 class="security-page__panel-title">Found a vulnerability?</h2>
      <p class="security-page__card-text">
        Please report it privately through GitHub security advisories instead of a public issue, so it can be fixed
        before details become public.
      </p>
      <div class="security-page__actions">
        <a :href="`${REPOSITORY_URL}/security/advisories/new`" class="btn" rel="noopener noreferrer" target="_blank">
          Report privately
        </a>
        <a :href="REPOSITORY_URL" class="btn btn--secondary" rel="noopener noreferrer" target="_blank">
          Review the source
        </a>
      </div>
    </section>
  </div>
</template>

<script setup lang="ts">
import type { SiteIconName } from '~/components/SiteIcon.vue'
import { REPOSITORY_URL } from '~/composables/features'

useSeoMeta({
  title: 'Security — Pretupid Launcher',
  description: 'Checksum-verified downloads, encrypted sign-in, sandboxed modpack files, signed updates and no telemetry.'
})

const measures: { icon: SiteIconName; title: string; text: string }[] = [
  {
    icon: 'check',
    title: 'Every download is verified',
    text: 'Minecraft, libraries, assets, loader files, mods and Java runtimes are checked against SHA-1, SHA-256 or SHA-512 checksums published by Mojang, Modrinth, the loader projects and Adoptium. A file that does not match is thrown away, never run.'
  },
  {
    icon: 'lock',
    title: 'Encrypted sign-in',
    text: 'Your Microsoft session is encrypted with ChaCha20-Poly1305 before it is stored. The key lives in the Windows Credential Manager, so copying the launcher folder alone does not expose your account.'
  },
  {
    icon: 'shield',
    title: 'Modpacks stay in their box',
    text: 'Modpack archives and file lists are checked so nothing can be written outside the instance folder, and pack files are only downloaded over HTTPS from Modrinth, GitHub or GitLab.'
  },
  {
    icon: 'key',
    title: 'Signed updates',
    text: 'Launcher updates are signed. The updater refuses any package whose signature does not match the key built into the app.'
  },
  {
    icon: 'folder',
    title: 'No partial files',
    text: 'Downloads are written to a temporary file and only moved into place once they are complete and verified, so an interrupted download cannot corrupt an instance.'
  },
  {
    icon: 'eye-off',
    title: 'No tracking',
    text: 'There is no telemetry, no analytics and no Pretupid account. Your instances, settings and logs stay on your PC.'
  }
]

const hosts = [
  { name: 'Microsoft & Xbox Live', purpose: 'Signing in with your Microsoft account' },
  { name: 'Mojang', purpose: 'Minecraft versions, game files and your skin' },
  { name: 'Modrinth', purpose: 'Searching and downloading mods, packs and shaders' },
  { name: 'Fabric, Quilt, Forge, NeoForge', purpose: 'Mod loader files for the versions you pick' },
  { name: 'Adoptium', purpose: 'Java runtimes when a matching Java is not installed' },
  { name: 'mc-heads.net', purpose: 'Showing your player head and skin preview' },
  { name: 'GitHub', purpose: 'Checking for and downloading launcher updates' }
]
</script>

<style scoped lang="scss">
.security-page {
  display: flex;
  flex-direction: column;
  gap: 3rem;
}

.security-page__header {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 0.75rem;
  max-width: 48rem;
  margin: 0 auto;
  text-align: center;
}

.security-page__eyebrow {
  padding: 0.4rem 0.8rem;
  font-family: var(--font-pixel);
  font-size: 0.85rem;
  letter-spacing: 0.1em;
  text-transform: uppercase;
  color: var(--accent);
  background-color: var(--bg-secondary);
  border: 2px solid var(--border-color);
  border-radius: var(--edge-radius);
}

.security-page__title {
  margin: 0;
  font-family: var(--font-pixel);
  font-size: clamp(1.5rem, 3.5vw, 2.3rem);
  text-transform: uppercase;
}

.security-page__subtitle {
  margin: 0;
  font-size: 1.25rem;
  color: var(--text-secondary);
}

.security-page__grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(300px, 1fr));
  gap: 1.25rem;
}

.security-page__card,
.security-page__panel {
  display: flex;
  flex-direction: column;
  gap: 0.75rem;
  padding: 1.5rem;
  background-color: var(--bg-secondary);
  border: 2px solid var(--border-color);
  border-radius: var(--edge-radius);
}

.security-page__icon {
  width: 48px;
  height: 48px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--accent);
  background-color: var(--bg-tertiary);
  border: 2px solid var(--border-color);
  border-radius: var(--edge-radius);
}

.security-page__card-title,
.security-page__panel-title {
  margin: 0;
  font-family: var(--font-pixel);
  font-size: 0.8rem;
  line-height: 1.4;
  text-transform: uppercase;
}

.security-page__card-text {
  margin: 0;
  font-size: 1.15rem;
  line-height: 1.3;
  color: var(--text-secondary);
}

.security-page__hosts {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(260px, 1fr));
  gap: 0.6rem;
  margin: 0;
  padding: 0;
  list-style: none;

  li {
    display: flex;
    flex-direction: column;
    gap: 0.2rem;
    padding: 0.75rem 1rem;
    background-color: var(--bg-tertiary);
    border: 2px solid var(--border-color);
    border-radius: var(--edge-radius);
  }

  strong {
    font-size: 1.15rem;
  }

  span {
    color: var(--text-secondary);
  }
}

.security-page__actions {
  display: flex;
  flex-wrap: wrap;
  gap: 0.75rem;
}
</style>
