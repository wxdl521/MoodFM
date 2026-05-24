<template>
  <div
    ref="wheelRef"
    class="mood-wheel"
    :style="{ width: size + 'px', height: size + 'px' }"
    role="application"
    tabindex="0"
    :aria-label="ariaLabel"
    @mousedown="onMouseDown"
    @touchstart.prevent="onTouchStart"
    @keydown="onKeyDown"
  >
    <!-- Colour disc: conic hue ring + radial saturation overlay -->
    <div class="wheel-disc">
      <div class="wheel-hue" />
      <div class="wheel-light" />
    </div>

    <!-- SVG layer: rings + draggable dot -->
    <svg
      class="wheel-svg"
      :width="size"
      :height="size"
      :viewBox="`0 0 ${size} ${size}`"
      aria-hidden="true"
    >
      <circle :cx="cx" :cy="cy" :r="r * 0.33" fill="none" stroke="rgba(255,255,255,0.22)" stroke-width="0.8" />
      <circle :cx="cx" :cy="cy" :r="r * 0.66" fill="none" stroke="rgba(255,255,255,0.16)" stroke-width="0.8" />
      <circle :cx="cx" :cy="cy" :r="r"         fill="none" stroke="rgba(0,0,0,0.18)"       stroke-width="1.5" />

      <circle
        :cx="dotX" :cy="dotY" :r="dotR"
        fill="white" stroke="rgba(0,0,0,0.28)" stroke-width="1.5"
        style="cursor:grab;filter:drop-shadow(0 2px 8px rgba(0,0,0,0.4))"
      />
      <circle :cx="dotX" :cy="dotY" :r="dotR * 0.44" :fill="dotColor" />
    </svg>

    <!-- Label -->
    <div v-if="moodLabel" class="wheel-label">
      <span class="mono" style="font-size:10px;letter-spacing:.14em;text-transform:uppercase">{{ moodLabel }}</span>
    </div>

    <!-- First-visit discoverability hint (decorative, non-focusable) -->
    <Transition name="hint-fade">
      <div v-if="showHint" class="wheel-hint" aria-hidden="true">
        拖动选择此刻心情
      </div>
    </Transition>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted } from 'vue'

const props = withDefaults(defineProps<{
  size?: number
}>(), { size: 340 })

const emit = defineEmits<{
  (e: 'change', x: number, y: number): void
}>()

const wheelRef = ref<HTMLElement | null>(null)

// Geometry helpers
const cx = computed(() => props.size / 2)
const cy = computed(() => props.size / 2)
const r  = computed(() => props.size / 2 - 2)
const dotR = computed(() => Math.max(10, props.size * 0.038))

// Dot position (normalised -1..1, starts slightly off-centre)
const INITIAL_NX = 0.18
const INITIAL_NY = -0.25
const dotNX = ref(INITIAL_NX)
const dotNY = ref(INITIAL_NY)

// Expose a reset() method so the parent can put the dot back to its initial
// position when another input source (text / scene) takes over.
function reset() {
  dotNX.value = INITIAL_NX
  dotNY.value = INITIAL_NY
}

defineExpose({ reset })

const dotX = computed(() => cx.value + dotNX.value * r.value)
const dotY = computed(() => cy.value + dotNY.value * r.value)

// Corner colours matching the disc CSS gradients
// top-right=energetic, top-left=calm, bottom-right=melancholy, bottom-left=focused
const DISC_TR: [number,number,number] = [255, 194,  64]  // #ffc240 energetic
const DISC_TL: [number,number,number] = [136, 200, 138]  // #88c88a calm
const DISC_BR: [number,number,number] = [160, 104, 200]  // #a068c8 melancholy
const DISC_BL: [number,number,number] = [ 74, 160, 160]  // #4aa0a0 focused
const DISC_C:  [number,number,number] = [240, 232, 218]  // #f0e8da centre neutral

