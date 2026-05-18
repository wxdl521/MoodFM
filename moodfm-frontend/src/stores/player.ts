import { defineStore } from 'pinia'
import { ref } from 'vue'
import type { Song } from '@/types'

export const usePlayerStore = defineStore('player', () => {
  const currentSong = ref<Song | null>(null)
  const queue = ref<Song[]>([])
  const isPlaying = ref(false)
  const progress = ref(0)
  const duration = ref(0)
  const volume = ref(1)
  const sessionId = ref<string | null>(null)
  const trackNumber = ref(1)

  let lastSong: Song | null = null

  function setSong(song: Song) {
    lastSong = currentSong.value
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
    if (!lastSong) return
    queue.value.unshift(currentSong.value!)
    currentSong.value = lastSong
    progress.value = 0
    duration.value = lastSong.duration
    lastSong = null
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
