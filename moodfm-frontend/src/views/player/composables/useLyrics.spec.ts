import { describe, it, expect, beforeEach, vi } from 'vitest'
import { ref } from 'vue'
import { createPinia, setActivePinia } from 'pinia'
import { useLyrics } from './useLyrics'

vi.mock('@/api/song', () => ({
  songApi: { lyrics: vi.fn(() => Promise.resolve([])) },
}))

describe('useLyrics', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
  })

  it('activeLyricIdx selects line matching current playback time', () => {
    const showLyrics = ref(false)
    const currentTime = ref(5.5)

    const { lyricsLines, activeLyricIdx } = useLyrics(showLyrics, currentTime)

    lyricsLines.value = [
      { time: 0, text: 'line 0' },
      { time: 3000, text: 'line 1' },
      { time: 6000, text: 'line 2' },
    ]

    expect(activeLyricIdx.value).toBe(1)

    currentTime.value = 6.2
    expect(activeLyricIdx.value).toBe(2)
  })

  it('activeLyricIdx is -1 when lyrics are empty', () => {
    const { activeLyricIdx } = useLyrics(ref(false), ref(0))
    expect(activeLyricIdx.value).toBe(-1)
  })
})