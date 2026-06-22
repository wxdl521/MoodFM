<template>
  <div
    ref="progressEl"
    class="progress-area"
    @mousedown="handleProgressDown"
    @touchstart.passive="handleProgressDown"
  >
    <div class="progress-track">
      <div class="progress-fill" :style="{ width: `${displayProgress * 100}%` }" />
      <div
        class="progress-thumb"
        :class="{ 'progress-thumb--dragging': isDragging }"
        :style="{ left: `${displayProgress * 100}%` }"
      />
    </div>
    <div class="mono between" style="font-size: 11px; margin-top: 8px; opacity: .8">
      <span>{{ formatTime(displayCurrentTime) }}</span>
      <span>{{ formatTime(totalDuration) }}</span>
    </div>
  </div>
</template>

<script setup lang="ts">
import { useProgressBar } from '../composables/useProgressBar'
import { formatTime } from '../utils/formatTime'
import type { useAudioPlayer } from '@/composables/useAudioPlayer'

const props = defineProps<{
  audio: ReturnType<typeof useAudioPlayer>
}>()

const {
  progressEl,
  isDragging,
  displayProgress,
  displayCurrentTime,
  totalDuration,
  handleProgressDown,
  cleanupProgressListeners,
} = useProgressBar(props.audio)

defineExpose({ cleanupProgressListeners })
</script>

<style scoped>
.progress-area {
  margin-top: 28px;
  cursor: pointer;
  padding: 10px 0;
  touch-action: none;
  user-select: none;
  -webkit-user-select: none;
}

.progress-track {
  height: 2px;
  background: rgba(255, 255, 255, 0.18);
  border-radius: 1px;
  position: relative;
}

.progress-fill {
  position: absolute;
  left: 0;
  top: 0;
  bottom: 0;
  background: #fff;
  border-radius: 1px;
  transition: width 0.2s linear;
}

.progress-thumb {
  position: absolute;
  top: -3px;
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: #fff;
  transform: translateX(-50%);
  transition: left 0.2s linear, width 0.15s ease, height 0.15s ease, top 0.15s ease;
}

.progress-thumb--dragging {
  width: 14px;
  height: 14px;
  top: -6px;
  transition: width 0.15s ease, height 0.15s ease, top 0.15s ease;
}

.progress-area:has(.progress-thumb--dragging) .progress-fill,
.progress-area:has(.progress-thumb--dragging) .progress-thumb {
  transition: width 0.15s ease, height 0.15s ease, top 0.15s ease;
}
</style>