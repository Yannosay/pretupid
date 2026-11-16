import type { Directive } from 'vue'

export default defineNuxtPlugin((nuxtApp) => {
  const reducedMotion = window.matchMedia('(prefers-reduced-motion: reduce)').matches
  const observer = reducedMotion
    ? null
    : new IntersectionObserver(
        (entries) => {
          for (const entry of entries) {
            if (entry.isIntersecting) {
              entry.target.classList.add('reveal--visible')
              observer?.unobserve(entry.target)
            }
          }
        },
        { rootMargin: '0px 0px -8% 0px', threshold: 0.12 }
      )

  const reveal: Directive<HTMLElement, number | undefined> = {
    mounted(element, binding) {
      if (!observer) return
      element.classList.add('reveal')
      if (binding.value) element.style.setProperty('--reveal-delay', `${binding.value}ms`)
      observer.observe(element)
    },
    beforeUnmount(element) {
      observer?.unobserve(element)
    }
  }

  nuxtApp.vueApp.directive('reveal', reveal)
})
