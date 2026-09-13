<template>
  <div class="contact-page">
    <h1 class="contact-page__title">Contact Us</h1>
    <section class="contact-page__section">
      <h2>Get in Touch</h2>
      <p>Have questions, feedback, or need support? Reach out to the team.</p>
      <form class="contact-page__form" @submit.prevent="handleSubmit">
        <div class="contact-page__field">
          <label class="contact-page__label" for="name">Name</label>
          <input id="name" v-model="form.name" type="text" class="input" required />
        </div>
        <div class="contact-page__field">
          <label class="contact-page__label" for="email">Email</label>
          <input id="email" v-model="form.email" type="email" class="input" required />
        </div>
        <div class="contact-page__field">
          <label class="contact-page__label" for="message">Message</label>
          <textarea id="message" v-model="form.message" class="input" rows="5" required></textarea>
        </div>
        <button type="submit" class="btn">Send Message</button>
      </form>
    </section>
    <section class="contact-page__section">
      <h2>Other Channels</h2>
      <div class="contact-page__links">
        <a href="#" class="contact-page__link">Discord</a>
        <a href="#" class="contact-page__link">GitHub</a>
        <a href="#" class="contact-page__link">Twitter</a>
      </div>
    </section>
  </div>
</template>

<script setup lang="ts">
const form = reactive({
  name: '',
  email: '',
  message: ''
})

async function handleSubmit() {
  await new Promise(resolve => setTimeout(resolve, 300))
  alert('Thanks for reaching out! We will get back to you soon.')
  form.name = ''
  form.email = ''
  form.message = ''
}
</script>

<style scoped lang="scss">
@use '@/assets/styles/_mixins' as *;

.contact-page__title {
  font-family: var(--font-pixel);
  font-size: 2.5rem;
  text-align: center;
  margin-bottom: 2rem;
  text-transform: uppercase;
}

.contact-page__section {
  background-color: var(--bg-secondary);
  border: 2px solid var(--border-color);
  border-radius: var(--edge-radius);
  padding: 1.5rem;
  margin-bottom: 1.5rem;
}

.contact-page__section h2 {
  font-family: var(--font-pixel);
  font-size: 1.3rem;
  text-transform: uppercase;
  margin-bottom: 1rem;
}

.contact-page__form {
  display: flex;
  flex-direction: column;
  gap: 1rem;
  max-width: 600px;
}

.contact-page__field {
  display: flex;
  flex-direction: column;
  gap: 0.25rem;
}

.contact-page__label {
  font-family: var(--font-body);
  font-size: 1.1rem;
  color: var(--text-secondary);
}

.contact-page__links {
  display: flex;
  gap: 1rem;
  flex-wrap: wrap;
}

.contact-page__link {
  color: var(--accent);
  text-decoration: none;
}

.contact-page__link:hover {
  text-decoration: underline;
}
</style>