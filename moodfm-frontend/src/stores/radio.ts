import { defineStore } from 'pinia'
import { ref } from 'vue'
import { radioApi } from '@/api/radio'
import { usePlayerStore } from './player'
import type { RadioSession } from '@/types'

export const useRadioStore = defineStore('radio', () => {
  const session = ref<RadioSession | null>(null)
  const moodText = ref('')
  const scene = ref('')
  const isLoading = ref(false)
  const recentSessions = ref<RadioSession[]>([])

  async function startRadio(data: { moodText: string; scene?: string; durationMinutes?: number }) {
    isLoading.value = true
    try {
      const res = await radioApi.startRadio(data)
      // res is RadioQueueVO: { sessionId, scene, moodSummary, songs, totalCount }
      const player = usePlayerStore()
      if (res.songs && res.songs.length > 0) {
        player.setSessionId(String(res.sessionId))
        const mapSong = (s: any) => ({
          id: String(s.id),
          title: s.title,
          artist: s.artist,
          album: s.album,
          platform: s.platform || 'netease',
          platformSongId: s.platformSongId || '',
          duration: s.durationSeconds || s.duration || 0,
          coverUrl: s.coverUrl,
          audioUrl: s.playUrl || undefined,
          recommendReason: s.recommendReason,
          urlSource: s.urlSource,
        })
        const [first, ...rest] = res.songs
        player.setSong(mapSong(first))
        player.setQueue(rest.map(mapSong))
        player.setPlaying(true)
      }
      session.value = { sessionId: String(res.sessionId), moodText: data.moodText, scene: res.scene, status: 'active', createdAt: new Date().toISOString() }
      return session.value
    } finally {
      isLoading.value = false
    }
  }

  async function fetchRecentSessions() {
    const res = await radioApi.getSessions(5)
    recentSessions.value = (res as any[]).map(r => ({
      sessionId: String(r.id ?? r.sessionId ?? ''),
      moodText:  r.rawInput ?? r.moodText ?? '',
      scene:     r.scene ?? '',
      status:    'ended' as const,
      createdAt: r.startedAt ?? r.createdAt ?? '',
    }))
    return recentSessions.value
  }

  function setMoodText(t: string) {
    moodText.value = t
  }

  function setScene(s: string) {
    scene.value = s
  }

  return { session, moodText, scene, isLoading, recentSessions, startRadio, fetchRecentSessions, setMoodText, setScene }
})
