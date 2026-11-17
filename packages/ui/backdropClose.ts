import type { Directive } from 'vue'

type CloseHandler = () => void

interface BackdropState {
  handler: CloseHandler | undefined
  pressedOnBackdrop: boolean
  onPointerDown: (event: PointerEvent) => void
  onClick: (event: MouseEvent) => void
}

const states = new WeakMap<HTMLElement, BackdropState>()

export const vBackdropClose: Directive<HTMLElement, CloseHandler | undefined> = {
  mounted(el, binding) {
    const state: BackdropState = {
      handler: binding.value,
      pressedOnBackdrop: false,
      onPointerDown: (event) => {
        state.pressedOnBackdrop = event.target === el
      },
      onClick: (event) => {
        const shouldClose = state.pressedOnBackdrop && event.target === el
        state.pressedOnBackdrop = false
        if (shouldClose) state.handler?.()
      }
    }
    states.set(el, state)
    el.addEventListener('pointerdown', state.onPointerDown)
    el.addEventListener('click', state.onClick)
  },
  updated(el, binding) {
    const state = states.get(el)
    if (state) state.handler = binding.value
  },
  beforeUnmount(el) {
    const state = states.get(el)
    if (!state) return
    el.removeEventListener('pointerdown', state.onPointerDown)
    el.removeEventListener('click', state.onClick)
    states.delete(el)
  }
}
