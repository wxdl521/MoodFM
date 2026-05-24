/**
 * Audio player composable — module-level singleton.
 *
 * The Howl instance, refs (isReady/isPlaying/currentTime/duration/loadedUrl)
 * and progress timer all live at module scope, so every caller of
 * useAudioPlayer() observes the same playback state. This is what lets
 * Player.vue and MiniPlayer.vue stay perfectly in sync when the user
 * navigates between them.
 *
 * Caveat: do NOT call audioPlayer.stop() (or unload the Howl) from a
 * component's onUnmounted. Leaving the Player page would silently kill
 * audio for the MiniPlayer that takes over. Stop only when the user
 * actually asks to stop, or when the queue is exhausted.
 */
import { ref } from 'vue'
import { Howl } from 'howler'
import { usePlayerStore } from '@/stores/player'
import api from '@/api/client'
import { logger } from '@/utils/logger'

const isReady = ref(false)
const isPlaying = ref(false)
const currentTime = ref(0)
const duration = ref(0)
const loadedUrl = ref<string | null>(null)

let howl: Howl | null = null
let progressTimer: ReturnType<typeof setInterval> | null = null

function clearProgressTimer() {
  if (progressTimer !== null) {
    clearInterval(progressTimer)
    progressTimer = null
  }
}

function startProgressTimer() {
  clearProgressTimer()
  progressTimer = setInterval(() => {
    if (howl && howl.playing()) {
      const t = howl.seek() as number
      const d = howl.duration() ?? 0
      currentTime.value = t
      duration.value = d
      if (d > 0) {
        usePlayerStore().setProgress(t / d)
      }
    }
  }, 1000)
}

function load(url: string): Promise<void> {
  return new Promise((resolve, reject) => {
    if (howl) {
      howl.unload()
      howl = null
    }
    clearProgressTimer()
    isReady.value = false
    isPlaying.value = false
    currentTime.value = 0
    duration.value = 0
    loadedUrl.value = url

    // Capture local reference so stale callbacks from a superseded load() call
    // can detect they've been replaced and bail out, preventing double-play.
    const instance = new Howl({
      src: [url],
      html5: true,
      volume: usePlayerStore().volume,
      onload() {
        if (howl !== instance) return
        isReady.value = true
        duration.value = instance.duration()
        resolve()
      },
      onloaderror(_id, err) {
        if (howl !== instance) return
        isReady.value = false
        reject(err)
      },
      onplay() {
        if (howl !== instance) return
        isPlaying.value = true
        usePlayerStore().setPlaying(true)
        startProgressTimer()
      },
      onpause() {
        if (howl !== instance) return
        isPlaying.value = false
        usePlayerStore().setPlaying(false)
        clearProgressTimer()
      },
      onstop() {
        if (howl !== instance) return
        isPlaying.value = false
        usePlayerStore().setPlaying(false)
        clearProgressTimer()
        currentTime.value = 0
        usePlayerStore().setProgress(0)
      },
      onend() {
        if (howl !== instance) return
        isPlaying.value = false
        const store = usePlayerStore()
        // Capture before next() changes currentSong
        const completedSong = store.currentSong
        const sessionId = store.sessionId
        const totalSecs = Math.round(duration.value) || 0
        store.setPlaying(false)
        clearProgressTimer()
        currentTime.value = 0
        store.setProgress(0)
        store.next()
        // Fire-and-forget: record completed play
        if (completedSong?.id && sessionId) {
          api.post('/radio/feedback', {
            sessionId: Number(sessionId),
            songId: Number(completedSong.id),
            eventType: 'completed',
            playedSeconds: totalSecs,
            totalSeconds: totalSecs,
            platform: completedSong.platform || 'netease',
            // silent: feedback 上报失败不影响播放
          }).catch(err => { logger.warn('player:feedback-completed', err) })
        }
        const next = store.currentSong
        if (next?.audioUrl) {
          load(next.audioUrl).then(() => play())
        }
      },
      onseek() {
        if (howl !== instance) return
        const t = instance.seek() as number
        currentTime.value = t
        const d = instance.duration() ?? 0
        if (d > 0) {
          usePlayerStore().setProgress(t / d)
        }
      },
    })
    howl = instance
  })
}

function play() {
  if (howl && isReady.value) {
    howl.play()
  }
}

function pause() {
  if (howl && howl.playing()) {
    howl.pause()
  }
}

function stop() {
  if (howl) {
    howl.stop()
  }
}

function seek(time: number) {
  if (howl && isReady.value) {
    howl.seek(time)
  }
}

function setVolume(v: number) {
  usePlayerStore().setVolume(v)
  if (howl) {
    howl.volume(v)
  }
}

/**
 * Returns the shared audio-player handle. Calling this from multiple
 * components is fine — they all observe the same singleton state.
 * See the module-level docstring for the onUnmounted caveat.
 */
export function useAudioPlayer() {
  return {
    isReady,
    isPlaying,
    currentTime,
    duration,
    loadedUrl,
    play,
    pause,
    stop,
    seek,
    setVolume,
    load,
  }
}
