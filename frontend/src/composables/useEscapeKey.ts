import { onBeforeUnmount, onMounted } from 'vue'

const handlers: Array<{ active: () => boolean; run: () => void }> = []

function onKeydown(event: KeyboardEvent) {
  if (event.key !== 'Escape' || event.defaultPrevented) return
  for (let i = handlers.length - 1; i >= 0; i--) {
    const handler = handlers[i]
    if (handler.active()) {
      event.preventDefault()
      handler.run()
      return
    }
  }
}

export function useEscapeKey(active: () => boolean, run: () => void) {
  const handler = { active, run }
  onMounted(() => {
    if (!handlers.length) document.addEventListener('keydown', onKeydown)
    handlers.push(handler)
  })
  onBeforeUnmount(() => {
    const index = handlers.indexOf(handler)
    if (index >= 0) handlers.splice(index, 1)
    if (!handlers.length) document.removeEventListener('keydown', onKeydown)
  })
}
