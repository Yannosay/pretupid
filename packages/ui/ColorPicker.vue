<template>
  <div class="color-picker">
    <div class="color-picker__dyes" role="group" aria-label="Dye colors">
      <button
        v-for="dye in dyes"
        :key="dye.name"
        type="button"
        class="color-picker__dye"
        :class="{ 'color-picker__dye--active': hex === dye.color }"
        :style="{ backgroundColor: dye.color }"
        :title="dye.name"
        :aria-label="dye.name"
        @click="pickDye(dye.color)"
      />
    </div>

    <div
      ref="padRef"
      class="color-picker__pad"
      :style="{ backgroundColor: `hsl(${hue} 100% 50%)` }"
      role="slider"
      tabindex="0"
      aria-label="Saturation and brightness"
      :aria-valuetext="hex"
      @pointerdown="startPad"
      @keydown="onPadKey"
    >
      <span class="color-picker__pad-white" />
      <span class="color-picker__pad-black" />
      <span class="color-picker__thumb" :style="{ left: `${saturation * 100}%`, top: `${(1 - value) * 100}%`, backgroundColor: hex }" />
    </div>

    <div
      ref="hueRef"
      class="color-picker__hue"
      role="slider"
      tabindex="0"
      aria-label="Hue"
      aria-valuemin="0"
      aria-valuemax="360"
      :aria-valuenow="Math.round(hue)"
      @pointerdown="startHue"
      @keydown="onHueKey"
    >
      <span class="color-picker__hue-thumb" :style="{ left: `${(hue / 360) * 100}%` }" />
    </div>

    <div class="color-picker__row">
      <span class="color-picker__swatch"><span :style="{ backgroundColor: hex }" /></span>
      <label class="color-picker__hex">
        <span>#</span>
        <input
          :value="hexDraft"
          maxlength="6"
          spellcheck="false"
          autocomplete="off"
          aria-label="Hex color"
          @input="onHexInput"
          @blur="hexDraft = hex.slice(1)"
          @keydown.enter="($event.target as HTMLInputElement).blur()"
        />
      </label>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, ref, watch } from 'vue'

const props = defineProps<{ modelValue: string }>()
const emit = defineEmits<{ (e: 'update:modelValue', value: string): void }>()

const padRef = ref<HTMLElement | null>(null)
const hueRef = ref<HTMLElement | null>(null)
const hue = ref(0)
const saturation = ref(0)
const value = ref(0)
const hexDraft = ref('')
let dragging: 'pad' | 'hue' | null = null

function clamp(n: number, min = 0, max = 1): number {
  return Math.min(max, Math.max(min, n))
}

