<template>
  <div class="contact-page">
    <header class="contact-page__header">
      <span class="contact-page__eyebrow">Get in touch</span>
      <h1 class="contact-page__title">Contact</h1>
      <p class="contact-page__subtitle">
        Feedback, bug reports, feature ideas. All of it welcome, and all of it handled in the open on GitHub.
      </p>
    </header>

    <section class="contact-page__card">
      <h2 class="contact-page__card-title">Pick a channel</h2>
      <div class="contact-page__links">
        <a
          v-for="channel in channels"
          :key="channel.label"
          :href="channel.href"
          class="contact-page__link"
          target="_blank"
          rel="noopener noreferrer"
        >
          <SiteIcon :name="channel.icon" :size="22" class="contact-page__link-icon" />
          <span class="contact-page__link-label">{{ channel.label }}</span>
          <span class="contact-page__link-hint">{{ channel.hint }}</span>
        </a>
      </div>
    </section>

    <section class="contact-page__card">
      <h2 class="contact-page__card-title">Reporting a bug?</h2>
      <ol class="contact-page__steps">
        <li>Open the instance in the launcher and switch to the <strong>Console</strong> tab.</li>
        <li>Press <strong>Copy</strong> to copy the game output.</li>
        <li>Paste it into a new issue together with what you were doing when it happened.</li>
      </ol>
      <p class="contact-page__note">
        Please do not post your access token or anything from the launcher's data folder. The console output never
        contains your password.
      </p>
    </section>
  </div>
</template>

<script setup lang="ts">
import type { SiteIconName } from '~/components/SiteIcon.vue'
import { REPOSITORY_URL } from '~/composables/features'

useSeoMeta({
  title: 'Contact — Pretupid Launcher',
  description: 'Report bugs, request features or report a security issue for Pretupid Launcher.'
})

const channels: { icon: SiteIconName; label: string; hint: string; href: string }[] = [
  { icon: 'bug', label: 'Report a bug', hint: 'Something broke or crashed', href: `${REPOSITORY_URL}/issues/new` },
  { icon: 'bolt', label: 'Suggest a feature', hint: 'Ideas and improvements', href: `${REPOSITORY_URL}/issues/new` },
  { icon: 'shield', label: 'Security issue', hint: 'Report privately', href: `${REPOSITORY_URL}/security/advisories/new` },
  { icon: 'github', label: 'Source code', hint: 'Browse and contribute', href: REPOSITORY_URL }
]
</script>

<style scoped lang="scss">
.contact-page {
  display: flex;
  flex-direction: column;
  gap: 2rem;
  max-width: 760px;
  margin: 0 auto;
}

.contact-page__header {
  display: flex;
  flex-direction: column;
  align-items: center;
  text-align: center;
  gap: 0.75rem;
}

.contact-page__eyebrow {
  display: inline-block;
  font-family: var(--font-body);
  font-weight: 600;
  font-size: 0.85rem;
  text-transform: uppercase;
  letter-spacing: 0.1em;
  color: var(--accent);
  background-color: var(--bg-secondary);
  border: 2px solid var(--border-color);
  border-radius: var(--edge-radius);
  padding: 0.4rem 0.8rem;
}

.contact-page__title {
  font-family: var(--font-pixel);
  font-weight: 700;
  font-size: clamp(1.8rem, 4vw, 2.4rem);
  margin: 0;
}

.contact-page__subtitle {
  font-family: var(--font-body);
  font-size: 1.2rem;
  color: var(--text-secondary);
  margin: 0;
}

.contact-page__card {
  padding: 1.75rem;
  background-color: var(--bg-secondary);
  border: 2px solid var(--border-color);
  border-radius: var(--edge-radius);
}

.contact-page__card-title {
  font-family: var(--font-pixel);
  font-weight: 700;
  font-size: 1.1rem;
  margin: 0 0 1.25rem;
}

.contact-page__links {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(200px, 1fr));
  gap: 1rem;
}

.contact-page__link {
  display: flex;
  flex-direction: column;
  gap: 0.4rem;
  padding: 1rem;
  background-color: var(--bg-tertiary);
  border: 2px solid var(--border-color);
  border-radius: var(--edge-radius);
  text-decoration: none;
  color: inherit;
  transition: border-color var(--transition-fast), transform var(--transition-fast);
}

.contact-page__link:hover {
  border-color: var(--accent);
  transform: translate(-2px, -2px);
}

.contact-page__link-icon {
  color: var(--accent);
}

.contact-page__link-label {
  font-family: var(--font-body);
  font-weight: 600;
  letter-spacing: 0.08em;
  font-size: 0.85rem;
  text-transform: uppercase;
  color: var(--accent);
}

.contact-page__link-hint {
  font-family: var(--font-body);
  font-size: 1rem;
  color: var(--text-secondary);
}

.contact-page__steps {
  display: flex;
  flex-direction: column;
  gap: 0.5rem;
  margin: 0 0 1rem;
  padding-left: 1.4rem;
  font-size: 1.15rem;
}

.contact-page__note {
  margin: 0;
  color: var(--text-secondary);
}
</style>
