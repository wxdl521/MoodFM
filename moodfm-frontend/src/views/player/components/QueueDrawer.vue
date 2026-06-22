<template>
  <Transition name="fade">
    <div
      v-if="ui.queueDrawerOpen"
      class="queue-drawer-backdrop"
      @click="ui.toggleQueueDrawer()"
    >
      <div class="queue-drawer-panel" @click.stop>
        <div class="mono queue-drawer-title">
          UP NEXT · 接下来播放 · {{ player.queue.length }} 首
        </div>
        <div v-if="player.queue.length === 0" class="queue-drawer-empty">
          队列为空
        </div>
        <div
          v-for="(song, i) in player.queue.slice(0, 12)"
          :key="song.id ?? i"
          class="row queue-drawer-item"
        >
          <span class="mono queue-drawer-index">{{ String(i + 1).padStart(2,'0') }}</span>
          <div class="queue-drawer-song">
            <div class="queue-drawer-song-title">{{ song.title }}</div>
            <div class="meta queue-drawer-song-artist">{{ song.artist }}</div>
          </div>
          <span class="mono queue-drawer-duration">{{ formatTime(song.duration) }}</span>
        </div>
      </div>
    </div>
  </Transition>
</template>

<script setup lang="ts">
import { usePlayerStore } from '@/stores/player'
import { useUiStore } from '@/stores/ui'
import { formatTime } from '../utils/formatTime'

const player = usePlayerStore()
const ui = useUiStore()
</script>

<style scoped>
.queue-drawer-backdrop {
  position: fixed;
  inset: 0;
  z-index: 50;
  background: rgba(0,0,0,0.55);
  display: flex;
  flex-direction: column;
  justify-content: flex-end;
}

.queue-drawer-panel {
  background: var(--bg);
  border-radius: 20px 20px 0 0;
  padding: 24px 24px 48px;
  max-height: 65vh;
  overflow-y: auto;
}

.queue-drawer-title {
  font-size: 10px;
  letter-spacing: .2em;
  opacity: .5;
  text-align: center;
  margin-bottom: 20px;
}

.queue-drawer-empty {
  text-align: center;
  padding: 32px 0;
  font-family: var(--serif-cn);
  font-size: 15px;
  color: var(--ink-3);
}

.queue-drawer-item {
  gap: 14px;
  padding: 12px 0;
  border-bottom: 1px solid var(--rule);
  align-items: center;
}

.queue-drawer-index {
  font-size: 11px;
  color: var(--ink-3);
  width: 24px;
  flex-shrink: 0;
}

.queue-drawer-song {
  flex: 1;
  min-width: 0;
}

.queue-drawer-song-title {
  font-family: var(--serif-en);
  font-style: italic;
  font-size: 17px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.queue-drawer-song-artist {
  margin-top: 2px;
}

.queue-drawer-duration {
  font-size: 11px;
  color: var(--ink-3);
  flex-shrink: 0;
}

.fade-enter-active,
.fade-leave-active { transition: opacity 0.25s ease; }
.fade-enter-from,
.fade-leave-to     { opacity: 0; }
</style>