import api from './client'
import type { Platform } from '@/types'

export interface SongDetail {
  id: string
  title: string
  artist: string
  album?: string
  platform: Platform
  platformSongId: string
  duration: number
  coverUrl?: string
  audioUrl?: string
}

export interface LyricLine { time: number; text: string }

export const songApi = {
  get:            (id: string): Promise<SongDetail>             => api.get(`/songs/${id}`),
  lyrics:         (id: string): Promise<LyricLine[]>            => api.get(`/songs/${id}/lyrics`),
  getAudioUrl:    (id: string): Promise<{ url: string | null }>  => api.get(`/songs/${id}/audio-url`),
  batchAudioUrls: (ids: number[]): Promise<Record<string, string>> =>
                                                                  api.post('/songs/batch-audio-urls', ids),
}
