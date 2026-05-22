import { defineStore } from 'pinia'
import { ref } from 'vue'
import { playlistApi, type SmartPlaylistSummary } from '@/api/playlist'

const TTL_MS = 5 * 60 * 1000

export interface PlaylistItem {
  id: string
  t: string
  en: string
  n: number
  m: number
  src: string
  mood: string
  desc: string
  ai: boolean
}

function toPlatformLabel(p: string): string {
  return p === 'netease' ? '网易云' : p === 'qqmusic' ? 'QQ' : p
}

export const usePlaylistStore = defineStore('playlist', () => {
  const lists = ref<PlaylistItem[]>([])
  const smartPlaylists = ref<SmartPlaylistSummary[]>([])
  const loadedAt = ref<number | null>(null)
  const loading = ref(false)
  const error = ref<string | null>(null)

  function isStale(): boolean {
    return loadedAt.value === null || Date.now() - loadedAt.value > TTL_MS
  }

  async function load(force = false) {
    if (!force && !isStale() && lists.value.length > 0) return
    loading.value = true
    error.value = null
    try {
      const [playlistData, smartData] = await Promise.all([
        playlistApi.list(),
        playlistApi.listSmart().catch(() => []),
      ])
      lists.value = playlistData.map(pl => ({
        id: pl.id,
        t: pl.name,
        en: pl.name.toUpperCase().slice(0, 16),
        n: pl.trackCount,
        m: Math.round(pl.trackCount * 3.5),
        src: toPlatformLabel(pl.platform),
        mood: 'calm',
        desc: pl.description ?? '',
        ai: false,
      }))
      smartPlaylists.value = smartData
      loadedAt.value = Date.now()
    } catch (e: any) {
      error.value = e?.message ?? '加载失败'
      throw e
    } finally {
      loading.value = false
    }
  }

  function invalidate() {
    loadedAt.value = null
  }

  return { lists, smartPlaylists, loading, error, isStale, load, invalidate }
})
