<template>
  <div class="home">
    <HeroSection />

    <section id="features" class="home__section">
      <header class="home__section-header">
        <span class="home__section-eyebrow">Built for players</span>
        <h2 class="home__section-title">Everything you need, nothing you do not</h2>
        <p class="home__section-subtitle">
          Mods that install with their dependencies, Forge that just works, crashes that explain
          themselves, and downloads that are verified before they ever run.
        </p>
      </header>

      <div class="home__features-grid">
        <FeatureCard
          v-for="feature in highlightedFeatures"
          :key="feature.title"
          :icon="feature.icon"
          :title="feature.title"
          :description="feature.description"
        />
      </div>

      <div class="home__section-more">
        <NuxtLink to="/features" class="btn btn--secondary">See all features</NuxtLink>
      </div>
    </section>

    <section class="home__gallery" aria-labelledby="gallery-title">
      <header class="home__section-header">
        <span class="home__section-eyebrow">A closer look</span>
        <h2 id="gallery-title" class="home__section-title">Built to get you playing</h2>
      </header>
      <div class="home__gallery-grid">
        <figure v-for="shot in screenshots" :key="shot.src" class="home__shot">
          <img :src="shot.src" :alt="shot.alt" width="1600" height="1000" loading="lazy" class="home__shot-image" />
          <figcaption class="home__shot-caption">
            <strong>{{ shot.title }}</strong>
            <span>{{ shot.text }}</span>
          </figcaption>
        </figure>
      </div>
    </section>

    <section class="home__trust">
      <div class="home__trust-copy">
        <span class="home__section-eyebrow">Security first</span>
        <h2 class="home__section-title home__trust-title">Safe by default</h2>
        <p class="home__section-subtitle">
          Every file is checksum-verified, your sign-in is encrypted, modpacks cannot touch files outside their folder,
          and nothing about you is tracked.
        </p>
        <NuxtLink to="/security" class="btn btn--secondary home__trust-link">How we keep you safe</NuxtLink>
      </div>
      <ul class="home__trust-list">
        <li><SiteIcon name="check" :size="18" />Verified downloads</li>
        <li><SiteIcon name="lock" :size="18" />Encrypted sign-in</li>
        <li><SiteIcon name="shield" :size="18" />Sandboxed modpacks</li>
        <li><SiteIcon name="key" :size="18" />Signed updates</li>
        <li><SiteIcon name="eye-off" :size="18" />No telemetry</li>
      </ul>
    </section>

    <section class="home__cta">
      <div class="home__cta-inner">
        <div class="home__cta-content">
          <span class="home__cta-eyebrow">Ready to play</span>
          <h2 class="home__cta-title">Get Pretupid Launcher</h2>
          <p class="home__cta-text">
            Free, open source, and built with the tools modern desktop apps deserve.
          </p>
        </div>
        <div class="home__cta-actions">
          <NuxtLink to="/download" class="btn btn--large">Download</NuxtLink>
          <button type="button" class="btn btn--secondary btn--large" @click="openLauncher">
            Open Launcher
          </button>
        </div>
      </div>
    </section>

    <section class="home__faq">
      <header class="home__section-header">
        <span class="home__section-eyebrow">FAQ</span>
        <h2 class="home__section-title">Frequently asked questions</h2>
      </header>

      <FaqAccordion :items="faqItems" />
    </section>
  </div>
</template>

<script setup lang="ts">
import { useLauncherBridge } from '~/composables/useLauncherBridge'
import { highlightedFeatures } from '~/composables/features'

const screenshots = [
  {
    src: '/screenshots/launcher-browse.webp',
    alt: 'Browsing Modrinth mods inside Pretupid Launcher',
    title: 'Browse Modrinth',
    text: 'Mods, modpacks, resource packs and shaders install straight into an instance, with their dependencies.'
  },
  {
    src: '/screenshots/launcher-library.webp',
    alt: 'The instance library in Pretupid Launcher',
    title: 'Your library',
    text: 'Every instance with its loader, version and playtime, ready to launch in one click.'
  }
]

const { openLauncher } = useLauncherBridge()

