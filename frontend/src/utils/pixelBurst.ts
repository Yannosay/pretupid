const COLORS = ['#4ade80', '#86efac', '#22c55e', '#facc15', '#ffffff']

export function pixelBurst(origin: HTMLElement, count = 18, colors: string[] = COLORS) {
  if (window.matchMedia('(prefers-reduced-motion: reduce)').matches) return
  const bounds = origin.getBoundingClientRect()
  const centerX = bounds.left + bounds.width / 2
  const centerY = bounds.top + bounds.height / 2
  for (let i = 0; i < count; i++) {
    const pixel = document.createElement('span')
    const size = 4 + Math.round(Math.random() * 4)
    pixel.style.cssText = `position:fixed;left:${centerX}px;top:${centerY}px;width:${size}px;height:${size}px;background:${colors[i % colors.length]};pointer-events:none;z-index:9999;image-rendering:pixelated`
    document.body.appendChild(pixel)
    const angle = (Math.PI * 2 * i) / count + Math.random() * 0.4
    const distance = 40 + Math.random() * 50
    const dx = Math.cos(angle) * distance
    const dy = Math.sin(angle) * distance - 20
    pixel
      .animate(
        [
          { transform: 'translate(-50%, -50%) scale(1)', opacity: 1 },
          { transform: `translate(calc(-50% + ${dx}px), calc(-50% + ${dy + 30}px)) scale(0.4)`, opacity: 0 }
        ],
        { duration: 550 + Math.random() * 250, easing: 'cubic-bezier(0.22, 1, 0.36, 1)' }
      )
      .finished.finally(() => pixel.remove())
  }
}
