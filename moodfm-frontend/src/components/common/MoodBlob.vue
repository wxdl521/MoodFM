<template>
  <div v-if="geometry === 'blob'" :style="{ width: size + 'px', height: size + 'px', position: 'relative', flexShrink: 0 }">
    <svg :viewBox="`0 0 200 200`" :width="size" :height="size" style="display: block;">
      <defs>
        <radialGradient :id="`g1-${uid}`" cx="30%" cy="30%" r="80%">
          <stop offset="0%"   stop-color="var(--mood-a)" />
          <stop offset="100%" stop-color="var(--mood-c)" />
        </radialGradient>
        <radialGradient :id="`g2-${uid}`" cx="70%" cy="70%" r="80%">
          <stop offset="0%"   stop-color="var(--mood-b)" stop-opacity="0.9" />
          <stop offset="100%" stop-color="var(--mood-d)" stop-opacity="0" />
        </radialGradient>
        <filter :id="`f-${uid}`">
          <feGaussianBlur stdDeviation="0.6" />
        </filter>
      </defs>
      <path
        d="M 100,18 C 145,18 180,55 180,100 C 180,150 145,182 100,182 C 55,182 22,150 22,100 C 22,58 55,18 100,18 Z"
        :fill="`url(#g1-${uid})`"
        :filter="`url(#f-${uid})`"
      >
        <animate v-if="drift"
          attributeName="d"
          dur="14s"
          repeatCount="indefinite"
          values="
            M 100,18 C 145,18 180,55 180,100 C 180,150 145,182 100,182 C 55,182 22,150 22,100 C 22,58 55,18 100,18 Z;
            M 100,22 C 152,15 178,62 184,108 C 188,148 138,184 96,178 C 50,172 18,142 22,96 C 26,52 60,28 100,22 Z;
            M 100,18 C 145,18 180,55 180,100 C 180,150 145,182 100,182 C 55,182 22,150 22,100 C 22,58 55,18 100,18 Z"
        />
      </path>
      <path
        d="M 100,30 C 140,30 170,60 170,100 C 170,140 140,170 100,170 C 60,170 30,140 30,100 C 30,60 60,30 100,30 Z"
        :fill="`url(#g2-${uid})`"
        opacity="0.7"
      >
        <animateTransform v-if="drift"
          attributeName="transform"
          type="translate"
          dur="11s"
          values="0,0; 8,-6; -6,8; 0,0"
          repeatCount="indefinite"
        />
      </path>
    </svg>
    <div v-if="$slots.default" style="position: absolute; inset: 0;">
      <slot />
    </div>
  </div>

  <div v-else :style="shapeStyle" style="flex-shrink: 0; position: relative;">
    <slot />
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'

const props = withDefaults(defineProps<{
  size?: number
  drift?: boolean
  geometry?: 'blob' | 'circle' | 'square'
}>(), {
  size: 280,
  drift: true,
  geometry: 'blob',
})

const uid = Math.random().toString(36).slice(2, 8)

const shapeStyle = computed(() => {
  const r = props.geometry === 'circle' ? '50%' : props.geometry === 'square' ? '16px' : '0'
  return {
    width: props.size + 'px',
    height: props.size + 'px',
    borderRadius: r,
    background: `
      radial-gradient(circle at 28% 28%, var(--mood-a) 0%, transparent 55%),
      radial-gradient(circle at 75% 35%, var(--mood-b) 0%, transparent 55%),
      radial-gradient(circle at 50% 85%, var(--mood-c) 0%, transparent 60%),
      radial-gradient(circle at 18% 75%, var(--mood-d) 0%, transparent 60%)`,
    backgroundColor: 'var(--mood-c)',
  }
})
</script>
