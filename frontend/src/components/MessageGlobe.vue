<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import createGlobe from 'cobe'
import type { Location, Message } from '../message/api'
import { countryName } from '../message/api'

const props = defineProps<{ messages: Message[]; selected: Location | null; locked: boolean }>()
const emit = defineEmits<{ pick: [location: Location]; open: [message: Message] }>()
const host = ref<HTMLDivElement>()
const canvas = ref<HTMLCanvasElement>()
const unavailable = ref(false)
const hover = ref(false)
const keyboardFocus = ref(false)
const view = ref({ phi: 4.2, theta: 0.12, scale: 1 })
const motion = window.matchMedia('(prefers-reduced-motion: reduce)')
const radians = Math.PI / 180
let globe: ReturnType<typeof createGlobe> | undefined
let observer: ResizeObserver | undefined
let frame = 0
let lastTime = 0
let velocity = 0.08
let pointer: { id: number; x: number; startX: number; startY: number; moved: boolean } | null = null
let transition: { from: typeof view.value; to: typeof view.value; start: number } | null = null

// Matches COBE 2's orthographic sphere (radius 0.8) and phi/theta rotation.
function project(location: Location) {
  const lat = location.latitude * radians, lon = location.longitude * radians
  const x = Math.cos(lat) * Math.cos(lon), y = Math.sin(lat), z = -Math.cos(lat) * Math.sin(lon)
  const { phi, theta, scale } = view.value
  const cp = Math.cos(phi), sp = Math.sin(phi), ct = Math.cos(theta), st = Math.sin(theta)
  return {
    x: 50 + (cp * x + sp * z) * 40 * scale,
    y: 50 - (sp * st * x + ct * y - cp * st * z) * 40 * scale,
    visible: -sp * ct * x + st * y + cp * ct * z > 0.04,
  }
}
const pins = computed(() => props.messages.map(message => ({ message, ...project(message) })))
const selectedPin = computed(() => props.selected ? project(props.selected) : null)
function unproject(clientX: number, clientY: number): Location | null {
  if (!canvas.value) return null
  const rect = canvas.value.getBoundingClientRect()
  const { phi, theta, scale } = view.value
  const x = ((clientX - rect.left) / rect.width * 2 - 1) / scale
  const y = (1 - (clientY - rect.top) / rect.height * 2) / scale
  const radius = x * x + y * y
  if (radius > 0.64) return null
  const z = Math.sqrt(0.64 - radius)
  const cp = Math.cos(phi), sp = Math.sin(phi), ct = Math.cos(theta), st = Math.sin(theta)
  const worldX = cp * x + sp * st * y - sp * ct * z
  const worldY = ct * y + st * z
  const worldZ = sp * x - cp * st * y + cp * ct * z
  return {
    latitude: Math.asin(Math.max(-1, Math.min(1, worldY / 0.8))) / radians,
    longitude: Math.atan2(-worldZ, worldX) / radians,
  }
}
watch(() => props.selected, location => {
  pointer = null
  const current = view.value
  const desired = location ? -location.longitude * radians - Math.PI / 2 : current.phi
  const difference = Math.atan2(Math.sin(desired - current.phi), Math.cos(desired - current.phi))
  const to = { phi: current.phi + difference, theta: location ? location.latitude * radians : 0.12, scale: location ? 1.3 : 1 }
  if (motion.matches) { view.value = to; transition = null }
  else transition = { from: { ...current }, to, start: performance.now() }
})
function down(event: PointerEvent) {
  if (event.button !== 0 || props.locked || transition) return
  pointer = { id: event.pointerId, x: event.clientX, startX: event.clientX, startY: event.clientY, moved: false }
  canvas.value?.setPointerCapture(event.pointerId)
}
function move(event: PointerEvent) {
  hover.value = unproject(event.clientX, event.clientY) !== null
  if (!pointer || pointer.id !== event.pointerId) return
  if (Math.hypot(event.clientX - pointer.startX, event.clientY - pointer.startY) > 5) pointer.moved = true
  if (pointer.moved) view.value.phi += (event.clientX - pointer.x) * 0.005
  pointer.x = event.clientX
}
function up(event: PointerEvent) {
  if (!pointer || pointer.id !== event.pointerId) return
  const click = !pointer.moved
  pointer = null
  canvas.value?.releasePointerCapture(event.pointerId)
  if (click && !props.locked) {
    const location = unproject(event.clientX, event.clientY)
    if (location) emit('pick', location)
  }
}
function key(event: KeyboardEvent) {
  if (props.locked || transition) return
  if (event.key === 'ArrowLeft' || event.key === 'ArrowRight') {
    event.preventDefault(); view.value.phi += event.key === 'ArrowLeft' ? -0.15 : 0.15
  } else if (event.key === 'Enter' && canvas.value) {
    event.preventDefault()
    const rect = canvas.value.getBoundingClientRect()
    const location = unproject(rect.left + rect.width / 2, rect.top + rect.height / 2)
    if (location) emit('pick', location)
  }
}
onMounted(() => {
  const element = canvas.value
  if (!element || !host.value) return
  try {
    if (!element.getContext('webgl2') && !element.getContext('webgl')) throw new Error('WebGL unavailable')
    const width = host.value.clientWidth
    globe = createGlobe(element, {
      devicePixelRatio: Math.min(window.devicePixelRatio || 1, 2), width, height: width,
      ...view.value, dark: 1, diffuse: 1.5, mapSamples: 24000, mapBrightness: 8,
      baseColor: [0.48, 0.53, 0.45], markerColor: [0.78, 1, 0.36], glowColor: [0.08, 0.12, 0.06], markers: [],
    })
    observer = new ResizeObserver(([entry]) => {
      if (entry) globe?.update({ width: entry.contentRect.width, height: entry.contentRect.width })
    })
    observer.observe(host.value)
    const animate = (time: number) => {
      const delta = Math.min((time - (lastTime || time)) / 1000, 0.05)
      lastTime = time
      if (!document.hidden) {
        const target = hover.value || keyboardFocus.value || props.locked || pointer || motion.matches ? 0 : 0.08
        velocity += (target - velocity) * (1 - Math.exp(-delta * 5))
        if (transition) {
          const progress = Math.min((time - transition.start) / 1100, 1)
          const ease = 1 - (1 - progress) ** 4
          for (const property of ['phi', 'theta', 'scale'] as const) {
            view.value[property] = transition.from[property] + (transition.to[property] - transition.from[property]) * ease
          }
          if (progress === 1) transition = null
        } else if (!props.locked && !pointer && !motion.matches) view.value.phi += velocity * delta
        globe?.update(view.value)
      }
      frame = requestAnimationFrame(animate)
    }
    frame = requestAnimationFrame(animate)
  } catch { unavailable.value = true; globe?.destroy() }
})
onBeforeUnmount(() => { cancelAnimationFrame(frame); observer?.disconnect(); globe?.destroy() })
</script>

