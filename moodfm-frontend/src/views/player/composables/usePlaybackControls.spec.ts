import { describe, it, expect, beforeEach, vi } from 'vitest'
import { ref } from 'vue'
import { createPinia, setActivePinia } from 'pinia'
import { usePlaybackControls } from './usePlaybackControls'
import { usePlayerStore } from '@/stores/player'
import type { Song } from '@/types'

vi.mock('@/api/radio', () => ({
  radioApi: { sendFeedback: vi.fn(() => Promise.resolve()) },
}))
vi.mock('@/api/blacklist', () => ({
  blacklistApi: { add: vi.fn(() => Promise.resolve()) },
}))

function makeSong(id: string, title: string): Song {
  return {
    id,
    title,
    artist: 'Artist',
    album: '',
    duration: 200,
    coverUrl: '',
    platform: 'netease',
    platformSongId: id,
    audioUrl: `https://example.com/${id}.mp3`,
  }
}

describe('usePlaybackControls', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
  })

  function mockAudio() {
    return {
      currentTime: ref(10),
      duration: ref(200),
      isReady: ref(true),
      play: vi.fn(),
      pause: vi.fn(),
      stop: vi.fn(),
      load: vi.fn(() => Promise.resolve()),
    }
  }

  it('canPrev is false when history is empty', () => {
    const { canPrev, handlePrev } = usePlaybackControls(mockAudio(), () => {})
    expect(canPrev.value).toBe(false)
    handlePrev()
    const player = usePlayerStore()
    expect(player.currentSong).toBeNull()
  })

  it('canPrev is true when history has entries', () => {
    const player = usePlayerStore()
    const first = makeSong('1', 'First')
    const second = makeSong('2', 'Second')
    player.setSong(first)
    player.setQueue([second])
    player.next()

    const { canPrev } = usePlaybackControls(mockAudio(), () => {})
    expect(canPrev.value).toBe(true)
  })

  it('handleSkip increments consecutive skips for same artist', async () => {
    const player = usePlayerStore()
    const s1 = makeSong('1', 'A')
    const s2 = makeSong('2', 'B')
    player.setSong(s1)
    player.setQueue([s2])
    player.setSessionId('99')

    const toasts: string[] = []
    const { handleSkip, blacklistToast } = usePlaybackControls(mockAudio(), (msg) => { toasts.push(msg) })

    handleSkip()
    handleSkip()
    handleSkip()

    expect(blacklistToast.value).toBe('Artist')
    expect(toasts).not.toContain('自动拉黑失败，请稍后重试')
  })
})