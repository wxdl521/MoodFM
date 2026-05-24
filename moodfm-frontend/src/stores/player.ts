import { defineStore } from 'pinia'
import { ref } from 'vue'
import type { Song } from '@/types'

const HISTORY_MAX = 50

export const usePlayerStore = defineStore('player', () => {
  const currentSong = ref<Song | null>(null)
  const queue = ref<Song[]>([])
  const isPlaying = ref(false)
  const progress = ref(0)
  const duration = ref(0)
  const volume = ref(1)
  const sessionId = ref<string | null>(null)
  const trackNumber = ref(1)

  // Playback history stack — most recent at the end. Used by prev() to walk
  // backwards through previously-played songs. Capped at HISTORY_MAX entries;
  // oldest entries are dropped when the cap is exceeded.
  const history = ref<Song[]>([])

  function setSong(song: Song, opts: { fromHistory?: boolean } = {}) {
    // Push the outgoing song onto the history stack, unless this call is itself
    // the result of popping from history (avoids infinite ping-pong on prev()).
    if (!opts.fromHistory && currentSong.value) {
      history.value.push(currentSong.value)
      if (history.value.length > HISTORY_MAX) {
        history.value.splice(0, history.value.length - HISTORY_MAX)
      }
    }
    currentSong.value = song
    progress.value = 0
    duration.value = song.duration
  }

  function setQueue(songs: Song[]) {
    queue.value = songs
  }

  function addToQueue(songs: Song[]) {
    queue.value.push(...songs)
  }

  function togglePlay() {
    isPlaying.value = !isPlaying.value
  }

  function setPlaying(v: boolean) {
    isPlaying.value = v
  }

  function next() {
    if (queue.value.length === 0) return
    const nextSong = queue.value.shift()!
    setSong(nextSong)
    trackNumber.value++
  }

  function prev() {
    if (history.value.length === 0) return
    const previousSong = history.value.pop()!
    // Put the current song back at the front of the queue so it isn't lost
    if (currentSong.value) queue.value.unshift(currentSong.value)
    setSong(previousSong, { fromHistory: true })
    // Mirror next()'s track-number bookkeeping
    if (trackNumber.value > 1) trackNumber.value--
  }

  function setProgress(v: number) {
    progress.value = v
  }

  function setVolume(v: number) {
    volume.value = v
  }

  function setSessionId(id: string) {
    sessionId.value = id
  }

  return {
    currentSong,
    queue,
    isPlaying,
    progress,
    duration,
    volume,
    sessionId,
    trackNumber,
    history,
    setSong,
    setQueue,
    addToQueue,
    togglePlay,
    setPlaying,
    next,
    prev,
    setProgress,
    setVolume,
    setSessionId,
  }
})
