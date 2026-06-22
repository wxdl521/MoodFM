<template>
  <div class="player-cover-wrap">
    <div class="pulse-ring" :class="{ active: player.isPlaying, loading: isAudioLoading && !player.isPlaying }" />
    <MoodBlob
      :size="440"
      geometry="blob"
      :drift="player.isPlaying"
      class="player-blob"
    />
  </div>
</template>

<script setup lang="ts">
import { usePlayerStore } from '@/stores/player'
import MoodBlob from '@/components/common/MoodBlob.vue'

defineProps<{
  isAudioLoading: boolean
}>()

const player = usePlayerStore()
</script>

<style scoped>
.player-cover-wrap {
  display: flex;
  justify-content: center;
  align-items: center;
  position: relative;
}

.pulse-ring {
  position: absolute;
  width: 480px;
  height: 480px;
  border-radius: 50%;
  border: 1px solid rgba(255, 255, 255, 0.18);
  pointer-events: none;
}

.pulse-ring.active {
  animation: pulse-ring 4s ease-out infinite;
}

.pulse-ring.loading {
  animation: pulse-ring-loading 1.6s ease-in-out infinite;
}

@keyframes pulse-ring-loading {
  0%, 100% { transform: scale(1.00); opacity: 0.45; }
  50%      { transform: scale(1.04); opacity: 0.75; }
}

@media (max-width: 900px) {
  .pulse-ring {
    width: 320px;
    height: 320px;
  }
}

.player-blob {
  position: relative;
  z-index: 1;
}

@keyframes pulse-ring {
  0%   { transform: scale(0.96); opacity: 0.7; }
  100% { transform: scale(1.18); opacity: 0; }
}
</style>