<template>
  <div ref="host" class="globe-host" @pointerleave="hover = false">
    <canvas v-show="!unavailable" ref="canvas" class="globe" tabindex="0" role="application"
      aria-label="交互地球：拖动旋转，点击选点留言；键盘左右键旋转，回车在球心选点"
      @pointerdown="down" @pointermove="move" @pointerup="up" @pointercancel="pointer = null"
      @lostpointercapture="pointer = null" @keydown="key" @focus="keyboardFocus = true" @blur="keyboardFocus = false" />
    <div v-if="!unavailable" class="pin-layer">
      <template v-for="pin in pins" :key="pin.message.id">
        <button v-if="pin.visible" type="button" class="message-pin"
          :style="{ left: `${pin.x}%`, top: `${pin.y}%` }" :disabled="locked"
          :aria-label="`查看留言：${pin.message.text}`" @pointerenter="hover = true"
          @focus="keyboardFocus = true" @blur="keyboardFocus = false" @click.stop="emit('open', pin.message)">
          <span class="pin-dot"></span>
          <span class="pin-preview"><span>{{ pin.message.text }}</span><small>{{ countryName(pin.message.country) }} · 点击打开</small></span>
        </button>
      </template>
      <span v-if="selectedPin?.visible" class="selected-pin" :style="{ left: `${selectedPin.x}%`, top: `${selectedPin.y}%` }"></span>
    </div>
    <div v-if="unavailable" class="globe-fallback" role="status">当前浏览器无法显示交互地球，请启用 WebGL 后重新打开。</div>
  </div>
</template>
