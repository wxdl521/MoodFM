import { ref, watch, onUnmounted } from 'vue'
import { usePlayerStore } from '@/stores/player'

import { playlistApi } from '@/api/playlist'
import { radioApi } from '@/api/radio'
import { logger } from '@/utils/logger'

export function useTrackActions(handleNext: () => void) {
  const player = usePlayerStore()
  const liked = ref(false)
  const infoToast = ref<string | null>(null)
  let infoToastTimer: ReturnType<typeof setTimeout> | null = null

  function showInfoToast(msg: string, durationMs = 2500) {
    infoToast.value = msg
    if (infoToastTimer) clearTimeout(infoToastTimer)
    infoToastTimer = setTimeout(() => { infoToast.value = null }, durationMs)
  }

  let lastVolumeSentTs = 0
  let prevVolume = player.volume

  watch(() => player.volume, (newVol) => {
    const now = Date.now()
    if (newVol > prevVolume && (newVol - prevVolume) > 0.1 && now - lastVolumeSentTs > 30_000) {
      lastVolumeSentTs = now
      const song = player.currentSong
      const sid = player.sessionId
      if (song && sid) {
        radioApi.sendFeedback('volume_up', {
          sessionId: Number(sid),
          songId: Number(song.id),
          platform: song.platform,
        }).catch(err => { logger.warn('player:feedback-volume', err) })
      }
    }
    prevVolume = newVol
  })

  watch(() => player.currentSong?.id, async (songId) => {
    liked.value = false
    if (songId) {
      try {
        const res = await playlistApi.isLiked(songId)
        liked.value = res.liked
      } catch (err) {
        logger.warn('player:is-liked', err)
      }
    }
  }, { immediate: true })

  async function toggleLike() {
    const song = player.currentSong
    if (!song?.id) return
    try {
      const res = await playlistApi.toggleLike(song.id)
      liked.value = res.liked
    } catch (err) {
      logger.warn('player:toggle-like', err)
      showInfoToast('操作失败，请稍后重试')
    }
  }

  async function handleDislike() {
    const song = player.currentSong
    const sid = player.sessionId
    if (song && sid) {
      try {
        await radioApi.sendFeedback('dislike', {
          songId: Number(song.id),
          sessionId: Number(sid),
          platform: song.platform,
        })
      } catch (err) {
        logger.warn('player:feedback-dislike', err)
      }
    }
    handleNext()
  }

  async function handleShare() {
    const song = player.currentSong
    if (!song) return
    const title = `MoodFM: ${song.title}`
    const text = `正在收听 ${song.artist} - ${song.title}`
    const shareUrl = typeof window !== 'undefined' ? window.location.href : ''

    if (typeof navigator !== 'undefined' && typeof navigator.share === 'function') {
      try {
        await navigator.share({ title, text, url: shareUrl })
        return
      } catch (err: unknown) {
        if (err instanceof Error && err.name === 'AbortError') return
      }
    }

    try {
      if (navigator.clipboard?.writeText) {
        await navigator.clipboard.writeText(shareUrl)
        showInfoToast('链接已复制')
      } else {
        const ta = document.createElement('textarea')
        ta.value = shareUrl
        ta.style.position = 'fixed'
        ta.style.opacity = '0'
        document.body.appendChild(ta)
        ta.select()
        const ok = document.execCommand('copy')
        document.body.removeChild(ta)
        showInfoToast(ok ? '链接已复制' : '复制失败，请手动复制')
      }
    } catch (err) {
      logger.warn('player:share-clipboard', err)
      showInfoToast('复制失败，请手动复制')
    }
  }

  onUnmounted(() => {
    if (infoToastTimer) {
      clearTimeout(infoToastTimer)
      infoToastTimer = null
    }
  })

  return {
    liked,
    infoToast,
    showInfoToast,
    toggleLike,
    handleDislike,
    handleShare,
  }
}