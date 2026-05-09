<template>
  <svg
    :width="size"
    :height="size"
    viewBox="0 0 24 24"
    fill="none"
    xmlns="http://www.w3.org/2000/svg"
    aria-label="MoodFM logo"
  >
    <!-- Outer ring -->
    <circle cx="12" cy="12" r="10.5" stroke="var(--ink)" stroke-width="1.4" />
    <!-- Inner circle / vinyl centre -->
    <circle cx="12" cy="12" r="2.8" fill="var(--ink)" />
    <!-- Concentric sound-wave arcs — upper half -->
    <path
      d="M 6.2 10.2 A 6.2 6.2 0 0 1 17.8 10.2"
      stroke="var(--ink)" stroke-width="1.3" stroke-linecap="round" fill="none"
    />
    <path
      d="M 8.4 11.3 A 3.8 3.8 0 0 1 15.6 11.3"
      stroke="var(--ink)" stroke-width="1.2" stroke-linecap="round" fill="none"
    />
    <!-- Frequency needle / indicator -->
    <line
      x1="12" y1="12" x2="16.2" y2="7.8"
      stroke="var(--ink)" stroke-width="1.4" stroke-linecap="round"
    />
  </svg>
</template>

<script setup lang="ts">
withDefaults(
  defineProps<{ size?: number }>(),
  { size: 24 },
)
</script>
