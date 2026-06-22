<template>
  <div>
    <div class="ctrl-row">
      <button
        class="ctrl-btn"
        aria-label="上一首"
        :disabled="!canPrev"
        :class="{ 'ctrl-btn--disabled': !canPrev }"
        @click="emit('prev')"
      >
        <svg width="20" height="20" viewBox="0 0 24 24" fill="currentColor"><path d="M6 6h2v12H6zm3.5 6 8.5 6V6z"/></svg>
      </button>

      <button
        class="ctrl-btn ctrl-btn--primary"
        :aria-label="player.isPlaying ? '暂停' : '播放'"
        @click="emit('play-pause')"
      >
        <svg v-if="player.isPlaying" width="24" height="24" viewBox="0 0 24 24" fill="currentColor">
          <path d="M6 19h4V5H6zm8-14v14h4V5z"/>
        </svg>
        <svg v-else width="24" height="24" viewBox="0 0 24 24" fill="currentColor">
          <path d="M8 5v14l11-7z"/>
        </svg>
      </button>

      <button class="ctrl-btn" aria-label="下一首" @click="emit('next')">
        <svg width="20" height="20" viewBox="0 0 24 24" fill="currentColor"><path d="M6 18l8.5-6L6 6zm8.5-6 3.5 2.5V9.5z"/><rect x="16" y="6" width="2" height="12"/></svg>
      </button>

      <button class="ctrl-btn" style="margin-left: 12px" aria-label="跳过" @click="emit('skip')">
        <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
          <polyline points="5 4 15 12 5 20"/>
          <line x1="19" y1="4" x2="19" y2="20"/>
        </svg>
      </button>
    </div>

    <div class="chip-row">
      <button
        class="chip-btn"
        :class="{ 'chip-btn--active': liked }"
        @click="emit('toggle-like')"
      >
        <svg
          width="14" height="14" viewBox="0 0 24 24"
          :fill="liked ? 'var(--mood-a)' : 'none'"
          :stroke="liked ? 'var(--mood-a)' : 'currentColor'"
          stroke-width="2" stroke-linecap="round" stroke-linejoin="round"
          :style="liked ? 'transform:scale(1.15); transition: transform 0.2s cubic-bezier(0.34, 1.56, 0.64, 1);' : 'transform:scale(1); transition: transform 0.2s cubic-bezier(0.34, 1.56, 0.64, 1);'"
        >
          <path d="M20.84 4.61a5.5 5.5 0 0 0-7.78 0L12 5.67l-1.06-1.06a5.5 5.5 0 0 0-7.78 7.78l1.06 1.06L12 21.23l7.78-7.78 1.06-1.06a5.5 5.5 0 0 0 0-7.78z"/>
        </svg>
        {{ liked ? '已红心' : '红心' }}
      </button>

      <button class="chip-btn" @click="emit('dislike')">不喜欢</button>
      <button class="chip-btn" @click="emit('toggle-lyrics')">歌词</button>
      <button class="chip-btn" @click="ui.toggleQueueDrawer()">
        队列 · {{ queueCount }}
      </button>
      <button class="chip-btn chip-btn--share" @click="emit('share')">
        <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
          <circle cx="18" cy="5" r="3"/><circle cx="6" cy="12" r="3"/><circle cx="18" cy="19" r="3"/>
          <line x1="8.59" y1="13.51" x2="15.42" y2="17.49"/>
          <line x1="15.41" y1="6.51" x2="8.59" y2="10.49"/>
        </svg>
        分享
      </button>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { usePlayerStore } from '@/stores/player'
import { useUiStore } from '@/stores/ui'

defineProps<{
  liked: boolean
  canPrev: boolean
}>()

const emit = defineEmits<{
  'play-pause': []
  next: []
  prev: []
  skip: []
  'toggle-like': []
  dislike: []
  'toggle-lyrics': []
  share: []
}>()

const player = usePlayerStore()
const ui = useUiStore()
const queueCount = computed(() => player.queue.length)
</script>

<style scoped>
.ctrl-row {
  display: flex;
  align-items: center;
  gap: 14px;
  margin-top: 24px;
  animation: player-rise 0.45s cubic-bezier(0.16, 1, 0.3, 1) 0.34s both;
}

@media (max-width: 900px) {
  .ctrl-row { justify-content: center; }
}

.ctrl-btn {
  width: 48px;
  height: 48px;
  border-radius: 50%;
  background: rgba(255, 255, 255, 0.12);
  border: 1px solid rgba(255, 255, 255, 0.2);
  color: #fff;
  cursor: pointer;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  transition: background 0.15s, transform 0.12s cubic-bezier(0.34, 1.56, 0.64, 1);
}

.ctrl-btn:hover { background: rgba(255, 255, 255, 0.22); }
.ctrl-btn:active { transform: scale(0.88); transition-duration: 0.07s; }

.ctrl-btn--disabled,
.ctrl-btn:disabled {
  opacity: 0.35;
  cursor: not-allowed;
}

.ctrl-btn--disabled:hover,
.ctrl-btn:disabled:hover { background: rgba(255, 255, 255, 0.12); }

.ctrl-btn--disabled:active,
.ctrl-btn:disabled:active { transform: none; }

.ctrl-btn--primary {
  width: 64px;
  height: 64px;
  background: #fff;
  color: var(--ink);
  border-color: transparent;
  transition: background 0.15s, transform 0.15s cubic-bezier(0.34, 1.56, 0.64, 1);
}

.ctrl-btn--primary:hover {
  background: rgba(255, 255, 255, 0.9);
  transform: scale(1.06);
}

.ctrl-btn--primary:active {
  transform: scale(0.91);
  transition-duration: 0.07s;
}

.chip-row {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 22px;
  animation: player-rise 0.4s cubic-bezier(0.16, 1, 0.3, 1) 0.42s both;
}

.chip-btn {
  height: 34px;
  padding: 0 14px;
  border-radius: 999px;
  border: 1px solid rgba(255, 255, 255, 0.22);
  background: transparent;
  color: #fff;
  cursor: pointer;
  font-family: var(--mono);
  font-size: 11px;
  letter-spacing: .12em;
  text-transform: uppercase;
  display: inline-flex;
  align-items: center;
  gap: 6px;
  transition: background 0.15s, transform 0.12s cubic-bezier(0.34, 1.56, 0.64, 1);
}

.chip-btn:hover { background: rgba(255, 255, 255, 0.1); }
.chip-btn:active { transform: scale(0.93); transition-duration: 0.07s; }
.chip-btn--active { background: rgba(255, 255, 255, 0.12); }

@keyframes player-rise {
  from { opacity: 0; transform: translateY(14px); }
  to   { opacity: 1; transform: translateY(0); }
}
</style>