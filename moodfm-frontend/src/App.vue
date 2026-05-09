<template>
  <div :data-theme="uiStore.theme" :data-mood="uiStore.moodPreset">
    <RouterView />
  </div>
</template>

<script setup lang="ts">
import { useUiStore } from '@/stores/ui'

const uiStore = useUiStore()
</script>
