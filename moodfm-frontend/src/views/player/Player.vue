<template>
  <div class="player-page" :data-mood="ui.moodPreset">
    <div class="player-backdrop" />
    <div class="player-overlay" />

    <div class="player-fg">
      <PlayerTopBar
        :station-name="stationName"
        :subtitle="stationSubtitle"
        @back="goBack"
      />

      <div class="player-body">
        <PlayerCover :is-audio-loading="isAudioLoading" />
        <div class="player-track-column">
          <TrackInfo />
          <ProgressBar ref="progressBarRef" :audio="audio" />
          <PlayerControls
            :liked="liked"
            :can-prev="canPrev"
            @play-pause="handlePlayPause"
            @next="handleNext"
            @prev="handlePrev"
            @skip="handleSkip"
            @toggle-like="toggleLike"
            @dislike="handleDislike"
            @toggle-lyrics="showLyrics = !showLyrics"
            @share="handleShare"
          />
        </div>
      </div>

      <QueueStrip />
    </div>

    <QueueDrawer />
    <PlayerToasts :blacklist-toast="blacklistToast" :info-toast="infoToast" />

    <LyricsPanel
      v-model:lyrics-scroll-el="lyricsScrollEl"
      :open="showLyrics"
      :song-title="songTitle"
      :lyrics-lines="lyricsLines"
      :lyrics-loading="lyricsLoading"
      :active-lyric-idx="activeLyricIdx"
      @close="showLyrics = false"
    />
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted } from 'vue'
import { useRouter } from 'vue-router'
import { usePlayerStore } from '@/stores/player'
import { useRadioStore } from '@/stores/radio'
import { useUiStore } from '@/stores/ui'
import { useAudioPlayer } from '@/composables/useAudioPlayer'

import { usePlaybackControls } from './composables/usePlaybackControls'
import { useLyrics } from './composables/useLyrics'
import { useTrackActions } from './composables/useTrackActions'
import PlayerTopBar from './components/PlayerTopBar.vue'
import PlayerCover from './components/PlayerCover.vue'
import TrackInfo from './components/TrackInfo.vue'
import ProgressBar from './components/ProgressBar.vue'
import PlayerControls from './components/PlayerControls.vue'
import QueueStrip from './components/QueueStrip.vue'
import QueueDrawer from './components/QueueDrawer.vue'
import PlayerToasts from './components/PlayerToasts.vue'
import LyricsPanel from './components/LyricsPanel.vue'

const router = useRouter()
const player = usePlayerStore()
const radio = useRadioStore()
const ui = useUiStore()
const audio = useAudioPlayer()

const showLyrics = ref(false)
const progressBarRef = ref<InstanceType<typeof ProgressBar> | null>(null)

const totalDuration = computed(() =>
  audio.duration.value > 0 ? audio.duration.value : player.duration,
)

const isAudioLoading = computed(() => {
  const url = player.currentSong?.audioUrl
  if (!url) return false
  return audio.loadedUrl.value !== url || !audio.isReady.value
})

const lyricCurrentTime = computed(() =>
  audio.currentTime.value > 0
    ? audio.currentTime.value
    : player.progress * totalDuration.value,
)

/** Deferred binding: useTrackActions needs handleNext before usePlaybackControls defines it. */
const deferredNext = { run: (): void => undefined }

const {
  liked,
  infoToast,
  showInfoToast,
  toggleLike,
  handleDislike,
  handleShare,
} = useTrackActions(() => deferredNext.run())

const {
  handlePlayPause,
  handleNext,
  handlePrev,
  handleSkip,
  canPrev,
  blacklistToast,
  bootstrapPlayback,
} = usePlaybackControls(audio, showInfoToast)

deferredNext.run = handleNext

const {
  lyricsLines,
  lyricsLoading,
  lyricsScrollEl,
  activeLyricIdx,
} = useLyrics(showLyrics, lyricCurrentTime)

const stationName = computed(() =>
  radio.session?.moodText || radio.moodText || 'Late-night Reset',
)

const stationSubtitle = computed(() =>
  `${radio.scene || radio.moodText || '情绪电台'} · ${player.queue.length} 首`,
)

const songTitle = computed(() => player.currentSong?.title ?? 'weightless')

function goBack() {
  router.push('/home')
}

onMounted(() => {
  bootstrapPlayback({ onNoCurrentSong: () => router.replace('/home') })
})

onUnmounted(() => {
  progressBarRef.value?.cleanupProgressListeners()
})
</script>

<style scoped>
.player-page {
  position: relative;
  min-height: 100vh;
  overflow: hidden;
}

.player-backdrop {
  position: absolute;
  inset: 0;
  background:
    radial-gradient(circle at 20% 25%, var(--mood-a) 0%, transparent 55%),
    radial-gradient(circle at 80% 20%, var(--mood-b) 0%, transparent 55%),
    radial-gradient(circle at 70% 90%, var(--mood-c) 0%, transparent 60%),
    radial-gradient(circle at 10% 80%, var(--mood-d) 0%, transparent 65%);
  background-color: var(--mood-d);
  filter: saturate(1.1);
}

.player-overlay {
  position: absolute;
  inset: 0;
  background: rgba(0, 0, 0, 0.18);
}

.player-fg {
  position: relative;
  z-index: 2;
  color: #fff;
  height: 100vh;
  display: flex;
  flex-direction: column;
  animation: player-rise 0.5s cubic-bezier(0.16, 1, 0.3, 1) 0.05s both;
}

.player-body {
  flex: 1;
  display: grid;
  grid-template-columns: 1fr 1.1fr;
  gap: 48px;
  padding: 48px 56px;
  align-items: center;
  overflow-y: auto;
}

@media (max-width: 900px) {
  .player-body {
    grid-template-columns: 1fr;
    gap: 28px;
    padding: 24px 22px;
  }
}

.player-track-column {
  display: flex;
  flex-direction: column;
}

@keyframes player-rise {
  from { opacity: 0; transform: translateY(14px); }
  to   { opacity: 1; transform: translateY(0); }
}
</style>