import type { Song } from '@/types'

/** Map backend SongVO (radio/search responses) to player Song. */
export function mapSongVoToSong(s: {
  id?: number | string
  title: string
  artist: string
  album?: string
  platform?: string
  platformSongId?: string
  durationSeconds?: number
  duration?: number
  coverUrl?: string
  playUrl?: string
  recommendReason?: string
  urlSource?: string
}): Song {
  return {
    id: String(s.id ?? ''),
    title: s.title,
    artist: s.artist,
    album: s.album ?? '',
    platform: (s.platform as Song['platform']) || undefined,
    platformSongId: s.platformSongId ?? '',
    duration: s.durationSeconds ?? s.duration ?? 0,
    coverUrl: s.coverUrl ?? '',
    audioUrl: s.playUrl ?? undefined,
    recommendReason: s.recommendReason,
    urlSource: s.urlSource,
  }
}