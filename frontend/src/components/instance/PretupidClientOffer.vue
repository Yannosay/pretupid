<template>
  <button
    type="button"
    role="switch"
    class="client-offer"
    :class="{ 'client-offer--on': modelValue }"
    :aria-checked="modelValue"
    :disabled="disabled"
    @click="toggle"
  >
    <span class="client-offer__frame" aria-hidden="true" />
    <span ref="logoRef" class="client-offer__logo">
      <img :src="logo" alt="" />
      <span class="client-offer__spark client-offer__spark--a" />
      <span class="client-offer__spark client-offer__spark--b" />
      <span class="client-offer__spark client-offer__spark--c" />
    </span>
    <span class="client-offer__text">
      <span class="client-offer__title">
        Install Pretupid Client
        <span class="client-offer__tag">Recommended</span>
      </span>
      <span class="client-offer__hint">Mod menu, HUD, FPS boost, zoom, cosmetics and the Pretupid badge. You can remove it any time.</span>
    </span>
    <span class="client-offer__switch" aria-hidden="true" />
  </button>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import logo from '@shared/branding/pretupid_logo_512x.png'
import { pixelBurst } from '@/utils/pixelBurst'

const props = defineProps<{ modelValue: boolean; disabled?: boolean }>()
const emit = defineEmits<{ (e: 'update:modelValue', value: boolean): void }>()

const logoRef = ref<HTMLElement | null>(null)

function accentColors(): string[] {
  const accent = getComputedStyle(document.documentElement).getPropertyValue('--accent').trim() || '#4a9eff'
  return [accent, '#ffffff', accent, '#facc15']
}

function toggle() {
  const next = !props.modelValue
  emit('update:modelValue', next)
  if (next && logoRef.value) pixelBurst(logoRef.value, 22, accentColors())
}
</script>

<style scoped lang="scss">
.client-offer {
  position: relative;
  display: flex;
  align-items: center;
  gap: 0.9rem;
  width: 100%;
  padding: 0.85rem 1rem;
  text-align: left;
  font: inherit;
  color: var(--text-primary);
  background-color: var(--bg-tertiary);
  border: 1px solid var(--border-color);
  border-radius: var(--edge-radius);
  cursor: pointer;
  isolation: isolate;
  transition: background-color var(--transition-normal), border-color var(--transition-normal), box-shadow var(--transition-normal), transform var(--transition-fast);

  &:hover:not(:disabled) {
    border-color: var(--border-strong);
  }

  &:active:not(:disabled) {
    transform: scale(0.995);
  }

  &:focus-visible {
    outline: 2px solid var(--text-primary);
    outline-offset: 2px;
  }

  &:disabled {
    opacity: 0.6;
    cursor: not-allowed;
  }
}

.client-offer__frame {
  position: absolute;
  inset: -1px;
  z-index: -1;
  border-radius: inherit;
  opacity: 0;
  pointer-events: none;
  padding: 2px;
  background: linear-gradient(110deg, var(--accent), #ffffff 18%, var(--accent) 36%, color-mix(in srgb, var(--accent) 40%, #facc15) 60%, var(--accent));
  background-size: 250% 100%;
  mask: linear-gradient(#000 0 0) content-box exclude, linear-gradient(#000 0 0);
  transition: opacity var(--transition-normal);
}

.client-offer__logo {
  position: relative;
  display: inline-flex;
  flex-shrink: 0;
  width: 44px;
  height: 44px;

  img {
    width: 100%;
    height: 100%;
    image-rendering: pixelated;
    filter: grayscale(0.85) brightness(0.8);
    transition: filter var(--transition-normal), transform var(--transition-normal);
  }
}

.client-offer__spark {
  position: absolute;
  width: 4px;
  height: 4px;
  background-color: #ffffff;
  opacity: 0;
  pointer-events: none;
}

.client-offer__spark--a {
  top: -4px;
  right: -2px;
}

.client-offer__spark--b {
  bottom: 2px;
  left: -6px;
  background-color: var(--accent);
}

.client-offer__spark--c {
  top: 40%;
  right: -8px;
  background-color: #facc15;
}

.client-offer__text {
  display: flex;
  flex: 1;
  flex-direction: column;
  gap: 0.25rem;
  min-width: 0;
}

.client-offer__title {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 0.5rem;
  font-family: var(--font-pixel);
  font-size: 0.92rem;
}

.client-offer__tag {
  padding: 0.1rem 0.35rem;
  font-size: 0.58rem;
  letter-spacing: 0.04em;
  text-transform: uppercase;
  color: var(--accent);
  border: 1px solid color-mix(in srgb, var(--accent) 55%, transparent);
  border-radius: var(--edge-radius);
}

.client-offer__hint {
  font-size: 0.85rem;
  line-height: 1.35;
  color: var(--text-secondary);
}

.client-offer__switch {
  position: relative;
  flex-shrink: 0;
  width: 42px;
  height: 24px;
  background-color: var(--bg-tertiary);
  border: 1px solid var(--border-color);
  border-radius: var(--edge-radius);
  transition: background-color var(--transition-fast), border-color var(--transition-fast);

  &::after {
    content: '';
    position: absolute;
    top: 2px;
    left: 2px;
    width: 18px;
    height: 18px;
    background-color: var(--text-secondary);
    border-radius: var(--edge-radius);
    transition: transform var(--transition-normal), background-color var(--transition-fast);
  }
}

.client-offer--on {
  background:
    radial-gradient(circle at 30px 50%, color-mix(in srgb, var(--accent) 30%, transparent), transparent 140px),
    linear-gradient(110deg, color-mix(in srgb, var(--accent) 16%, transparent), transparent 75%),
    var(--bg-tertiary);
  border-color: transparent;
  box-shadow: 0 0 22px color-mix(in srgb, var(--accent) 28%, transparent);

  &:hover:not(:disabled) {
    border-color: transparent;
  }

  .client-offer__frame {
    opacity: 1;
    animation: client-offer-sheen 3.2s linear infinite;
  }

  .client-offer__logo img {
    filter: drop-shadow(0 0 10px color-mix(in srgb, var(--accent) 70%, transparent));
    animation: client-offer-pop 0.45s cubic-bezier(0.34, 1.56, 0.64, 1), client-offer-float 3s ease-in-out 0.45s infinite;
  }

  .client-offer__spark {
    animation: client-offer-twinkle 2.4s steps(2) infinite;
  }

  .client-offer__spark--b {
    animation-delay: 0.8s;
  }

  .client-offer__spark--c {
    animation-delay: 1.6s;
  }

  .client-offer__tag {
    color: var(--accent-text);
    background-color: var(--accent);
    border-color: var(--accent);
  }

  .client-offer__switch {
    background-color: var(--accent);
    border-color: var(--accent);

    &::after {
      transform: translateX(18px);
      background-color: var(--accent-text);
    }
  }
}

@keyframes client-offer-sheen {
  from {
    background-position: 100% 0;
  }
  to {
    background-position: -150% 0;
  }
}

@keyframes client-offer-pop {
  0% {
    transform: scale(0.7) rotate(-8deg);
  }
  100% {
    transform: scale(1) rotate(0);
  }
}

@keyframes client-offer-float {
  50% {
    transform: translateY(-3px);
  }
}

@keyframes client-offer-twinkle {
  0%,
  100% {
    opacity: 0;
  }
  50% {
    opacity: 1;
  }
}

@media (prefers-reduced-motion: reduce) {
  .client-offer--on .client-offer__frame,
  .client-offer--on .client-offer__logo img,
  .client-offer--on .client-offer__spark {
    animation: none;
  }
}
</style>
