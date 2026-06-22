import { ref, computed, watch, nextTick, type Ref, type ComputedRef } from 'vue'
import { usePlayerStore } from '@/stores/player'
import { songApi, type LyricLine } from '@/api/song'
import { logger } from '@/utils/logger'

export function useLyrics(
  showLyrics: Ref<boolean>,
  currentTime: Ref<number> | ComputedRef<number>,
) {
  const player = usePlayerStore()

  const lyricsLines = ref<LyricLine[]>([])
  const lyricsLoading = ref(false)
  const lyricsScrollEl = ref<HTMLElement | null>(null)
  const lyricsLoadedSongId = ref<string | number | null>(null)

  const activeLyricIdx = computed(() => {
    if (!lyricsLines.value.length) return -1
    const ms = currentTime.value * 1000
    let idx = 0
    for (let i = 0; i < lyricsLines.value.length; i++) {
      if (lyricsLines.value[i].time <= ms) idx = i
      else break
    }
    return idx
  })

  async function loadLyrics() {
    const song = player.currentSong
    if (!song?.id) return
    if (lyricsLoadedSongId.value === song.id) return
    const targetId = song.id
    lyricsLoadedSongId.value = targetId
    lyricsLoading.value = true
    try {
      const lines = await songApi.lyrics(targetId)
      if (lyricsLoadedSongId.value !== targetId) return
      lyricsLines.value = Array.isArray(lines) ? lines.filter(l => l.text?.trim()) : []
    } catch (err) {
      logger.warn('player:lyrics-prefetch', err)
      if (lyricsLoadedSongId.value === targetId) {
        lyricsLines.value = []
        lyricsLoadedSongId.value = null
      }
    } finally {
      if (lyricsLoadedSongId.value === targetId || lyricsLoadedSongId.value === null) {
        lyricsLoading.value = false
      }
    }
  }

  watch(showLyrics, (open) => {
    if (open && !lyricsLines.value.length && !lyricsLoading.value) loadLyrics()
  })

  watch(() => player.currentSong?.id, (songId) => {
    lyricsLines.value = []
    lyricsLoadedSongId.value = null
    if (songId) loadLyrics()
  }, { immediate: true })

  watch(activeLyricIdx, async (idx) => {
    if (!showLyrics.value || idx < 0 || !lyricsScrollEl.value) return
    await nextTick()
    const el = lyricsScrollEl.value.children[idx] as HTMLElement | undefined
    el?.scrollIntoView({ block: 'center', behavior: 'smooth' })
  })

  return {
    lyricsLines,
    lyricsLoading,
    lyricsScrollEl,
    activeLyricIdx,
    loadLyrics,
  }
}