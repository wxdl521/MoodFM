import { ref, onUnmounted } from 'vue'
import { Howl } from 'howler'
import { usePlayerStore } from '@/stores/player'

export function useAudioPlayer() {
  const playerStore = usePlayerStore()

  const isReady = ref(false)
  const isPlaying = ref(false)
  const currentTime = ref(0)
  const duration = ref(0)

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
          playerStore.setProgress(t / d)
        }
      }
    }, 1000)
  }

  function load(url: string): Promise<void> {
    return new Promise((resolve, reject) => {
      // Unload previous instance
      if (howl) {
        howl.unload()
        howl = null
      }
      clearProgressTimer()
      isReady.value = false
      isPlaying.value = false
      currentTime.value = 0
      duration.value = 0

      howl = new Howl({
        src: [url],
        html5: true,
        volume: playerStore.volume,
        onload() {
          isReady.value = true
          duration.value = howl!.duration()
          resolve()
        },
        onloaderror(_id, err) {
          isReady.value = false
          reject(err)
        },
        onplay() {
          isPlaying.value = true
          playerStore.setPlaying(true)
          startProgressTimer()
        },
        onpause() {
          isPlaying.value = false
          playerStore.setPlaying(false)
          clearProgressTimer()
        },
        onstop() {
          isPlaying.value = false
          playerStore.setPlaying(false)
          clearProgressTimer()
          currentTime.value = 0
          playerStore.setProgress(0)
        },
        onend() {
          isPlaying.value = false
          playerStore.setPlaying(false)
          clearProgressTimer()
          currentTime.value = 0
          playerStore.setProgress(0)
          // Advance queue then load new song
          playerStore.next()
          const next = playerStore.currentSong
          if (next?.audioUrl) {
            load(next.audioUrl).then(() => play())
          }
        },
        onseek() {
          const t = howl!.seek() as number
          currentTime.value = t
          const d = howl!.duration() ?? 0
          if (d > 0) {
            playerStore.setProgress(t / d)
          }
        },
      })
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
    playerStore.setVolume(v)
    if (howl) {
      howl.volume(v)
    }
  }

  onUnmounted(() => {
    clearProgressTimer()
    if (howl) {
      howl.unload()
      howl = null
    }
  })

  return {
    isReady,
    isPlaying,
    currentTime,
    duration,
    play,
    pause,
    stop,
    seek,
    setVolume,
    load,
  }
}
