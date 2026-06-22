import { describe, it, expect, beforeEach } from 'vitest'
import { ref } from 'vue'
import { createPinia, setActivePinia } from 'pinia'
import { useProgressBar } from './useProgressBar'
import { usePlayerStore } from '@/stores/player'

describe('useProgressBar', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
  })

  function mockAudio(duration = 180) {
    return {
      currentTime: ref(0),
      duration: ref(duration),
      seek: () => {},
    }
  }

  it('displayProgress follows player.progress when not dragging', () => {
    const player = usePlayerStore()
    player.setProgress(0.42)

    const { displayProgress } = useProgressBar(mockAudio())
    expect(displayProgress.value).toBeCloseTo(0.42)
  })

  it('displayProgress follows pointer ratio while dragging', () => {
    const player = usePlayerStore()
    player.setProgress(0.1)

    const { progressEl, handleProgressDown, displayProgress, isDragging } = useProgressBar(mockAudio())

    const el = document.createElement('div')
    document.body.appendChild(el)
    Object.defineProperty(el, 'getBoundingClientRect', {
      value: () => ({
        left: 0, width: 200, top: 0, height: 10,
        right: 200, bottom: 10, x: 0, y: 0, toJSON: () => ({}),
      }),
    })
    progressEl.value = el

    handleProgressDown({ clientX: 100, preventDefault: () => {} } as MouseEvent)
    expect(isDragging.value).toBe(true)
    expect(displayProgress.value).toBeCloseTo(0.5)

    document.body.removeChild(el)
  })
})