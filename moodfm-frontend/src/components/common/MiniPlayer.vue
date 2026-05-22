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
import api from '@/api/client'

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
  const prevSong = playerStore.currentSong
  const sid = playerStore.sessionId
  const playedSecs = Math.round(audioPlayer.currentTime.value) || 0
  const totalSecs = Math.round(audioPlayer.duration.value || prevSong?.duration || 0) || 0
  playerStore.next()
  const song = playerStore.currentSong
  if (song?.audioUrl) {
    audioPlayer.load(song.audioUrl)
      .then(() => audioPlayer.play())
      .catch(() => { if (playerStore.queue.length > 0) handleNext() })
  } else {
    audioPlayer.stop()
  }
  if (prevSong?.id && sid) {
    api.post('/radio/feedback', {
      sessionId: Number(sid),
      songId: Number(prevSong.id),
      eventType: 'skip',
      playedSeconds: playedSecs,
      totalSeconds: totalSecs,
      platform: prevSong.platform || 'netease',
    }).catch(() => {})
  }
}

function handlePrev() {
  playerStore.prev()
  const song = playerStore.currentSong
  if (song?.audioUrl) {
    audioPlayer.load(song.audioUrl)
      .then(() => audioPlayer.play())
      .catch(() => {})
  } else {
    audioPlayer.stop()
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
  animation: mini-slide-up 0.42s cubic-bezier(0.16, 1, 0.3, 1) both;
  transition: box-shadow 0.2s ease, transform 0.15s cubic-bezier(0.34, 1.56, 0.64, 1);
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
  transition: background 0.15s, transform 0.12s cubic-bezier(0.34, 1.56, 0.64, 1);
}

.mini-player__btn:hover {
  background: rgba(244, 239, 230, 0.12);
}

.mini-player__btn:active {
  transform: scale(0.85);
  transition-duration: 0.07s;
}

.mini-player:hover {
  box-shadow: 0 12px 40px rgba(0, 0, 0, 0.36), 0 4px 12px rgba(0, 0, 0, 0.2);
}

.mini-player__btn--primary {
  width: 40px;
  height: 40px;
  background: var(--bg);
  color: var(--ink);
  transition: background 0.15s, transform 0.12s cubic-bezier(0.34, 1.56, 0.64, 1);
}

.mini-player__btn--primary:hover {
  background: var(--bg-2, #ebe4d6);
}

.mini-player__btn--primary:active {
  transform: scale(0.88);
  transition-duration: 0.07s;
}

/* ── MiniPlayer enter ──────────────────────────────── */
@keyframes mini-slide-up {
  from {
    transform: translateY(100%) scale(0.96);
    opacity: 0;
  }
  to {
    transform: translateY(0) scale(1);
    opacity: 1;
  }
}
</style>
