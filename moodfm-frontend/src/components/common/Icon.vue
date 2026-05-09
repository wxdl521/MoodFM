<template>
  <svg
    :width="size"
    :height="size"
    :viewBox="viewBox"
    :fill="fill"
    :stroke="stroke"
    :stroke-width="strokeWidth"
    :stroke-linecap="strokeLinecap"
    :stroke-linejoin="strokeLinejoin"
    style="display: block; flex-shrink: 0;"
    aria-hidden="true"
  >
    <template v-if="name === 'search'">
      <circle cx="11" cy="11" r="7.5" />
      <line x1="16.8" y1="16.8" x2="22" y2="22" />
    </template>

    <template v-else-if="name === 'bell'">
      <path d="M18 8a6 6 0 0 0-12 0c0 7-3 9-3 9h18s-3-2-3-9" />
      <path d="M13.73 21a2 2 0 0 1-3.46 0" />
    </template>

    <template v-else-if="name === 'play'">
      <path d="M8 5v14l11-7z" fill="currentColor" stroke="none" />
    </template>

    <template v-else-if="name === 'pause'">
      <path d="M6 19h4V5H6zm8-14v14h4V5z" fill="currentColor" stroke="none" />
    </template>

    <template v-else-if="name === 'prev'">
      <path d="M6 6h2v12H6zm3.5 6 8.5 6V6z" fill="currentColor" stroke="none" />
    </template>

    <template v-else-if="name === 'next'">
      <path d="M6 18l8.5-6L6 6zm8.5-6 3.5 2.5V9.5z" fill="currentColor" stroke="none" />
      <rect x="16" y="6" width="2" height="12" fill="currentColor" stroke="none" />
    </template>

    <template v-else-if="name === 'skip'">
      <polyline points="5 4 15 12 5 20" />
      <line x1="19" y1="4" x2="19" y2="20" />
    </template>

    <template v-else-if="name === 'heart'">
      <path d="M20.84 4.61a5.5 5.5 0 0 0-7.78 0L12 5.67l-1.06-1.06a5.5 5.5 0 0 0-7.78 7.78l1.06 1.06L12 21.23l7.78-7.78 1.06-1.06a5.5 5.5 0 0 0 0-7.78z" />
    </template>

    <template v-else-if="name === 'arrow'">
      <line x1="5" y1="12" x2="19" y2="12" />
      <polyline points="12 5 19 12 12 19" />
    </template>

    <template v-else-if="name === 'chevL'">
      <polyline points="15 18 9 12 15 6" />
    </template>

    <template v-else-if="name === 'chevR'">
      <polyline points="9 18 15 12 9 6" />
    </template>

    <template v-else-if="name === 'more'">
      <circle cx="12" cy="5"  r="1.5" fill="currentColor" stroke="none" />
      <circle cx="12" cy="12" r="1.5" fill="currentColor" stroke="none" />
      <circle cx="12" cy="19" r="1.5" fill="currentColor" stroke="none" />
    </template>

    <template v-else-if="name === 'share'">
      <circle cx="18" cy="5"  r="3" />
      <circle cx="6"  cy="12" r="3" />
      <circle cx="18" cy="19" r="3" />
      <line x1="8.59"  y1="13.51" x2="15.42" y2="17.49" />
      <line x1="15.41" y1="6.51"  x2="8.59"  y2="10.49" />
    </template>

    <template v-else-if="name === 'x'">
      <line x1="18" y1="6" x2="6" y2="18" />
      <line x1="6"  y1="6" x2="18" y2="18" />
    </template>

    <template v-else-if="name === 'check'">
      <polyline points="20 6 9 17 4 12" />
    </template>

    <template v-else-if="name === 'plus'">
      <line x1="12" y1="5" x2="12" y2="19" />
      <line x1="5"  y1="12" x2="19" y2="12" />
    </template>

    <template v-else-if="name === 'menu'">
      <line x1="3"  y1="6"  x2="21" y2="6" />
      <line x1="3"  y1="12" x2="21" y2="12" />
      <line x1="3"  y1="18" x2="21" y2="18" />
    </template>

    <template v-else-if="name === 'settings'">
      <circle cx="12" cy="12" r="3" />
      <path d="M19.4 15a1.65 1.65 0 0 0 .33 1.82l.06.06a2 2 0 0 1-2.83 2.83l-.06-.06a1.65 1.65 0 0 0-1.82-.33 1.65 1.65 0 0 0-1 1.51V21a2 2 0 0 1-4 0v-.09A1.65 1.65 0 0 0 9 19.4a1.65 1.65 0 0 0-1.82.33l-.06.06a2 2 0 0 1-2.83-2.83l.06-.06A1.65 1.65 0 0 0 4.68 15a1.65 1.65 0 0 0-1.51-1H3a2 2 0 0 1 0-4h.09A1.65 1.65 0 0 0 4.6 9a1.65 1.65 0 0 0-.33-1.82l-.06-.06a2 2 0 0 1 2.83-2.83l.06.06A1.65 1.65 0 0 0 9 4.68a1.65 1.65 0 0 0 1-1.51V3a2 2 0 0 1 4 0v.09a1.65 1.65 0 0 0 1 1.51 1.65 1.65 0 0 0 1.82-.33l.06-.06a2 2 0 0 1 2.83 2.83l-.06.06A1.65 1.65 0 0 0 19.4 9a1.65 1.65 0 0 0 1.51 1H21a2 2 0 0 1 0 4h-.09a1.65 1.65 0 0 0-1.51 1z" />
    </template>

    <template v-else-if="name === 'list'">
      <line x1="8"  y1="6"  x2="21" y2="6" />
      <line x1="8"  y1="12" x2="21" y2="12" />
      <line x1="8"  y1="18" x2="21" y2="18" />
      <line x1="3"  y1="6"  x2="3.01" y2="6" />
      <line x1="3"  y1="12" x2="3.01" y2="12" />
      <line x1="3"  y1="18" x2="3.01" y2="18" />
    </template>

    <template v-else-if="name === 'user'">
      <path d="M20 21v-2a4 4 0 0 0-4-4H8a4 4 0 0 0-4 4v2" />
      <circle cx="12" cy="7" r="4" />
    </template>

    <template v-else-if="name === 'clock'">
      <circle cx="12" cy="12" r="10" />
      <polyline points="12 6 12 12 16 14" />
    </template>

    <template v-else-if="name === 'music'">
      <path d="M9 18V5l12-2v13" />
      <circle cx="6"  cy="18" r="3" />
      <circle cx="18" cy="16" r="3" />
    </template>

    <template v-else-if="name === 'ban'">
      <circle cx="12" cy="12" r="10" />
      <line x1="4.93" y1="4.93" x2="19.07" y2="19.07" />
    </template>
  </svg>
</template>

<script setup lang="ts">
import { computed } from 'vue'

const props = withDefaults(defineProps<{
  name: string
  size?: number
  color?: string
}>(), {
  size: 20,
  color: 'currentColor',
})

const strokeIcons = new Set([
  'search', 'bell', 'skip', 'heart', 'arrow', 'chevL', 'chevR',
  'share', 'x', 'check', 'plus', 'menu', 'settings', 'list',
  'user', 'clock', 'music', 'ban',
])

const fillIcons = new Set(['play', 'pause', 'prev', 'next', 'more'])

const isStroke = computed(() => strokeIcons.has(props.name))

const fill   = computed(() => isStroke.value ? 'none' : props.color)
const stroke = computed(() => isStroke.value ? props.color : 'none')
const strokeWidth = computed(() => isStroke.value ? '1.8' : '0')
const strokeLinecap  = computed(() => isStroke.value ? 'round' : undefined)
const strokeLinejoin = computed(() => isStroke.value ? 'round' : undefined)
const viewBox = computed(() => '0 0 24 24')
</script>
