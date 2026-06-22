import { ref, computed, type Ref } from 'vue'
import { usePlayerStore } from '@/stores/player'

type AudioHandle = {
  currentTime: Ref<number>
  duration: Ref<number>
  seek: (time: number) => void
}

export function useProgressBar(audio: AudioHandle) {
  const player = usePlayerStore()

  const progressEl = ref<HTMLElement | null>(null)
  const isDragging = ref(false)
  const dragRatio = ref(0)

  const totalDuration = computed(() =>
    audio.duration.value > 0 ? audio.duration.value : player.duration,
  )
  const currentTime = computed(() =>
    audio.currentTime.value > 0 ? audio.currentTime.value : player.progress * totalDuration.value,
  )

  const displayProgress = computed(() =>
    isDragging.value ? dragRatio.value : player.progress,
  )
  const displayCurrentTime = computed(() =>
    isDragging.value ? dragRatio.value * totalDuration.value : currentTime.value,
  )

  function getEventClientX(e: MouseEvent | TouchEvent): number {
    if ('touches' in e) {
      return e.touches[0]?.clientX ?? (e as TouchEvent).changedTouches[0]?.clientX ?? 0
    }
    return (e as MouseEvent).clientX
  }

  function computeRatio(clientX: number): number {
    const el = progressEl.value
    if (!el) return 0
    const rect = el.getBoundingClientRect()
    if (rect.width <= 0) return 0
    return Math.max(0, Math.min(1, (clientX - rect.left) / rect.width))
  }

  function cleanupProgressListeners() {
    window.removeEventListener('mousemove', handleProgressMove)
    window.removeEventListener('mouseup', handleProgressUp)
    window.removeEventListener('touchmove', handleProgressMove)
    window.removeEventListener('touchend', handleProgressUp)
    window.removeEventListener('touchcancel', handleProgressUp)
  }

  function handleProgressMove(e: MouseEvent | TouchEvent) {
    if (!isDragging.value) return
    if (e.cancelable) e.preventDefault()
    dragRatio.value = computeRatio(getEventClientX(e))
  }

  function handleProgressUp() {
    if (!isDragging.value) {
      cleanupProgressListeners()
      return
    }
    const ratio = dragRatio.value
    isDragging.value = false
    cleanupProgressListeners()
    if (totalDuration.value > 0 && player.currentSong?.audioUrl) {
      audio.seek(ratio * totalDuration.value)
    } else {
      player.setProgress(ratio)
    }
  }

  function handleProgressDown(e: MouseEvent | TouchEvent) {
    if (!progressEl.value) return
    if ('clientX' in e) e.preventDefault()
    dragRatio.value = computeRatio(getEventClientX(e))
    isDragging.value = true
    window.addEventListener('mousemove', handleProgressMove)
    window.addEventListener('mouseup', handleProgressUp)
    window.addEventListener('touchmove', handleProgressMove, { passive: false })
    window.addEventListener('touchend', handleProgressUp)
    window.addEventListener('touchcancel', handleProgressUp)
  }

  return {
    progressEl,
    isDragging,
    displayProgress,
    displayCurrentTime,
    totalDuration,
    handleProgressDown,
    cleanupProgressListeners,
  }
}