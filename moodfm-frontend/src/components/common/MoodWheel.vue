<template>
  <div
    ref="wheelRef"
    class="mood-wheel"
    :style="{ width: size + 'px', height: size + 'px' }"
    @mousedown="onMouseDown"
    @touchstart.prevent="onTouchStart"
  >
    <svg
      :width="size"
      :height="size"
      :viewBox="`0 0 ${size} ${size}`"
      style="display: block; border-radius: 50%; overflow: hidden"
      aria-hidden="true"
    >
      <defs>
        <radialGradient id="wheel-radial" cx="50%" cy="50%" r="50%">
          <stop offset="0%"   stop-color="#fff" stop-opacity="1" />
          <stop offset="100%" stop-color="#fff" stop-opacity="0" />
        </radialGradient>
      </defs>

      <g>
        <circle :cx="cx" :cy="cy" :r="r" fill="var(--mood-d)" />
        <circle
          v-for="(seg, i) in segments"
          :key="i"
          :cx="cx" :cy="cy" :r="r"
          :fill="seg.color"
          :style="{ mixBlendMode: 'screen', opacity: seg.opacity }"
          :transform="`rotate(${seg.rotate} ${cx} ${cy})`"
        />
        <circle :cx="cx" :cy="cy" :r="r" fill="url(#wheel-radial)" />
        <circle :cx="cx" :cy="cy" :r="r" fill="none" stroke="rgba(0,0,0,0.18)" stroke-width="1" />
      </g>

      <circle :cx="cx" :cy="cy" :r="r * 0.33" fill="none" stroke="rgba(255,255,255,0.18)" stroke-width="0.8" />
      <circle :cx="cx" :cy="cy" :r="r * 0.66" fill="none" stroke="rgba(255,255,255,0.14)" stroke-width="0.8" />

      <circle
        :cx="dotX"
        :cy="dotY"
        :r="dotR"
        fill="white"
        stroke="rgba(0,0,0,0.25)"
        stroke-width="1.5"
        style="cursor: grab; filter: drop-shadow(0 2px 6px rgba(0,0,0,0.35))"
      />
      <circle
        :cx="dotX"
        :cy="dotY"
        :r="dotR * 0.45"
        :fill="dotColor"
      />
    </svg>

    <div v-if="moodLabel" class="wheel-label">
      <span class="mono" style="font-size: 10px; letter-spacing: .14em; text-transform: uppercase">{{ moodLabel }}</span>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onUnmounted } from 'vue'

const props = withDefaults(defineProps<{
  size?: number
}>(), { size: 340 })

const emit = defineEmits<{
  (e: 'change', x: number, y: number): void
}>()

const wheelRef = ref<HTMLElement | null>(null)

const cx = computed(() => props.size / 2)
const cy = computed(() => props.size / 2)
const r  = computed(() => props.size / 2 - 2)
const dotR = computed(() => Math.max(10, props.size * 0.038))

const dotNX = ref(0.18)
const dotNY = ref(-0.25)

const dotX = computed(() => cx.value + dotNX.value * r.value)
const dotY = computed(() => cy.value + dotNY.value * r.value)

const dotColor = computed(() => {
  const x = dotNX.value
  const y = dotNY.value
  return `hsl(${Math.round(((x + 1) / 2) * 360)}, 80%, ${Math.round(50 + y * -20)}%)`
})

const moodLabel = computed(() => {
  const x = dotNX.value
  const y = dotNY.value
  if (y < -0.4 && x > 0.2)  return 'energetic · 活跃'
  if (y < -0.4 && x < -0.2) return 'calm · 平静'
  if (y > 0.4  && x > 0.2)  return 'melancholy · 忧郁'
  if (y > 0.4  && x < -0.2) return 'focused · 专注'
  if (Math.abs(x) < 0.2 && Math.abs(y) < 0.2) return 'balanced · 平衡'
  return ''
})

const segments = computed(() => [
  { color: 'var(--mood-a)', rotate: 0,   opacity: 0.7 },
  { color: 'var(--mood-b)', rotate: 90,  opacity: 0.6 },
  { color: 'var(--mood-c)', rotate: 180, opacity: 0.65 },
  { color: 'var(--mood-d)', rotate: 270, opacity: 0.6 },
])

let dragging = false

function clampDot(nx: number, ny: number) {
  const dist = Math.hypot(nx, ny)
  if (dist > 1) {
    nx = nx / dist
    ny = ny / dist
  }
  return { nx, ny }
}

function pointerToNorm(clientX: number, clientY: number) {
  const el = wheelRef.value
  if (!el) return { nx: dotNX.value, ny: dotNY.value }
  const rect = el.getBoundingClientRect()
  const relX = clientX - rect.left - rect.width  / 2
  const relY = clientY - rect.top  - rect.height / 2
  const nr = rect.width / 2
  return clampDot(relX / nr, relY / nr)
}

function onMouseDown(e: MouseEvent) {
  dragging = true
  updateFromPointer(e.clientX, e.clientY)
  window.addEventListener('mousemove', onMouseMove)
  window.addEventListener('mouseup', onMouseUp)
}

function onMouseMove(e: MouseEvent) {
  if (!dragging) return
  updateFromPointer(e.clientX, e.clientY)
}

function onMouseUp() {
  dragging = false
  window.removeEventListener('mousemove', onMouseMove)
  window.removeEventListener('mouseup', onMouseUp)
}

function onTouchStart(e: TouchEvent) {
  dragging = true
  const t = e.touches[0]
  updateFromPointer(t.clientX, t.clientY)
  window.addEventListener('touchmove', onTouchMove, { passive: false })
  window.addEventListener('touchend', onTouchEnd)
}

function onTouchMove(e: TouchEvent) {
  e.preventDefault()
  if (!dragging) return
  const t = e.touches[0]
  updateFromPointer(t.clientX, t.clientY)
}

function onTouchEnd() {
  dragging = false
  window.removeEventListener('touchmove', onTouchMove)
  window.removeEventListener('touchend', onTouchEnd)
}

function updateFromPointer(clientX: number, clientY: number) {
  const { nx, ny } = pointerToNorm(clientX, clientY)
  dotNX.value = nx
  dotNY.value = ny
  emit('change', nx, ny)
}

onUnmounted(() => {
  window.removeEventListener('mousemove', onMouseMove)
  window.removeEventListener('mouseup', onMouseUp)
  window.removeEventListener('touchmove', onTouchMove)
  window.removeEventListener('touchend', onTouchEnd)
})
</script>

<style scoped>
.mood-wheel {
  position: relative;
  user-select: none;
  cursor: crosshair;
  flex-shrink: 0;
}

.wheel-label {
  position: absolute;
  bottom: -24px;
  left: 0;
  right: 0;
  text-align: center;
  color: var(--ink-2);
  pointer-events: none;
}
</style>