const faqItems = [
  {
    question: 'Is Pretupid Launcher free?',
    answer: 'Yes. Pretupid Launcher is free to use and open source under the GNU AGPL-3.0 license.'
  },
  {
    question: 'Is it safe to use?',
    answer:
      'Every game file, mod and Java download is checked against its published checksum, your Microsoft session is stored encrypted, and modpacks cannot write outside their own folder. The full details are on the Security page.'
  },
  {
    question: 'Does it collect any data?',
    answer: 'No. There is no telemetry, no analytics and no extra account. Everything stays on your PC.'
  },
  {
    question: 'Does it support mods?',
    answer:
      'Yes. Fabric, Quilt, Forge, and NeoForge are supported, and Modrinth mods and modpacks can be installed directly from the launcher.'
  },
  {
    question: 'Which Minecraft versions are supported?',
    answer:
      'Vanilla Minecraft from the earliest releases through the latest ones, plus any Fabric, Quilt, Forge, or NeoForge loader version compatible with the selected Minecraft release.'
  },
  {
    question: 'Is it affiliated with Mojang or Microsoft?',
    answer:
      'No. Pretupid Launcher is not an official Minecraft service and is not approved by or associated with Mojang or Microsoft.'
  },
  {
    question: 'How do I install Java?',
    answer:
      'The launcher detects the required Java major version from the Minecraft version you have selected and installs a matching runtime from Adoptium automatically if you do not have it.'
  }
]
</script>

<style scoped lang="scss">
@use 'design-system/mixins' as *;

.home__gallery {
  display: flex;
  flex-direction: column;
  gap: 2rem;
}

.home__gallery-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(min(100%, 420px), 1fr));
  gap: 1.5rem;
}

.home__shot {
  display: flex;
  flex-direction: column;
  gap: 1rem;
  margin: 0;
  padding: 0.75rem;
  background-color: var(--bg-secondary);
  border: 1px solid var(--border-color);
  border-radius: var(--edge-radius);
}

.home__shot-image {
  display: block;
  width: 100%;
  height: auto;
  border-radius: var(--edge-radius);
}

.home__shot-caption {
  display: flex;
  flex-direction: column;
  gap: 0.25rem;
  padding: 0 0.25rem 0.25rem;
  color: var(--text-secondary);

  strong {
    font-size: 1.1rem;
    color: var(--text-primary);
  }
}


.home {
  display: flex;
  flex-direction: column;
  gap: 5rem;
}

.home__section {
  display: flex;
  flex-direction: column;
  gap: 2.5rem;
}

.home__section-header {
  display: flex;
  flex-direction: column;
  align-items: center;
  text-align: center;
  gap: 0.75rem;
  max-width: 46rem;
  margin: 0 auto;
}

.home__section-eyebrow {
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

.home__section-title {
  font-family: var(--font-pixel);
  font-weight: 700;
  font-size: clamp(1.5rem, 3vw, 2.2rem);
  margin: 0;
}

.home__section-subtitle {
  font-family: var(--font-body);
  font-size: 1.25rem;
  color: var(--text-secondary);
  margin: 0;
}

.home__section-more {
  display: flex;
  justify-content: center;
}

.home__trust {
  display: grid;
  grid-template-columns: minmax(0, 1.4fr) minmax(0, 1fr);
  align-items: center;
  gap: 2rem;
  padding: 2.5rem;
  background-color: var(--bg-secondary);
  border: 2px solid var(--border-color);
  border-radius: var(--edge-radius);
}

.home__trust-copy {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 0.9rem;
}

.home__trust-title {
  text-align: left;
}

.home__trust-link {
  margin-top: 0.5rem;
}

.home__trust-list {
  display: flex;
  flex-direction: column;
  gap: 0.6rem;
  margin: 0;
  padding: 0;
  list-style: none;

  li {
    display: flex;
    align-items: center;
    gap: 0.75rem;
    padding: 0.7rem 1rem;
    font-size: 1.2rem;
    background-color: var(--bg-tertiary);
    border: 2px solid var(--border-color);
    border-radius: var(--edge-radius);
  }

  .site-icon {
    color: var(--accent);
  }
}

@media (max-width: 800px) {
  .home__trust {
    grid-template-columns: 1fr;
    padding: 1.5rem;
  }
}

.home__features-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(280px, 1fr));
  gap: 1.5rem;
}

.home__cta {
  padding: 3rem 0;
}

.home__cta-inner {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: space-between;
  gap: 1.5rem;
  padding: 2.5rem;
  background-color: var(--bg-secondary);
  border: 2px solid var(--border-color);
  border-radius: var(--edge-radius);
  @include block-shadow(var(--shadow-color));
}

.home__cta-content {
  display: flex;
  flex-direction: column;
  gap: 0.5rem;
  max-width: 32rem;
}

.home__cta-eyebrow {
  font-family: var(--font-body);
  font-weight: 600;
  font-size: 0.85rem;
  text-transform: uppercase;
  letter-spacing: 0.1em;
  color: var(--accent);
}

.home__cta-title {
  font-family: var(--font-pixel);
  font-weight: 700;
  font-size: 1.6rem;
  margin: 0;
}

.home__cta-text {
  font-family: var(--font-body);
  font-size: 1.15rem;
  color: var(--text-secondary);
  margin: 0;
}

.home__cta-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 0.75rem;
}

.home__faq {
  max-width: 820px;
  width: 100%;
  margin: 0 auto;
  display: flex;
  flex-direction: column;
  gap: 2rem;
}
</style>