<template>
  <NuxtLayout>
    <NuxtPage />
  </NuxtLayout>
</template>

<script setup lang="ts">
useHead({
  htmlAttrs: {
    lang: 'en'
  },
  bodyAttrs: {
    class: 'pretupid-body'
  }
})
</script>

<style>
html {
  scroll-behavior: smooth;
}
</style>