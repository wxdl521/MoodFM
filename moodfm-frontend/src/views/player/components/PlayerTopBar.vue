<template>
  <div class="player-topbar">
    <button class="player-back-btn" @click="emit('back')">
      <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
        <polyline points="15 18 9 12 15 6"/>
      </svg>
      <span class="mono" style="font-size: 11px; letter-spacing: .16em">BACK</span>
    </button>

    <div class="player-station-info">
      <div class="mono" style="font-size: 10px; letter-spacing: .2em; opacity: .7">
        STATION · NOW PLAYING
      </div>
      <div style="font-family: var(--serif-en); font-style: italic; font-size: 22px; margin-top: 2px">
        {{ stationName }}
      </div>
      <div style="font-size: 13px; opacity: .85; margin-top: 1px">
        {{ subtitle }}
      </div>
    </div>

    <button class="player-more-btn" @click="ui.toggleQueueDrawer()">
      <svg width="20" height="20" viewBox="0 0 24 24" fill="currentColor">
        <circle cx="12" cy="5"  r="1.5"/>
        <circle cx="12" cy="12" r="1.5"/>
        <circle cx="12" cy="19" r="1.5"/>
      </svg>
    </button>
  </div>
</template>

<script setup lang="ts">
import { useUiStore } from '@/stores/ui'

defineProps<{
  stationName: string
  subtitle: string
}>()

const emit = defineEmits<{ back: [] }>()
const ui = useUiStore()
</script>

<style scoped>
.player-topbar {
  padding: 24px 56px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  border-bottom: 1px solid rgba(255, 255, 255, 0.12);
  flex-shrink: 0;
}

@media (max-width: 768px) {
  .player-topbar { padding: 18px 22px; }
}

.player-back-btn,
.player-more-btn {
  display: flex;
  align-items: center;
  gap: 8px;
  background: transparent;
  border: none;
  color: #fff;
  cursor: pointer;
  padding: 6px;
  border-radius: 8px;
  transition: background 0.15s;
}

.player-back-btn:hover,
.player-more-btn:hover {
  background: rgba(255, 255, 255, 0.1);
}

.player-station-info {
  text-align: center;
  flex: 1;
  padding: 0 16px;
}
</style>