function lerpRgb(
  a: [number,number,number],
  b: [number,number,number],
  t: number,
): [number,number,number] {
  return [a[0]+(b[0]-a[0])*t, a[1]+(b[1]-a[1])*t, a[2]+(b[2]-a[2])*t]
}

// Bilinear interpolation across the four corners, fade to neutral at centre
const dotColor = computed(() => {
  const x = dotNX.value   // -1=left  +1=right
  const y = dotNY.value   // -1=top   +1=bottom
  const tx = (x + 1) / 2
  const ty = (y + 1) / 2
  const top  = lerpRgb(DISC_TL, DISC_TR, tx)
  const bot  = lerpRgb(DISC_BL, DISC_BR, tx)
  const [r, g, b] = lerpRgb(top, bot, ty)
  const centre = Math.max(0, 1 - Math.hypot(x, y) * 2.2)
  const fr = Math.round(r + (DISC_C[0] - r) * centre)
  const fg = Math.round(g + (DISC_C[1] - g) * centre)
  const fb = Math.round(b + (DISC_C[2] - b) * centre)
  return `rgb(${fr},${fg},${fb})`
})

// Mood label derived from position
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

// role=application + dynamic aria-label：屏幕阅读器朗读当前心情和键盘说明。
// 2D 选择器不适合 role=slider（slider 是 1D），用 application 更准确。
const ariaLabel = computed(() => {
  const mood = moodLabel.value
  const state = mood ? `当前 ${mood}` : '中性区'
  return `心情选择器。${state}。方向键调整，Shift+方向键大步，Home 回到中央。`
})


// ── First-visit discoverability hint ───────────────────────────────────────
// Runs a one-off demo: dot orbits the centre once while a tooltip fades in.
// `isHinting` suppresses the `change` emit so parent state isn't disturbed.
const HINT_KEY = 'moodwheel_hinted'
const showHint = ref(false)
let isHinting = false
let hintRaf = 0
let hintHideTimer: ReturnType<typeof setTimeout> | null = null
let hintEndTimer: ReturnType<typeof setTimeout> | null = null

function prefersReducedMotion(): boolean {
  return typeof window !== 'undefined'
    && typeof window.matchMedia === 'function'
    && window.matchMedia('(prefers-reduced-motion: reduce)').matches
}

function runHintDemo() {
  isHinting = true
  showHint.value = true

  if (prefersReducedMotion()) {
    // Static tooltip only — no orbit animation
    hintHideTimer = setTimeout(() => {
      showHint.value = false
      isHinting = false
    }, 2000)
    return
  }

  const start = performance.now()
  const duration = 600 // ms for one full orbit
  const radius = 0.3

  const step = (now: number) => {
    const p = Math.min(1, (now - start) / duration)
    // Parametric circle starting at top (cos -> x, sin -> y, offset -π/2)
    const angle = 2 * Math.PI * p - Math.PI / 2
    dotNX.value = Math.cos(angle) * radius
    dotNY.value = Math.sin(angle) * radius
    if (p < 1) {
      hintRaf = requestAnimationFrame(step)
    } else {
      // Reset to centre for a calm post-demo state
      dotNX.value = INITIAL_NX
      dotNY.value = INITIAL_NY
      // Keep tooltip visible for ~2s after orbit completes
      hintHideTimer = setTimeout(() => {
        showHint.value = false
        // Allow fade-out (~200ms) before clearing the hint guard
        hintEndTimer = setTimeout(() => { isHinting = false }, 250)
      }, 1400)
    }
  }
  hintRaf = requestAnimationFrame(step)
}

onMounted(() => {
  try {
    if (localStorage.getItem(HINT_KEY) === '1') return
    localStorage.setItem(HINT_KEY, '1')
  } catch {
    // localStorage unavailable — skip the hint entirely
    return
  }
  runHintDemo()
})

