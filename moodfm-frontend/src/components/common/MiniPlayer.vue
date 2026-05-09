<template>
  <div
    v-if="currentSong"
    class="mini-player"
    @click.self="goToPlayer"
  >
    <!-- Left: blob cover + song info -->
    <div class="mini-player__left" @click="goToPlayer">
      <MoodBlob :size="48" geometry="circle" :drift="false" />
      <div class="mini-player__info">
        <p class="mini-player__title">{{ currentSong.title }}</p>
        <p class="mini-player__artist">{{ currentSong.artist }}</p>
      </div>
    </div>

    <!-- Right: controls -->
    <div class="mini-player__controls" @click.stop>
      <!-- Prev -->
      <button class="mini-player__btn" aria-label="上一首" @click="handlePrev">
        <svg width="16" height="16" viewBox="0 0 24 24" fill="currentColor">
          <path d="M6 6h2v12H6zm3.5 6 8.5 6V6z" />
        </svg>
      </button>

      <!-- Play / Pause -->
      <button class="mini-player__btn mini-player__btn--primary" :aria-label="isPlaying ? '暂停' : '播放'" @click="handlePlayPause">
        <!-- Pause icon -->
        <svg v-if="isPlaying" width="18" height="18" viewBox="0 0 24 24" fill="currentColor">
          <path d="M6 19h4V5H6zm8-14v14h4V5z" />
        </svg>
        <!-- Play icon -->
        <svg v-else width="18" height="18" viewBox="0 0 24 24" fill="currentColor">
          <path d="M8 5v14l11-7z" />
        </svg>
      </button>

      <!-- Next -->
      <button class="mini-player__btn" aria-label="下一首" @click="handleNext">
        <svg width="16" height="16" viewBox="0 0 24 24" fill="currentColor">
          <path d="M6 18l8.5-6L6 6zm8.5-6 3.5 2.5V9.5z" />
          <rect x="16" y="6" width="2" height="12" />
        </svg>
      </button>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useRouter } from 'vue-router'
import { usePlayerStore } from '@/stores/player'
import { useAudioPlayer } from '@/composables/useAudioPlayer'
import MoodBlob from './MoodBlob.vue'

const router = useRouter()
const playerStore = usePlayerStore()
const audioPlayer = useAudioPlayer()

const currentSong = computed(() => playerStore.currentSong)
const isPlaying = computed(() => playerStore.isPlaying)

function goToPlayer() {
  router.push('/player')
}

function handlePlayPause() {
  if (isPlaying.value) {
    audioPlayer.pause()
  } else {
    audioPlayer.play()
  }
}

function handleNext() {
  playerStore.next()
  const song = playerStore.currentSong
  if (song?.audioUrl) {
    audioPlayer.load(song.audioUrl).then(() => audioPlayer.play())
  }
}

function handlePrev() {
  playerStore.prev()
  const song = playerStore.currentSong
  if (song?.audioUrl) {
    audioPlayer.load(song.audioUrl).then(() => audioPlayer.play())
  }
}
</script>

<style scoped>
.mini-player {
  position: fixed;
  bottom: 16px;
  left: 12px;
  right: 12px;
  height: 64px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 12px 0 8px;
  background: var(--ink);
  color: var(--bg);
  border-radius: 999px;
  box-shadow: 0 8px 32px rgba(0, 0, 0, 0.28), 0 2px 8px rgba(0, 0, 0, 0.16);
  z-index: 100;
  cursor: pointer;
  user-select: none;
}

@media (min-width: 768px) {
  .mini-player {
    left: 24px;
    right: 24px;
    padding: 0 16px 0 10px;
  }
}

/* Left section */
.mini-player__left {
  display: flex;
  align-items: center;
  gap: 10px;
  flex: 1;
  min-width: 0;
  cursor: pointer;
}

.mini-player__info {
  display: flex;
  flex-direction: column;
  min-width: 0;
  gap: 1px;
}

.mini-player__title {
  margin: 0;
  font-family: var(--serif-cn);
  font-size: 0.875rem;
  font-weight: 500;
  color: var(--bg);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  line-height: 1.3;
}

.mini-player__artist {
  margin: 0;
  font-family: var(--mono);
  font-size: 0.72rem;
  color: var(--ink-3);
  /* In dark context, ink-3 is light enough; but we override for the dark pill */
  color: rgba(244, 239, 230, 0.5);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  line-height: 1.3;
}

/* Controls */
.mini-player__controls {
  display: flex;
  align-items: center;
  gap: 4px;
  flex-shrink: 0;
}

.mini-player__btn {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 36px;
  height: 36px;
  border: none;
  border-radius: 50%;
  background: transparent;
  color: var(--bg);
  cursor: pointer;
  transition: background 0.15s;
}

.mini-player__btn:hover {
  background: rgba(244, 239, 230, 0.12);
}

.mini-player__btn--primary {
  width: 40px;
  height: 40px;
  background: var(--bg);
  color: var(--ink);
}

.mini-player__btn--primary:hover {
  background: var(--bg-2, #ebe4d6);
}
</style>
