<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref } from 'vue'
import createGlobe from 'cobe'

const canvas = ref<HTMLCanvasElement>()
const unavailable = ref(false)
let globe: ReturnType<typeof createGlobe> | undefined
let observer: ResizeObserver | undefined
let frame = 0
let phi = 4.2
let lastTime = 0
let pointerX: number | undefined
const motion = window.matchMedia('(prefers-reduced-motion: reduce)')

function startDrag(event: PointerEvent) {
  if (event.button !== 0) return
  pointerX = event.clientX
  canvas.value?.setPointerCapture(event.pointerId)
}
function drag(event: PointerEvent) {
  if (pointerX === undefined) return
  phi += (event.clientX - pointerX) * 0.005
  pointerX = event.clientX
  globe?.update({ phi })
}
function endDrag() { pointerX = undefined }
function turn(event: KeyboardEvent) {
  if (event.key !== 'ArrowLeft' && event.key !== 'ArrowRight') return
  event.preventDefault()
  phi += event.key === 'ArrowLeft' ? -0.15 : 0.15
  globe?.update({ phi })
}

onMounted(() => {
  const element = canvas.value
  if (!element) return
  const pixelRatio = Math.min(window.devicePixelRatio || 1, 2)
  const width = element.getBoundingClientRect().width
  try {
    if (!element.getContext('webgl2', { alpha: true, premultipliedAlpha: true }) && !element.getContext('webgl', { alpha: true, premultipliedAlpha: true })) throw new Error('WebGL unavailable')
    globe = createGlobe(element, {
      devicePixelRatio: pixelRatio,
      width: width * pixelRatio,
      height: width * pixelRatio,
      phi,
      theta: 0.12,
      dark: 1,
      diffuse: 1.5,
      mapSamples: 24000,
      mapBrightness: 8,
      baseColor: [0.48, 0.53, 0.45],
      markerColor: [0.78, 1, 0.36],
      glowColor: [0.08, 0.12, 0.06],
      markers: [
        { location: [1.35, 103.82], size: 0.035 },
        { location: [35.68, 139.69], size: 0.025 },
        { location: [51.51, -0.13], size: 0.025 },
        { location: [40.71, -74.01], size: 0.025 },
        { location: [-33.87, 151.21], size: 0.025 },
      ],
    })
    observer = new ResizeObserver(([entry]) => {
      if (!entry) return
      const size = entry.contentRect.width * pixelRatio
      globe?.update({ width: size, height: size })
    })
    observer.observe(element)
    const animate = (time: number) => {
      if (!motion.matches && !document.hidden && pointerX === undefined) {
        phi += Math.min(time - (lastTime || time), 50) * 0.00008
        globe?.update({ phi })
      }
      lastTime = time
      frame = requestAnimationFrame(animate)
    }
    frame = requestAnimationFrame(animate)
  } catch {
    unavailable.value = true
    globe?.destroy()
  }
})

onBeforeUnmount(() => {
  cancelAnimationFrame(frame)
  observer?.disconnect()
  globe?.destroy()
})
</script>

<template>
  <main class="landing">
    <header class="masthead">
      <a class="wordmark" href="./" aria-label="Java Web home"><span class="brand-dot"></span>JAVA / WEB</a>
      <span class="edition">A WORLD OF POSSIBILITIES</span>
    </header>

    <section class="hero" aria-labelledby="headline">
      <div class="globe-stage">
        <div class="orbit orbit-one" aria-hidden="true"></div>
        <div class="orbit orbit-two" aria-hidden="true"></div>
        <span class="coordinate coordinate-left" aria-hidden="true">01° 21′ N<br>103° 49′ E</span>
        <span class="coordinate coordinate-right" aria-hidden="true">ONE WORLD<br>ENDLESS CONNECTIONS</span>
        <canvas v-show="!unavailable" ref="canvas" class="globe" tabindex="0"
          role="img" aria-label="Interactive globe. Drag or use left and right arrow keys to rotate."
          @pointerdown="startDrag" @pointermove="drag" @pointerup="endDrag"
          @pointercancel="endDrag" @lostpointercapture="endDrag" @keydown="turn"></canvas>
        <div v-if="unavailable" class="globe-fallback" role="img" aria-label="Globe illustration">
          <span>◎</span>
        </div>
      </div>
      <div class="caption">
        <p class="eyebrow"><span class="status-dot"></span> SMALL CODE. BIG CONNECTIONS.</p>
        <h1 id="headline">Link the world<br>with <span>java web</span><span class="cursor" aria-hidden="true">_</span></h1>
      </div>
    </section>

    <footer class="footer"><span>BUILT TO CONNECT.</span><span>{{ unavailable ? 'THE WORLD IS STILL HERE.' : 'DRAG TO EXPLORE ↔' }}</span><span>JAVA WEB / 001</span></footer>
  </main>
</template>