// ── Drag logic ──────────────────────────────────────────────────────────────
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
  if (isHinting) return
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
  if (isHinting) return
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

function onKeyDown(e: KeyboardEvent) {
  if (isHinting) return
  const step = e.shiftKey ? 0.15 : 0.05
  let dx = 0, dy = 0
  switch (e.key) {
    case 'ArrowLeft':  dx = -step; break
    case 'ArrowRight': dx =  step; break
    case 'ArrowUp':    dy = -step; break
    case 'ArrowDown':  dy =  step; break
    case 'Home':
      e.preventDefault()
      dotNX.value = 0
      dotNY.value = 0
      emit('change', 0, 0)
      return
    default: return
  }
  e.preventDefault()
  const { nx, ny } = clampDot(dotNX.value + dx, dotNY.value + dy)
  dotNX.value = nx
  dotNY.value = ny
  emit('change', nx, ny)
}

onUnmounted(() => {
  window.removeEventListener('mousemove', onMouseMove)
  window.removeEventListener('mouseup', onMouseUp)
  window.removeEventListener('touchmove', onTouchMove)
  window.removeEventListener('touchend', onTouchEnd)
  if (hintRaf) cancelAnimationFrame(hintRaf)
  if (hintHideTimer) clearTimeout(hintHideTimer)
  if (hintEndTimer) clearTimeout(hintEndTimer)
})
</script>

<style scoped>
.mood-wheel {
  position: relative;
  user-select: none;
  cursor: crosshair;
  flex-shrink: 0;
  border-radius: 50%;
}

.mood-wheel:focus {
  outline: none;
}
.mood-wheel:focus-visible {
  outline: 2px solid rgba(0, 0, 0, 0.5);
  outline-offset: 6px;
}

.wheel-disc {
  position: absolute;
  inset: 2px;
  border-radius: 50%;
  overflow: hidden;
}

/*
  Mood-terrain colour disc:
  4 corners = the app's 4 mood colours,  centre = warm neutral.
  energetic (top-right)  calm (top-left)
  melancholy (bot-right) focused (bot-left)
*/
.wheel-hue {
  position: absolute;
  inset: 0;
  background-color: #f0e8da;
  background-image:
    radial-gradient(ellipse at 72% 26%, #ffd23f 0%, #ff8a5b 28%, transparent 62%),
    radial-gradient(ellipse at 28% 26%, #a8d8c0 0%, #7fb069 28%, transparent 62%),
    radial-gradient(ellipse at 72% 74%, #e085b0 0%, #6e5cd9 28%, transparent 62%),
    radial-gradient(ellipse at 28% 74%, #64c2b8 0%, #2d7a7a 28%, transparent 62%);
}

/* Soft white fade toward centre — keeps centre readable and calm */
.wheel-light {
  position: absolute;
  inset: 0;
  background: radial-gradient(
    circle at 50% 50%,
    rgba(255,255,255,0.88) 0%,
    rgba(255,255,255,0.50) 28%,
    rgba(255,255,255,0.10) 55%,
    rgba(0,0,0,0.06)       100%
  );
}

.wheel-svg {
  position: absolute;
  inset: 0;
  display: block;
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

/* First-visit hint tooltip — sits below the wheel + label */
.wheel-hint {
  position: absolute;
  bottom: -52px;
  left: 50%;
  transform: translateX(-50%);
  padding: 6px 12px;
  border-radius: 999px;
  font-size: 12px;
  letter-spacing: .04em;
  white-space: nowrap;
  color: var(--ink-1, #2a2a2a);
  background: rgba(255, 255, 255, 0.92);
  box-shadow: 0 4px 14px rgba(0, 0, 0, 0.12);
  pointer-events: none;
}

.hint-fade-enter-active,
.hint-fade-leave-active {
  transition: opacity 200ms ease;
}
.hint-fade-enter-from,
.hint-fade-leave-to {
  opacity: 0;
}
</style>