function parseHex(input: string): [number, number, number] | null {
  const clean = input.trim().replace(/^#/, '')
  const full = clean.length === 3 ? clean.split('').map((c) => c + c).join('') : clean
  if (!/^[0-9a-fA-F]{6}$/.test(full)) return null
  const num = parseInt(full, 16)
  return [(num >> 16) & 255, (num >> 8) & 255, num & 255]
}

function toHex(r: number, g: number, b: number): string {
  return `#${[r, g, b].map((v) => Math.round(v).toString(16).padStart(2, '0')).join('')}`
}

function hsvToRgb(h: number, s: number, v: number): [number, number, number] {
  const f = (n: number) => {
    const k = (n + h / 60) % 6
    return v - v * s * Math.max(0, Math.min(k, 4 - k, 1))
  }
  return [f(5) * 255, f(3) * 255, f(1) * 255]
}

function rgbToHsv(r: number, g: number, b: number): [number, number, number] {
  const rn = r / 255
  const gn = g / 255
  const bn = b / 255
  const max = Math.max(rn, gn, bn)
  const min = Math.min(rn, gn, bn)
  const d = max - min
  let h = 0
  if (d !== 0) {
    if (max === rn) h = 60 * (((gn - bn) / d) % 6)
    else if (max === gn) h = 60 * ((bn - rn) / d + 2)
    else h = 60 * ((rn - gn) / d + 4)
  }
  return [(h + 360) % 360, max === 0 ? 0 : d / max, max]
}

const dyes = [
  { name: 'White', color: '#f9fffe' },
  { name: 'Light Gray', color: '#9d9d97' },
  { name: 'Gray', color: '#474f52' },
  { name: 'Black', color: '#1d1d21' },
  { name: 'Brown', color: '#835432' },
  { name: 'Red', color: '#b02e26' },
  { name: 'Orange', color: '#f9801d' },
  { name: 'Yellow', color: '#fed83d' },
  { name: 'Lime', color: '#80c71f' },
  { name: 'Green', color: '#5e7c16' },
  { name: 'Cyan', color: '#169c9c' },
  { name: 'Light Blue', color: '#3ab3da' },
  { name: 'Blue', color: '#3c44aa' },
  { name: 'Purple', color: '#8932b8' },
  { name: 'Magenta', color: '#c74ebd' },
  { name: 'Pink', color: '#f38baa' }
]

const hex = computed(() => toHex(...hsvToRgb(hue.value, saturation.value, value.value)))

function syncFromProp(color: string) {
  const rgb = parseHex(color)
  if (!rgb) return
  if (toHex(...rgb) === hex.value) return
  const [h, s, v] = rgbToHsv(...rgb)
  if (s > 0 && v > 0) hue.value = h
  saturation.value = s
  value.value = v
  hexDraft.value = hex.value.slice(1)
}

watch(() => props.modelValue, syncFromProp, { immediate: true })

function commit() {
  hexDraft.value = hex.value.slice(1)
  if (hex.value.toLowerCase() !== props.modelValue.toLowerCase()) emit('update:modelValue', hex.value)
}

function updatePad(event: PointerEvent) {
  const rect = padRef.value?.getBoundingClientRect()
  if (!rect) return
  saturation.value = clamp((event.clientX - rect.left) / rect.width)
  value.value = clamp(1 - (event.clientY - rect.top) / rect.height)
  commit()
}

function updateHue(event: PointerEvent) {
  const rect = hueRef.value?.getBoundingClientRect()
  if (!rect) return
  hue.value = clamp((event.clientX - rect.left) / rect.width) * 360
  commit()
}

function onMove(event: PointerEvent) {
  if (dragging === 'pad') updatePad(event)
  else if (dragging === 'hue') updateHue(event)
}

function stopDrag() {
  dragging = null
  window.removeEventListener('pointermove', onMove)
  window.removeEventListener('pointerup', stopDrag)
  window.removeEventListener('pointercancel', stopDrag)
}

function beginDrag(kind: 'pad' | 'hue', event: PointerEvent) {
  event.preventDefault()
  ;(event.currentTarget as HTMLElement).focus()
  dragging = kind
  window.addEventListener('pointermove', onMove)
  window.addEventListener('pointerup', stopDrag)
  window.addEventListener('pointercancel', stopDrag)
  onMove(event)
}

function startPad(event: PointerEvent) {
  beginDrag('pad', event)
}

function startHue(event: PointerEvent) {
  beginDrag('hue', event)
}

function onPadKey(event: KeyboardEvent) {
  const step = event.shiftKey ? 0.1 : 0.02
  const moves: Record<string, [number, number]> = {
    ArrowLeft: [-step, 0],
    ArrowRight: [step, 0],
    ArrowUp: [0, step],
    ArrowDown: [0, -step]
  }
  const move = moves[event.key]
  if (!move) return
  event.preventDefault()
  saturation.value = clamp(saturation.value + move[0])
  value.value = clamp(value.value + move[1])
  commit()
}

function onHueKey(event: KeyboardEvent) {
  const step = event.shiftKey ? 15 : 3
  if (event.key !== 'ArrowLeft' && event.key !== 'ArrowRight') return
  event.preventDefault()
  hue.value = (hue.value + (event.key === 'ArrowRight' ? step : -step) + 360) % 360
  commit()
}

function pickDye(color: string) {
  syncFromProp(color)
  commit()
}

function onHexInput(event: Event) {
  const input = event.target as HTMLInputElement
  const cleaned = input.value.replace(/[^0-9a-fA-F]/g, '').slice(0, 6)
  input.value = cleaned
  hexDraft.value = cleaned
  const rgb = parseHex(cleaned)
  if (rgb && cleaned.length === 6) {
    const [h, s, v] = rgbToHsv(...rgb)
    if (s > 0 && v > 0) hue.value = h
    saturation.value = s
    value.value = v
    emit('update:modelValue', toHex(...rgb))
  }
}

onBeforeUnmount(stopDrag)
</script>

<style scoped>
.color-picker {
  --mc-slot: #8b8b8b;
  --mc-slot-dark: #373737;
  --mc-slot-light: #ffffff;
  display: flex;
  flex-direction: column;
  gap: 0.75rem;
  width: 100%;
  max-width: 296px;
  user-select: none;
  image-rendering: pixelated;
}

.color-picker__dyes {
  display: grid;
  grid-template-columns: repeat(8, 1fr);
  gap: 4px;
  padding: 4px;
  background-color: var(--mc-slot);
  box-shadow: inset 2px 2px 0 var(--mc-slot-dark), inset -2px -2px 0 var(--mc-slot-light);
}

.color-picker__dye {
  aspect-ratio: 1;
  padding: 0;
  border: none;
  cursor: pointer;
  box-shadow: inset 2px 2px 0 rgba(255, 255, 255, 0.35), inset -2px -2px 0 rgba(0, 0, 0, 0.35);
  transition: transform 0.08s ease;
}

.color-picker__dye:hover {
  transform: translateY(-1px);
  outline: 2px solid #ffffff;
  outline-offset: -1px;
}

.color-picker__dye--active {
  outline: 2px solid #ffffff;
  outline-offset: -1px;
  box-shadow: inset 0 0 0 3px rgba(0, 0, 0, 0.55);
}

.color-picker__dye:focus-visible {
  outline: 2px solid var(--text-primary, #fff);
  outline-offset: 1px;
}

.color-picker__pad {
  position: relative;
  height: 150px;
  border: 3px solid var(--mc-slot);
  box-shadow: 0 0 0 2px var(--mc-slot-dark);
  cursor: crosshair;
  touch-action: none;
  outline: none;
}

.color-picker__pad:focus-visible,
.color-picker__hue:focus-visible {
  box-shadow: 0 0 0 2px var(--text-primary, #fff);
}

.color-picker__pad-white,
.color-picker__pad-black {
  position: absolute;
  inset: 0;
  pointer-events: none;
}

.color-picker__pad-white {
  background: linear-gradient(90deg, #fff, rgba(255, 255, 255, 0));
}

.color-picker__pad-black {
  background: linear-gradient(0deg, #000, rgba(0, 0, 0, 0));
}

.color-picker__thumb {
  position: absolute;
  width: 14px;
  height: 14px;
  border: 2px solid #ffffff;
  box-shadow: 0 0 0 2px #000000;
  transform: translate(-50%, -50%);
  pointer-events: none;
}

.color-picker__hue {
  position: relative;
  height: 18px;
  margin: 0 5px;
  border: 2px solid var(--mc-slot-dark);
  background: linear-gradient(90deg, #f00, #ff0 17%, #0f0 33%, #0ff 50%, #00f 67%, #f0f 83%, #f00);
  cursor: pointer;
  touch-action: none;
  outline: none;
}

.color-picker__hue-thumb {
  position: absolute;
  top: 50%;
  width: 10px;
  height: 26px;
  background-color: #c6c6c6;
  border: 2px solid #000000;
  box-shadow: inset 2px 2px 0 #ffffff, inset -2px -2px 0 #555555;
  transform: translate(-50%, -50%);
  pointer-events: none;
}

.color-picker__row {
  display: flex;
  align-items: center;
  gap: 0.6rem;
}

.color-picker__swatch {
  display: flex;
  width: 36px;
  height: 36px;
  flex-shrink: 0;
  padding: 4px;
  background-color: var(--mc-slot);
  box-shadow: inset 2px 2px 0 var(--mc-slot-dark), inset -2px -2px 0 var(--mc-slot-light);
}

.color-picker__swatch span {
  flex: 1;
  box-shadow: inset 2px 2px 0 rgba(255, 255, 255, 0.3), inset -2px -2px 0 rgba(0, 0, 0, 0.3);
}

.color-picker__hex {
  display: flex;
  flex: 1;
  min-width: 0;
  align-items: center;
  gap: 0.2rem;
  height: 36px;
  padding: 0 0.7rem;
  font-family: var(--font-pixel, monospace);
  font-size: 0.85rem;
  color: #a0a0a0;
  background-color: #000000;
  border: 2px solid #a0a0a0;
}

.color-picker__hex:focus-within {
  border-color: #ffffff;
}

.color-picker__hex input {
  flex: 1;
  width: 100%;
  min-width: 0;
  font: inherit;
  text-transform: uppercase;
  color: #ffffff;
  background: none;
  border: none;
  outline: none;
}
</style>
