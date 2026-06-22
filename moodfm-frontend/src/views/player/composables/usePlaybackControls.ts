import { ref, computed } from 'vue'
import { usePlayerStore } from '@/stores/player'
import { radioApi } from '@/api/radio'
import { blacklistApi } from '@/api/blacklist'
import { logger } from '@/utils/logger'

type AudioHandle = {
  currentTime: { value: number }
  duration: { value: number }
  isReady: { value: boolean }
  loadedUrl: { value: string | null }
  play: () => void
  pause: () => void
  stop: () => void
  load: (url: string) => Promise<void>
}

export function usePlaybackControls(
  audio: AudioHandle,
  showInfoToast: (msg: string, durationMs?: number) => void,
) {
  const player = usePlayerStore()

  const lastSkippedArtist = ref<string | null>(null)
  const consecutiveSkips = ref(0)
  const blacklistToast = ref<string | null>(null)

  const canPrev = computed(() => player.history.length > 0)

  function advanceQueue() {
    player.next()
    const song = player.currentSong
    if (song?.audioUrl) {
      audio.load(song.audioUrl)
        .then(() => audio.play())
        .catch(err => {
          logger.warn('player:audio-load-advance', err)
          const failedTitle = song?.title
          if (failedTitle) showInfoToast(`「${failedTitle}」无法播放，已跳过`)
          else showInfoToast('该歌曲无法播放，已跳过')
          if (player.queue.length > 0) advanceQueue()
          else { audio.stop(); player.setPlaying(false) }
        })
    } else if (player.queue.length > 0) {
      const skippedTitle = song?.title
      if (skippedTitle) showInfoToast(`「${skippedTitle}」无法播放，已跳过`)
      else showInfoToast('该歌曲无法播放，已跳过')
      advanceQueue()
    } else {
      if (song) showInfoToast('该歌曲无法播放，队列已空')
      audio.stop()
      player.setPlaying(false)
    }
  }

  function _sendSkipFeedback(
    song: typeof player.currentSong,
    sid: string | null,
    playedSecs: number,
    totalSecs: number,
  ) {
    if (!song?.id || !sid) return
    radioApi.sendFeedback('skip', {
      sessionId: Number(sid),
      songId: Number(song.id),
      playedSeconds: playedSecs,
      totalSeconds: totalSecs,
      platform: song.platform,
    }).catch(err => { logger.warn('player:feedback-skip', err) })
  }

  function handlePlayPause() {
    if (player.isPlaying) {
      audio.pause()
    } else {
      if (player.currentSong?.audioUrl) {
        if (audio.isReady.value) {
          audio.play()
        } else {
          audio.load(player.currentSong.audioUrl).then(() => audio.play()).catch(err => {
            logger.warn('player:audio-load-playpause', err)
            showInfoToast('该歌曲无法播放')
          })
        }
      } else if (player.queue.length > 0) {
        advanceQueue()
      } else {
        player.togglePlay()
      }
    }
  }

  function handleNext() {
    const prevSong = player.currentSong
    const sid = player.sessionId
    const playedSecs = Math.round(audio.currentTime.value) || 0
    const totalSecs = Math.round(audio.duration.value || player.duration) || 0
    advanceQueue()
    _sendSkipFeedback(prevSong, sid, playedSecs, totalSecs)
  }

  function handlePrev() {
    player.prev()
    const song = player.currentSong
    if (song?.audioUrl) {
      audio.load(song.audioUrl).then(() => audio.play()).catch(err => {
        logger.warn('player:audio-load-prev', err)
        const failedTitle = song?.title
        showInfoToast(failedTitle ? `「${failedTitle}」无法播放` : '该歌曲无法播放')
      })
    }
  }

  function handleSkip() {
    const skippedSong = player.currentSong
    const sid = player.sessionId
    const playedSecs = Math.round(audio.currentTime.value) || 0
    const totalSecs = Math.round(audio.duration.value || player.duration) || 0
    const skippedArtist = skippedSong?.artist

    advanceQueue()
    _sendSkipFeedback(skippedSong, sid, playedSecs, totalSecs)

    if (!skippedArtist) return

    if (skippedArtist === lastSkippedArtist.value) {
      consecutiveSkips.value++
    } else {
      lastSkippedArtist.value = skippedArtist
      consecutiveSkips.value = 1
    }

    if (consecutiveSkips.value >= 3) {
      blacklistApi.add({ type: 'artist', value: skippedArtist, label: skippedArtist }).catch(err => {
        logger.warn('player:auto-blacklist-add', err)
        showInfoToast('自动拉黑失败，请稍后重试')
      })
      blacklistToast.value = skippedArtist
      consecutiveSkips.value = 0
      lastSkippedArtist.value = null
      setTimeout(() => { blacklistToast.value = null }, 3000)
    }
  }

  /** Mount-time playback bootstrap: load current track or advance when URL missing. */
  function bootstrapPlayback(options: { onNoCurrentSong: () => void }) {
    if (!player.currentSong) {
      options.onNoCurrentSong()
      return
    }
    if (player.currentSong.audioUrl) {
      if (audio.loadedUrl.value !== player.currentSong.audioUrl) {
        audio.load(player.currentSong.audioUrl)
          .then(() => audio.play())
          .catch(err => {
            logger.warn('player:audio-load-mount', err)
            const failedTitle = player.currentSong?.title
            if (player.queue.length > 0) {
              if (failedTitle) showInfoToast(`「${failedTitle}」无法播放，已跳过`)
              else showInfoToast('该歌曲无法播放，已跳过')
              advanceQueue()
            } else {
              if (failedTitle) showInfoToast(`「${failedTitle}」无法播放`)
              else showInfoToast('该歌曲无法播放')
              audio.stop()
              player.setPlaying(false)
            }
          })
      }
    } else if (player.queue.length > 0) {
      advanceQueue()
    } else {
      player.setPlaying(false)
    }
  }

  return {
    handlePlayPause,
    handleNext,
    handlePrev,
    handleSkip,
    canPrev,
    blacklistToast,
    advanceQueue,
    bootstrapPlayback,
  }
}