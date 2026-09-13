<template>
  <div class="features-page">
    <h1 class="features-page__title">Features</h1>
    <div class="features-page__grid">
      <FeatureCard
        icon="🚀"
        title="Fast Launch"
        description="Optimised launch pipeline for quick Minecraft startup."
      />
      <FeatureCard
        icon="🎨"
        title="Customizable"
        description="Themes, accents, and layout options to match your style."
      />
      <FeatureCard
        icon="🔧"
        title="Modrinth Integration"
        description="Browse, install, and manage mods and modpacks directly."
      />
      <FeatureCard
        icon="🛡️"
        title="Secure"
        description="Microsoft authentication and local data storage."
      />
      <FeatureCard
        icon="📦"
        title="Instance Management"
        description="Create, clone, and manage multiple Minecraft instances with ease."
      />
      <FeatureCard
        icon="⚙️"
        title="Advanced Settings"
        description="Fine-tune JVM arguments, game directories, and resolution."
      />
      <FeatureCard
        icon="📚"
        title="Modpack Support"
        description="Import and export modpacks with full compatibility."
      />
      <FeatureCard
        icon="🖥️"
        title="Cross-Platform"
        description="Built with Tauri and Rust for Windows, macOS, and Linux."
      />
    </div>
  </div>
</template>

<style scoped lang="scss">
@use '@/assets/styles/_mixins' as *;

.features-page__title {
  font-family: var(--font-pixel);
  font-size: 2.5rem;
  text-align: center;
  margin-bottom: 2rem;
  text-transform: uppercase;
}

.features-page__grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(250px, 1fr));
  gap: 1.5rem;
}
</style>