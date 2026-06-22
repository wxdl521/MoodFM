<template>
  <div class="queue-strip">
    <div class="mono" style="font-size: 10px; letter-spacing: .18em; opacity: .7; white-space: nowrap; flex-shrink: 0">
      UP NEXT →
    </div>
    <template v-if="player.queue.length > 0">
      <div
        v-for="(song, i) in player.queue.slice(0, 5)"
        :key="song.id"
        class="queue-item"
      >
        <span class="mono" style="font-size: 10px; opacity: .7">{{ String(player.trackNumber + i + 1).padStart(2, '0') }}</span>
        <span style="font-family: var(--serif-cn); font-size: 13px">{{ song.title }}</span>
        <span style="font-size: 12px; opacity: .7">· {{ song.artist }}</span>
        <span class="mono" style="font-size: 10px; opacity: .6">{{ formatTime(song.duration) }}</span>
      </div>
    </template>
    <template v-else>
      <div class="queue-empty mono">
        队列已空 · 当前为最后一首
      </div>
    </template>
  </div>
</template>

<script setup lang="ts">
import { usePlayerStore } from '@/stores/player'
import { formatTime } from '../utils/formatTime'

const player = usePlayerStore()
</script>

<style scoped>
.queue-strip {
  display: none;
  padding: 18px 56px 24px;
  border-top: 1px solid rgba(255, 255, 255, 0.12);
  gap: 16px;
  align-items: center;
  overflow-x: auto;
  flex-shrink: 0;
}

@media (min-width: 900px) {
  .queue-strip { display: flex; }
}

.queue-item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 8px 12px;
  border: 1px solid rgba(255, 255, 255, 0.18);
  border-radius: 999px;
  white-space: nowrap;
  cursor: pointer;
  transition: background 0.15s;
  flex-shrink: 0;
}

.queue-item:hover {
  background: rgba(255, 255, 255, 0.08);
}

.queue-empty {
  font-size: 10px;
  letter-spacing: .18em;
  opacity: 0.55;
  padding: 8px 0;
  white-space: nowrap;
}
</style>