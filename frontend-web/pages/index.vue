<template>
  <div class="home">
    <HeroSection />
    <section id="features" class="home__features">
      <h2 class="home__features-title">Features</h2>
      <div class="home__features-grid">
        <FeatureCard icon="🚀" title="Fast Launch" description="Optimised launch pipeline for quick Minecraft startup." />
        <FeatureCard icon="🎨" title="Customizable" description="Themes, accents, and layout options to match your style." />
        <FeatureCard icon="🔧" title="Modrinth Integration" description="Browse, install, and manage mods and modpacks directly." />
        <FeatureCard icon="🛡️" title="Secure" description="Microsoft authentication and local data storage." />
        <FeatureCard icon="📦" title="Instance Management" description="Create, clone, and manage multiple Minecraft instances with ease." />
        <FeatureCard icon="⚙️" title="Advanced Settings" description="Fine-tune JVM arguments, game directories, and resolution." />
      </div>
    </section>
    <section class="home__download-cta">
      <h2 class="home__download-cta-title">Ready to play?</h2>
      <NuxtLink to="/download" class="btn btn--large home__download-cta-button">Download Now</NuxtLink>
    </section>
    <section class="home__faq">
      <h2 class="home__faq-title">Frequently Asked Questions</h2>
      <FaqAccordion :items="faqItems" />
    </section>
  </div>
</template>

<script setup lang="ts">
const faqItems = [
  {
    question: 'Is Pretupid Launcher free?',
    answer: 'Yes, Pretupid Launcher is free to use and open source under the GNU 2.0 license.'
  },
  {
    question: 'Does it support mods?',
    answer: 'Yes, it integrates with Modrinth to install mods and modpacks directly from the launcher.'
  },
  {
    question: 'Which Minecraft versions are supported?',
    answer: 'Vanilla Minecraft from classic to the latest releases, plus Fabric, Quilt, Forge, and NeoForge loaders.'
  },
  {
    question: 'Is it affiliated with Mojang or Microsoft?',
    answer: 'No, Pretupid Launcher is not an official Minecraft service and is not approved by or associated with Mojang or Microsoft.'
  },
  {
    question: 'How do I install Java?',
    answer: 'The launcher automatically detects and installs the required Java version if missing.'
  }
]
</script>

<style scoped lang="scss">
@use '@/assets/styles/_mixins' as *;

.home {
  display: flex;
  flex-direction: column;
  gap: 4rem;
}

.home__features-title,
.home__download-cta-title,
.home__faq-title {
  font-family: var(--font-pixel);
  font-size: 2rem;
  text-align: center;
  margin-bottom: 2rem;
  text-transform: uppercase;
}

.home__features-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(250px, 1fr));
  gap: 1.5rem;
}

.home__download-cta {
  text-align: center;
  padding: 3rem 0;
}

.home__download-cta-button {
  font-size: 1.2rem;
  padding: 1rem 2.5rem;
}

.home__faq {
  max-width: 800px;
  margin: 0 auto;
}
</style>