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
      <h2>Pretupid Launcher</h2>
      <p>
        The launcher has no telemetry or analytics. Your instances, mods, playtime and settings stay on your device.
        Microsoft sign-in tokens are encrypted before they are written to disk, and the key is kept in your operating
        system's credential store. Tokens are only sent to Microsoft and Mojang.
      </p>
    </section>
    <section class="legal-page__section">
      <h2>Pretupid Client online services</h2>
      <p>
        Badges, cosmetics, friends and news use Pretupid online services. They are optional and can be switched off in
        the client under Settings, Online &amp; social. When they are on, we store:
      </p>
      <ul>
        <li>Your Minecraft player ID and name, and when you were last online.</li>
        <li>Your nametag style and the cape and trail you selected, so other Pretupid players can see them.</li>
        <li>Capes you upload, until you delete them or they are removed by moderation.</li>
        <li>Your friends, friend requests, blocked players and the messages and invites you send to friends.
          Messages are deleted after 30 days. Messages are not end-to-end encrypted.</li>
        <li>The server address you are playing on, only if you turn on sharing it with friends.</li>
      </ul>
      <p>
        Your account is verified the same way a Minecraft server verifies it. Your Microsoft or Minecraft access token is
        never sent to Pretupid. Requests pass through Cloudflare, which processes IP addresses to deliver and protect
        the service. We use IP addresses only for rate limiting and security and do not sell or share personal
        information.
      </p>
      <p>
        To have your Pretupid data deleted, contact us through the Contact page from the email or account you want to
        identify yourself with, and include your Minecraft name.
      </p>
    </section>
    <section class="legal-page__section">
      <h2>Third-Party Services</h2>
      <p>
        The launcher connects to Microsoft and Xbox Live (sign-in), Mojang (game files and skins), Modrinth (mods and
        modpacks), the Fabric, Quilt, Forge and NeoForge download servers (mod loaders), Adoptium (Java), mc-heads.net
        (player head previews, which receive your public player ID), GitHub (launcher and client updates) and Cloudflare (hosting of Pretupid online services). Each service has its
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
  font-weight: 700;
  font-size: 2.5rem;
  text-align: center;
  margin-bottom: 2rem;
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
  font-weight: 700;
  font-size: 1.3rem;
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